package controllers

import javax.inject.{Inject, Singleton}

import domain._
import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, Json, Writes}
import play.api.mvc.Action
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import Reads._

import scala.concurrent.Future

@Singleton
class PrepareUploadController @Inject()(prepareUploadService: PrepareUploadService) extends BaseController {

  implicit val uploadSettingsReads: Reads[UploadSettings] = (
    (JsPath \ "callbackUrl").read[String] and
      (JsPath \ "minimumFileSize").readNullable[Int](min(0)) and
      (JsPath \ "maximumFileSize").readNullable[Int](min(0) keepAnd max(prepareUploadService.globalFileSizeLimit + 1)) and
      (JsPath \ "expectedContentType").readNullable[String]
  )(UploadSettings.apply _)
    .filter(ValidationError("Maximum file size must be equal or greater than minimum file size"))(
      settings =>
        settings.minimumFileSize.getOrElse(0) <= settings.maximumFileSize.getOrElse(
          prepareUploadService.globalFileSizeLimit)
    )

  implicit val uploadFormTemplateWrites: Writes[UploadFormTemplate] = Json.writes[UploadFormTemplate]

  implicit val preparedUploadWrites: Writes[PreparedUpload] = new Writes[PreparedUpload] {
    def writes(preparedUpload: PreparedUpload) = Json.obj(
      "reference"     -> preparedUpload.reference.value,
      "uploadRequest" -> preparedUpload.uploadRequest
    )
  }

  def prepareUpload(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[UploadSettings] { (fileUploadDetails: UploadSettings) =>
      val result: PreparedUpload = prepareUploadService.setupUpload(fileUploadDetails)
      Future.successful(Ok(Json.toJson(result)))
    }
  }

}
