package domain

import java.time.Instant

trait UploadFormGenerator {
  def buildEndpoint(bucketName: String): String

  def generateFormFields(uploadParameters: UploadParameters): Map[String, String]
}

case class ContentLengthRange(min: Int, max: Int)

case class UploadParameters(
  expirationDateTime: Instant,
  bucketName: String,
  objectKey: String,
  acl: String,
  additionalMetadata: Map[String, String],
  contentLengthRange: ContentLengthRange,
  expectedContentType: Option[String]
)
