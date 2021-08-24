/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors.s3

import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ISO_INSTANT
import java.time.{Instant, ZoneOffset}

import connectors.model.{AwsCredentials, UploadFormGenerator, UploadParameters}
import play.api.libs.json.{JsArray, JsValue, Json}

class S3UploadFormGenerator(
  credentials: AwsCredentials,
  regionName: String,
  currentTime: () => Instant,
  policySigner: PolicySigner = PolicySigner)
    extends UploadFormGenerator {

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
    policySignature: String): Map[String, String] = {

    val fields = Map(
      "x-amz-algorithm"                     -> "AWS4-HMAC-SHA256",
      "x-amz-credential"                    -> signingCredentials,
      "x-amz-date"                          -> timeStamp,
      "policy"                              -> encodedPolicy,
      "x-amz-signature"                     -> policySignature,
      "acl"                                 -> uploadParameters.acl,
      "key"                                 -> uploadParameters.objectKey,
      "x-amz-meta-original-filename"        -> s"$${filename}",
      "x-amz-meta-upscan-initiate-response" -> currentTime().toString
    )

    val metadataFields =
      uploadParameters.additionalMetadata.map {
        case (metadataKey, value) => s"x-amz-meta-$metadataKey" -> value
      }

    val sessionCredentials = securityToken.map(v => Map("x-amz-security-token"                -> v)).getOrElse(Map.empty)
    val contentTypeField   = uploadParameters.expectedContentType.map(v => Map("Content-Type" -> v)).getOrElse(Map.empty)
    val successRedirect =
      uploadParameters.successRedirect.map(v => Map("success_action_redirect" -> v)).getOrElse(Map.empty)

    val errorRedirect =
      uploadParameters.errorRedirect.map(v => Map("error_action_redirect" -> v)).getOrElse(Map.empty)

    fields ++ metadataFields ++ sessionCredentials ++ contentTypeField ++ successRedirect ++ errorRedirect
  }

  private def buildPolicy(
    uploadParameters: UploadParameters,
    securityToken: Option[String],
    timeStamp: String,
    signingCredentials: String) = {

    val securityTokenJson = securityToken.map(t => Json.obj("x-amz-security-token" -> t)).toList

    val metadataJson: Seq[JsValue] = uploadParameters.additionalMetadata.map {
      case (k, v) => Json.obj(s"x-amz-meta-$k" -> v)
    }.toSeq :+
      Json.arr("starts-with", "$x-amz-meta-original-filename", "") :+
      Json.arr("starts-with", "$x-amz-meta-upscan-initiate-response", "")

    val contentTypeConstraintJson =
      uploadParameters.expectedContentType.map(contentType => Json.obj("Content-Type" -> contentType))

    val successRedirectConstraint =
      uploadParameters.successRedirect.map(redirect => Json.obj("success_action_redirect" -> redirect))

    val errorRedirectConstraint =
      uploadParameters.errorRedirect.map(redirect => Json.obj("error_action_redirect" -> redirect))

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
          ++ successRedirectConstraint
          ++ errorRedirectConstraint
      )
    )

    Json.stringify(policyDocument)
  }

}
