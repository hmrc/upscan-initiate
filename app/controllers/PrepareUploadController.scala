/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import java.net.URL
import java.time.{Clock, Instant}

import config.ServiceConfiguration
import controllers.model.{PrepareUpload, PrepareUploadRequestV1, PrepareUploadRequestV2, PreparedUploadResponse}
import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.libs.json._
import play.api.mvc.{Action, ControllerComponents, Result}
import services.PrepareUploadService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.UserAgentFilter

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class PrepareUploadController @Inject()(
  prepareUploadService: PrepareUploadService,
  configuration: ServiceConfiguration,
  clock: Clock,
  controllerComponents: ControllerComponents) extends BackendController(controllerComponents) with UserAgentFilter with Logging {

  implicit val prepareUploadRequestReads: Reads[PrepareUploadRequestV1] =
    PrepareUploadRequestV1.reads(prepareUploadService.globalFileSizeLimit)

  implicit val prepareUploadRequestV2Reads: Reads[PrepareUploadRequestV2] =
    PrepareUploadRequestV2.reads(prepareUploadService.globalFileSizeLimit)

  def prepareUploadV1(): Action[JsValue] = {
    val uploadUrl = s"https://${configuration.inboundBucketName}.s3.amazonaws.com"
    prepareUpload[PrepareUploadRequestV1](uploadUrl)
  }

  def prepareUploadV2(): Action[JsValue] = {
    val uploadUrl = s"${configuration.uploadProxyUrl}/v1/uploads/${configuration.inboundBucketName}"
    prepareUpload[PrepareUploadRequestV2](uploadUrl)
  }

  private def prepareUpload[T <: PrepareUpload](uploadUrl: String)
                                               (implicit reads: Reads[T], manifest: Manifest[T]): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      val receivedAt = Instant.now(clock)

      requireUserAgent[JsValue] { (_, consumingService) =>
        withJsonBody[T] { prepareUploadRequest: T =>
          withAllowedCallbackProtocol(prepareUploadRequest.callbackUrl) {
            val sessionId = hc(request).sessionId.map(_.value).getOrElse("n/a")
            val requestId = hc(request).requestId.map(_.value).getOrElse("n/a")
            logger.debug(s"Processing request: [$prepareUploadRequest] with requestId: [$requestId] sessionId: [$sessionId] from: [$consumingService].")
            val result: PreparedUploadResponse =
              prepareUploadService
                .prepareUpload(
                  settings         = prepareUploadRequest.toUploadSettings(uploadUrl),
                  consumingService = consumingService,
                  requestId        = requestId,
                  sessionId        = sessionId,
                  receivedAt       = receivedAt
                )

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
      case Success(false) =>
        logger.warn(s"Invalid callback url protocol: [$callbackUrl].")

        Future.successful(BadRequest(
          s"Invalid callback url protocol: [$callbackUrl]. Protocol must be in: [${allowedCallbackProtocols.mkString(",")}]."))

      case Failure(e) =>
        logger.warn(s"Invalid callback url format: [$callbackUrl].")

        Future.successful(BadRequest(s"Invalid callback url format: [$callbackUrl]. [${e.getMessage}]"))
    }
  }
}
