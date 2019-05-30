package services.model

case class UploadSettings(
  callbackUrl: String,
  minimumFileSize: Option[Int],
  maximumFileSize: Option[Int],
  expectedContentType: Option[String],
  successRedirect: Option[String],
  errorRedirect: Option[String])
