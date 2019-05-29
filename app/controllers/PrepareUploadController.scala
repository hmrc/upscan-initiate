package controllers

import java.net.URL
import java.time.{Clock, Instant}

import config.ServiceConfiguration
import controllers.model.{PrepareUploadRequestV1, PreparedUploadResponse, UploadFormTemplate}
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsPath, Json, Writes, _}
import play.api.mvc.{Action, Result}
import services.PrepareUploadService
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import utils.UserAgentFilter

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class PrepareUploadController @Inject()(
  prepareUploadService: PrepareUploadService,
  override val configuration: ServiceConfiguration,
  clock: Clock)
    extends BaseController
    with UserAgentFilter {

  implicit val uploadSettingsReads: Reads[PrepareUploadRequestV1] = (
    (JsPath \ "callbackUrl").read[String] and
      (JsPath \ "minimumFileSize").readNullable[Int](min(0)) and
      (JsPath \ "maximumFileSize").readNullable[Int](min(0) keepAnd max(prepareUploadService.globalFileSizeLimit + 1)) and
      (JsPath \ "expectedContentType").readNullable[String] and
      (JsPath \ "successRedirect").readNullable[String]
  )(PrepareUploadRequestV1.apply _)
    .filter(ValidationError("Maximum file size must be equal or greater than minimum file size"))(settings =>
      settings.minimumFileSize.getOrElse(0) <= settings.maximumFileSize.getOrElse(
        prepareUploadService.globalFileSizeLimit))

  implicit val uploadFormTemplateWrites: Writes[UploadFormTemplate] = Json.writes[UploadFormTemplate]

  implicit val preparedUploadWrites: Writes[PreparedUploadResponse] = new Writes[PreparedUploadResponse] {
    def writes(preparedUpload: PreparedUploadResponse): JsValue = Json.obj(
      "reference"     -> preparedUpload.reference.value,
      "uploadRequest" -> preparedUpload.uploadRequest
    )
  }

  def prepareUpload(): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      val receivedAt = Instant.now(clock)

      onlyAllowedServices[JsValue] { (_, consumingService) =>
        withJsonBody[PrepareUploadRequestV1] { uploadSettings: PrepareUploadRequestV1 =>
          withAllowedCallbackProtocol(uploadSettings.callbackUrl) {
            Logger.debug(s"Processing request: [$uploadSettings].")

            val sessionId = hc(request).sessionId.map(_.value).getOrElse("n/a")
            val requestId = hc(request).requestId.map(_.value).getOrElse("n/a")
            val result: PreparedUploadResponse =
              prepareUploadService.prepareUpload(uploadSettings, consumingService, requestId, sessionId, receivedAt)

            Future.successful(Ok(Json.toJson(result)))
          }
        }
      }
    }

  private[controllers] def withAllowedCallbackProtocol[A](callbackUrl: String)(
    block: => Future[Result]): Future[Result] = {

    val allowedCallbackProtocols: Seq[String] = configuration.allowedCallbackProtocols

    val isAllowedCallbackProtocol: Try[Boolean] = Try {
      allowedCallbackProtocols.contains(new URL(callbackUrl).getProtocol)
    }

    isAllowedCallbackProtocol match {
      case Success(true) => block
      case Success(false) => {
        Logger.warn(s"Invalid callback url protocol: [$callbackUrl].")

        Future.successful(BadRequest(
          s"Invalid callback url protocol: [$callbackUrl]. Protocol must be in: [${allowedCallbackProtocols.mkString(",")}]."))
      }
      case Failure(e) => {
        Logger.warn(s"Invalid callback url format: [$callbackUrl].")

        Future.successful(BadRequest(s"Invalid callback url format: [$callbackUrl]. [${e.getMessage}]"))
      }
    }

  }
}
