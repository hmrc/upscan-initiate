package connectors.s3

import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ISO_INSTANT
import java.time.{Instant, ZoneOffset}

import domain.{UploadFormGenerator, UploadParameters}
import play.api.libs.json.{JsArray, Json}

final case class AwsCredentials(
  accessKeyId: String,
  secretKey: String,
  sessionToken: Option[String]
)

class S3UploadFormGenerator(
  credentials: AwsCredentials,
  regionName: String,
  currentTime: () => Instant,
  policySigner: PolicySigner = PolicySigner)
    extends UploadFormGenerator {

  def buildEndpoint(bucketName: String) = s"https://$bucketName.s3.amazonaws.com"

  def generateFormFields(uploadParameters: UploadParameters): Map[String, String] = {
    val timestamp            = currentTime()
    val formattedSigningDate = awsDate(timestamp)
    val signingCredentials   = s"${credentials.accessKeyId}/$formattedSigningDate/$regionName/s3/aws4_request"
    val timeStampIso         = awsTimestamp(timestamp)
    val policy               = buildPolicy(uploadParameters, credentials.sessionToken, timeStampIso, signingCredentials)
    val encodedPolicy        = base64encode(policy)
    val policySignature      = policySigner.signPolicy(credentials, formattedSigningDate, regionName, encodedPolicy)

    buildFormFields(
      uploadParameters,
      credentials.sessionToken,
      timeStampIso,
      signingCredentials,
      encodedPolicy,
      policySignature
    )
  }

  private def awsTimestamp(i: Instant): String =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(i)

  private def awsDate(i: Instant): String =
    DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(i)

  private def base64encode(input: String): String = {
    val encodedBytes = java.util.Base64.getEncoder.encode(input.getBytes("UTF-8"))
    new String(encodedBytes).replaceAll("\n", "").replaceAll("\r", "")
  }

  private def buildFormFields(
    uploadParameters: UploadParameters,
    securityToken: Option[String],
    timeStamp: String,
    signingCredentials: String,
    encodedPolicy: String,
    policySignature: String) = {

    val fields = Map(
      "x-amz-algorithm"  -> "AWS4-HMAC-SHA256",
      "x-amz-credential" -> signingCredentials,
      "x-amz-date"       -> timeStamp,
      "policy"           -> encodedPolicy,
      "x-amz-signature"  -> policySignature,
      "acl"              -> uploadParameters.acl,
      "key"              -> uploadParameters.objectKey
    )

    val sessionCredentials = securityToken.map(t => Map("x-amz-security-token" -> t)).getOrElse(Map.empty)

    val metadataFields =
      uploadParameters.additionalMetadata.map {
        case (metadataKey, value) => s"x-amz-meta-$metadataKey" -> value
      }

    val contentTypeField = uploadParameters.expectedContentType.map(contentType => "Content-Type" -> contentType)

    fields ++ sessionCredentials ++ metadataFields ++ contentTypeField

  }

  private def buildPolicy(
    uploadParameters: UploadParameters,
    securityToken: Option[String],
    timeStamp: String,
    signingCredentials: String) = {

    val securityTokenJson = securityToken.map(t => Json.obj("x-amz-security-token" -> t)).toList

    val metadataJson = uploadParameters.additionalMetadata.map {
      case (k, v) => Json.obj(s"x-amz-meta-$k" -> v)
    }

    val contentTypeConstraintJson =
      uploadParameters.expectedContentType.map(contentType => Json.obj("Content-Type" -> contentType))

    val policyDocument = Json.obj(
      "expiration" -> ISO_INSTANT.format(uploadParameters.expirationDateTime),
      "conditions" -> JsArray(
        List(
          Json.obj("bucket"           -> uploadParameters.bucketName),
          Json.obj("acl"              -> uploadParameters.acl),
          Json.obj("x-amz-credential" -> signingCredentials),
          Json.obj("x-amz-algorithm"  -> "AWS4-HMAC-SHA256"),
          Json.obj("key"              -> uploadParameters.objectKey),
          Json.obj("x-amz-date"       -> timeStamp),
          Json.arr(
            "content-length-range",
            uploadParameters.contentLengthRange.min,
            uploadParameters.contentLengthRange.max)
        ) ++ securityTokenJson
          ++ metadataJson
          ++ contentTypeConstraintJson
      )
    )

    Json.stringify(policyDocument)
  }

}
