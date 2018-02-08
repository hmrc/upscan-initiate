package infrastructure.s3.awsclient
import collection.JavaConverters._
import com.amazonaws.util.{BinaryUtils, StringUtils}
import java.nio.charset.Charset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ISO_INSTANT
import java.time.{Instant, ZoneOffset}
import java.util
import java.util.Date
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import play.api.libs.json.{JsArray, Json}

case class UploadParameters(
  userSpecifiedExpirationDate: Date,
  bucketName: String,
  key: String,
  acl: String,
  additionalMetadata: Map[String, String]
)

final case class AwsCredentials(
  accessKeyId: String,
  secretKey: String,
  sessionToken: Option[String]
)

class S3PostSignerImpl(credentials: AwsCredentials, regionName: String, currentTime: () => Instant)
    extends S3PostSigner {

  def buildEndpoint(bucketName: String) = s"https://$bucketName.s3.amazonaws.com"

  def presignForm(
    userSpecifiedExpirationDate: Date,
    bucketName: String,
    key: String,
    acl: String,
    additionalMetadata: util.Map[String, String]) = {

    val uploadParameters =
      UploadParameters(userSpecifiedExpirationDate, bucketName, key, acl, additionalMetadata.asScala.toMap)

    val timestamp = currentTime()

    val timeStampIso         = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(timestamp)
    val formattedSigningDate = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(timestamp)

    val signingCredentials = s"${credentials.accessKeyId}/$formattedSigningDate/$regionName/s3/aws4_request"

    val signingKey = newSigningKey(credentials, formattedSigningDate, regionName, "s3")

    val policy = buildPolicy(uploadParameters, credentials.sessionToken, timeStampIso, signingCredentials)

    val encodedPolicy = {
      val encodedBytes = java.util.Base64.getEncoder.encode(policy.getBytes("UTF-8"))
      new String(encodedBytes).replaceAll("\n", "").replaceAll("\r", "")
    }

    val policySignature = sign(encodedPolicy, signingKey)

    buildFormFields(
      uploadParameters,
      credentials.sessionToken,
      timeStampIso,
      signingCredentials,
      encodedPolicy,
      policySignature).asJava
  }

  private def buildFormFields(
    uploadParameters: UploadParameters,
    securityToken: Option[String],
    timeStamp: String,
    signingCredentials: String,
    encodedPolicy: String,
    policySignature: Array[Byte]) = {

    val fields = Map(
      "X-Amz-Algorithm"  -> "AWS4-HMAC-SHA256",
      "X-Amz-Credential" -> signingCredentials,
      "X-Amz-Date"       -> timeStamp,
      "policy"           -> encodedPolicy,
      "X-Amz-Signature"  -> BinaryUtils.toHex(policySignature),
      "acl"              -> uploadParameters.acl,
      "key"              -> uploadParameters.key
    )

    val sessionCredentials = securityToken.map(t => Map("X-Amz-Security-Token" -> t)).getOrElse(Map.empty)

    val metadataFields =
      uploadParameters.additionalMetadata.map {
        case (metadataKey, value) => s"X-Amz-Meta-$metadataKey" -> value
      }

    fields ++ sessionCredentials ++ metadataFields

  }

  private def buildPolicy(
    uploadParameters: UploadParameters,
    securityToken: Option[String],
    timeStamp: String,
    signingCredentials: String) = {

    val securityTokenJson = securityToken.map(t => Json.obj("x-amz-security-token" -> t)).toList

    val metadataJson = uploadParameters.additionalMetadata.map {
      case (k, v) => Json.obj(s"X-Amz-Meta-$k" -> v)
    }

    val policyDocument = Json.obj(
      "expiration" -> ISO_INSTANT.format(uploadParameters.userSpecifiedExpirationDate.toInstant),
      "conditions" -> JsArray(
        List(
          Json.obj("bucket" -> uploadParameters.bucketName),
          Json.obj("acl"    -> uploadParameters.acl)
        )
          ++ securityTokenJson
          ++ List(
            Json.obj("x-amz-credential" -> signingCredentials),
            Json.obj("x-amz-algorithm"  -> "AWS4-HMAC-SHA256"),
            Json.obj("key"              -> uploadParameters.key)
          )
          ++ metadataJson
          ++ List(
            Json.obj("x-amz-date" -> timeStamp)
          ))
    )

    Json.stringify(policyDocument)
  }

  private def newSigningKey(credentials: AwsCredentials, dateStamp: String, regionName: String, serviceName: String) = {
    val kSecret  = ("AWS4" + credentials.secretKey).getBytes(Charset.forName("UTF-8"))
    val kDate    = sign(dateStamp, kSecret)
    val kRegion  = sign(regionName, kDate)
    val kService = sign(serviceName, kRegion)
    sign("aws4_request", kService)
  }

  private def sign(stringData: String, key: Array[Byte]): Array[Byte] = {
    val data      = stringData.getBytes(StringUtils.UTF8)
    val algorithm = "HmacSHA256"
    val mac       = Mac.getInstance(algorithm)
    mac.init(new SecretKeySpec(key, algorithm))
    mac.doFinal(data)
  }
}
