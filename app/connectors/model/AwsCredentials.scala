package connectors.model

final case class AwsCredentials(accessKeyId: String, secretKey: String, sessionToken: Option[String])
