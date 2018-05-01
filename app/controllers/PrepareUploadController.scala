package controllers

import javax.inject.{Inject, Singleton}
import config.ServiceConfiguration
import domain._
import play.api.Logger
import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsPath, Json, Writes, _}
import play.api.mvc.Action
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import utils.UserAgentFilter

import scala.concurrent.Future

@Singleton
class PrepareUploadController @Inject()(prepareUploadService: PrepareUploadService,
                                        override val configuration: ServiceConfiguration)
  extends BaseController with UserAgentFilter {

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

  def prepareUpload(): Action[JsValue] = onlyAllowedServices {
    Action.async(parse.json) { implicit request =>
      withJsonBody[UploadSettings] { (fileUploadDetails: UploadSettings) =>
        Logger.debug(s"Processing request: [$fileUploadDetails].")
        val result: PreparedUpload = prepareUploadService.prepareUpload(fileUploadDetails)
        Future.successful(Ok(Json.toJson(result)))
      }
    }
  }
}
