package controllers.model

import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads.{max, min}
import play.api.libs.json.{JsPath, Reads}
import services.model.UploadSettings

case class PrepareUploadRequestV1(
  callbackUrl: String,
  minimumFileSize: Option[Int],
  maximumFileSize: Option[Int],
  expectedContentType: Option[String],
  successRedirect: Option[String])
    extends PrepareUpload {

  def toUploadSettings(uploadUrl: String): UploadSettings = UploadSettings(
    uploadUrl           = uploadUrl,
    callbackUrl         = callbackUrl,
    minimumFileSize     = minimumFileSize,
    maximumFileSize     = maximumFileSize,
    expectedContentType = expectedContentType,
    successRedirect     = successRedirect,
    errorRedirect       = None
  )

}

object PrepareUploadRequestV1 {

  def reads(maxFileSize: Int): Reads[PrepareUploadRequestV1] =
    ((JsPath \ "callbackUrl").read[String] and
      (JsPath \ "minimumFileSize").readNullable[Int](min(0)) and
      (JsPath \ "maximumFileSize").readNullable[Int](min(0) keepAnd max(maxFileSize + 1)) and
      (JsPath \ "expectedContentType").readNullable[String] and
      (JsPath \ "successRedirect").readNullable[String])(PrepareUploadRequestV1.apply _)
      .filter(ValidationError("Maximum file size must be equal or greater than minimum file size"))(request =>
        request.minimumFileSize.getOrElse(0) <= request.maximumFileSize.getOrElse(maxFileSize))

}
