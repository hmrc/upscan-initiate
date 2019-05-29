package controllers.model

case class PrepareUploadRequestV1(
  callbackUrl: String,
  minimumFileSize: Option[Int],
  maximumFileSize: Option[Int],
  expectedContentType: Option[String],
  successRedirect: Option[String] = None)
