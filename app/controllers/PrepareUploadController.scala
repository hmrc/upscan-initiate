package controllers

import javax.inject.Inject

import domain.{PrepareUploadService, UploadSettings}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Action
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

class PrepareUploadController @Inject() (prepareUploadService : PrepareUploadService) extends BaseController  {

  implicit val uploadedFileSettingsFormat = Json.format[UploadSettings]

  def prepareUpload(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[UploadSettings] {
      (fileUploadDetails: UploadSettings) =>
      for (
        result <- prepareUploadService.setupUpload(fileUploadDetails)
      ) yield Ok(Json.obj("_links" -> Json.obj(
          "upload" -> Json.obj(
            "href" -> result.href,
            "method" -> result.method
          )
      )))
    }
  }



}