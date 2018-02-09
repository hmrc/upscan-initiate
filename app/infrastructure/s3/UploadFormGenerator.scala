package infrastructure.s3

import java.time.Instant

trait UploadFormGenerator {
  def buildEndpoint(bucketName: String): String

  def generateFormFields(uploadParameters: UploadParameters): Map[String, String]
}

case class UploadParameters(
  expirationDateTime: Instant,
  bucketName: String,
  objectKey: String,
  acl: String,
  additionalMetadata: Map[String, String]
)

final case class AwsCredentials(
  accessKeyId: String,
  secretKey: String,
  sessionToken: Option[String]
)
