package controllers

import config.ServiceConfiguration
import domain._
import javax.inject.{Inject, Singleton}
import java.net.URL
import play.api.Logger
import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsPath, Json, Writes, _}
import play.api.mvc.{Action, Result}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import utils.UserAgentFilter

@Singleton
class PrepareUploadController @Inject()(
  prepareUploadService: PrepareUploadService,
  override val configuration: ServiceConfiguration)
    extends BaseController
    with UserAgentFilter {

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

  def prepareUpload(): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      onlyAllowedServices[JsValue] { (_, consumingService) =>

        withJsonBody[UploadSettings] { (fileUploadDetails: UploadSettings) =>

          withAllowedCallbackProtocol(fileUploadDetails.callbackUrl) {
            Logger.debug(s"Processing request: [$fileUploadDetails].")

            val sessionId = hc(request).sessionId.map(_.value).getOrElse("n/a")
            val requestId = hc(request).requestId.map(_.value).getOrElse("n/a")
            val result: PreparedUpload =
              prepareUploadService.prepareUpload(fileUploadDetails, consumingService, requestId, sessionId)

            Future.successful(Ok(Json.toJson(result)))
          }
        }
      }
    }

  private[controllers] def withAllowedCallbackProtocol[A](callbackUrl: String)
                                            (block: => Future[Result]): Future[Result]= {

    val isHttps: Try[Boolean] = Try {
      new URL(callbackUrl).getProtocol == "https"
    }

    isHttps match {
      case Success(true) => block
      case Success(false) => {
        Logger.warn(s"Invalid callback url protocol: [$callbackUrl].")

        Future.successful(BadRequest(s"Invalid callback url protocol: [$callbackUrl]. Protocol must be https."))
      }
      case Failure(e) => {
        Logger.warn(s"Invalid callback url format: [$callbackUrl].")

        Future.successful(BadRequest(s"Invalid callback url format: [$callbackUrl]. [${e.getMessage}]"))
      }
    }

  }
}
