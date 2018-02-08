package infrastructure.s3

import java.time.Instant

trait S3PostSigner {
  def buildEndpoint(bucketName: String): String

  def presignForm(uploadParameters: UploadParameters): Map[String, String]
}

case class UploadParameters(
  expirationDateTime: Instant,
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
