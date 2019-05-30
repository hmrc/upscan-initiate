package connectors.model
import java.time.Instant

case class UploadParameters(
  expirationDateTime: Instant,
  bucketName: String,
  objectKey: String,
  acl: String,
  additionalMetadata: Map[String, String],
  contentLengthRange: ContentLengthRange,
  expectedContentType: Option[String],
  successRedirect: Option[String],
  errorRedirect: Option[String]
)
