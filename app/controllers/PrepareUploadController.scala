package controllers

import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Action
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

class PrepareUploadController extends BaseController  {

  case class UploadSettings(id : String, callbackUrl : String)

  case class Link(href : String, method : String)

  implicit val uploadedFileSettingsFormat = Json.format[UploadSettings]

  def prepareUpload(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[UploadSettings] {
      (fileUploadDetails: UploadSettings) =>
      for (
        result <- setupUploadUrl(fileUploadDetails.id)
      ) yield Ok(Json.obj("_links" -> Json.obj(
          "upload" -> Json.obj(
            "href" -> result.href,
            "method" -> result.method
          )
      )))
    }
  }

  private def setupUploadUrl(id : String) : Future[Link] = Future.successful(Link(s"http://localhost:8080/${id}", "PUT"))

}