package controllers.model

import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, Reads}
import play.api.libs.json.Reads.{max, min}
import services.model.UploadSettings
import play.api.libs.functional.syntax._

case class PrepareUploadRequestV2(
  callbackUrl: String,
  successRedirect: String,
  errorRedirect: String,
  minimumFileSize: Option[Int],
  maximumFileSize: Option[Int],
  expectedContentType: Option[String])
    extends PrepareUpload {

  def toUploadSettings(uploadUrl: String): UploadSettings = UploadSettings(
    uploadUrl           = uploadUrl,
    callbackUrl         = callbackUrl,
    minimumFileSize     = minimumFileSize,
    maximumFileSize     = maximumFileSize,
    expectedContentType = expectedContentType,
    successRedirect     = Some(successRedirect),
    errorRedirect       = Some(errorRedirect)
  )
}

object PrepareUploadRequestV2 {

  def reads(maxFileSize: Int): Reads[PrepareUploadRequestV2] =
    ((JsPath \ "callbackUrl").read[String] and
      (JsPath \ "successRedirect").read[String] and
      (JsPath \ "errorRedirect").read[String] and
      (JsPath \ "minimumFileSize").readNullable[Int](min(0)) and
      (JsPath \ "maximumFileSize").readNullable[Int](min(0) keepAnd max(maxFileSize + 1)) and
      (JsPath \ "expectedContentType").readNullable[String])(PrepareUploadRequestV2.apply _)
      .filter(ValidationError("Maximum file size must be equal or greater than minimum file size"))(request =>
        request.minimumFileSize.getOrElse(0) <= request.maximumFileSize.getOrElse(maxFileSize))

}
