package controllers

import java.net.URL
import java.time.{Clock, Instant}

import config.ServiceConfiguration
import controllers.model.{PrepareUploadRequestV1, PreparedUploadResponse}
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.{Json, _}
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

  implicit val prepareUploadRequestReads: Reads[PrepareUploadRequestV1] =
    PrepareUploadRequestV1.reads(prepareUploadService.globalFileSizeLimit)

  def prepareUpload(): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      val receivedAt = Instant.now(clock)

      onlyAllowedServices[JsValue] { (_, consumingService) =>
        withJsonBody[PrepareUploadRequestV1] { prepareUploadRequest: PrepareUploadRequestV1 =>
          withAllowedCallbackProtocol(prepareUploadRequest.callbackUrl) {
            Logger.debug(s"Processing request: [$prepareUploadRequest].")

            val sessionId = hc(request).sessionId.map(_.value).getOrElse("n/a")
            val requestId = hc(request).requestId.map(_.value).getOrElse("n/a")
            val result: PreparedUploadResponse =
              prepareUploadService
                .prepareUpload(
                  prepareUploadRequest.toUploadSettings,
                  consumingService,
                  requestId,
                  sessionId,
                  receivedAt)

            Future.successful(Ok(Json.toJson(result)(PreparedUploadResponse.writes)))
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
