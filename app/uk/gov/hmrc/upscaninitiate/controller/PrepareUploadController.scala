/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.upscaninitiate.controller

import play.api.Logging
import play.api.libs.json._
import play.api.mvc.{Action, ControllerComponents, Result}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.upscaninitiate.config.ServiceConfiguration
import uk.gov.hmrc.upscaninitiate.controller.model.{PrepareUploadRequest, PreparedUploadResponse}
import uk.gov.hmrc.upscaninitiate.service.PrepareUploadService
import uk.gov.hmrc.upscaninitiate.service.model.UploadSettings
import uk.gov.hmrc.upscaninitiate.util.UserAgentFilter

import java.net.URL
import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class PrepareUploadController @Inject()(
  prepareUploadService: PrepareUploadService,
  configuration: ServiceConfiguration,
  clock: Clock,
  controllerComponents: ControllerComponents) extends BackendController(controllerComponents) with UserAgentFilter with Logging {

  val prepareUploadRequestReadsV1: Reads[PrepareUploadRequest] =
    PrepareUploadRequest.readsV1(prepareUploadService.globalFileSizeLimit)

  val prepareUploadRequestReadsV2: Reads[PrepareUploadRequest] =
    PrepareUploadRequest.readsV2(prepareUploadService.globalFileSizeLimit)

  /**
   * V1 of the API supports direct upload to an S3 bucket and *does not support* error redirects in the event of failure
   */
  def prepareUploadV1: Action[JsValue] = {
    val uploadUrl = s"https://${configuration.inboundBucketName}.s3.amazonaws.com"
    prepareUpload(uploadUrl)(prepareUploadRequestReadsV1)
  }

  /**
   * V2 of the API supports upload to an S3 bucket via a proxy that additionally supports error redirects in the event of failure
   */
  def prepareUploadV2: Action[JsValue] = {
    val uploadUrl = s"${configuration.uploadProxyUrl}/v1/uploads/${configuration.inboundBucketName}"
    prepareUpload(uploadUrl)(prepareUploadRequestReadsV2)
  }

  private def prepareUpload(uploadUrl: String)(
    implicit reads: Reads[PrepareUploadRequest]): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      val receivedAt = Instant.now(clock)

      requireUserAgent[JsValue] { (_, userAgent) =>
        withJsonBody[PrepareUploadRequest] { prepareUploadRequest =>
          withAllowedCallbackProtocol(prepareUploadRequest.callbackUrl) {

            val sessionId = hc(request).sessionId.map(_.value).getOrElse("n/a")
            val requestId = hc(request).requestId.map(_.value).getOrElse("n/a")
            logger.debug(s"Processing request: [$prepareUploadRequest] with requestId: [$requestId] sessionId: [$sessionId] from: [$userAgent].")

            val settings =
              UploadSettings(
                uploadUrl            = uploadUrl,
                userAgent            = userAgent,
                prepareUploadRequest = prepareUploadRequest
              )

            val result =
              prepareUploadService
                .prepareUpload(
                  settings   = settings,
                  requestId  = requestId,
                  sessionId  = sessionId,
                  receivedAt = receivedAt
                )

            Future.successful(Ok(Json.toJson(result)(PreparedUploadResponse.writes)))
          }
        }
      }
    }

  private[controller] def withAllowedCallbackProtocol[A](callbackUrl: String)(
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
