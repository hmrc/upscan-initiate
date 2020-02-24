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

package services

import java.time.Instant

import com.kenshoo.play.metrics.Metrics
import config.ServiceConfiguration
import connectors.model.{ContentLengthRange, UploadFormGenerator, UploadParameters}
import controllers.model.{PreparedUploadResponse, Reference, UploadFormTemplate}
import javax.inject.{Inject, Singleton}
import org.slf4j.MDC
import play.api.Logger
import services.model.UploadSettings

@Singleton
class PrepareUploadService @Inject()(
  postSigner: UploadFormGenerator,
  configuration: ServiceConfiguration,
  metrics: Metrics) {

  def prepareUpload(
    settings: UploadSettings,
    consumingService: String,
    requestId: String,
    sessionId: String,
    receivedAt: Instant): PreparedUploadResponse = {
    val reference  = Reference.generate()
    val expiration = receivedAt.plus(configuration.fileExpirationPeriod)

    val result =
      PreparedUploadResponse(
        reference = reference,
        uploadRequest = generatePost(
          key              = reference,
          expiration       = expiration,
          settings         = settings,
          consumingService = consumingService,
          requestId        = requestId,
          sessionId        = sessionId,
          receivedAt       = receivedAt)
      )

    try {
      MDC.put("file-reference", reference.toString)
      Logger.info(
        s"Generated file-reference: [$reference], for settings: [$settings], with expiration at: [$expiration].")

      metrics.defaultRegistry.counter("uploadInitiated").inc()

      result
    } finally {
      MDC.remove("file-reference")
    }
  }

  private def generatePost(
    key: Reference,
    expiration: Instant,
    settings: UploadSettings,
    consumingService: String,
    requestId: String,
    sessionId: String,
    receivedAt: Instant): UploadFormTemplate = {

    val minFileSize = settings.minimumFileSize.getOrElse(0)
    val maxFileSize = settings.maximumFileSize.getOrElse(globalFileSizeLimit)

    require(minFileSize >= 0, "Minimum file size is less than 0")
    require(maxFileSize <= globalFileSizeLimit, "Maximum file size is greater than global maximum file size")
    require(minFileSize <= maxFileSize, "Minimum file size is greater than maximum file size")

    val uploadParameters = UploadParameters(
      expirationDateTime = expiration,
      bucketName         = configuration.inboundBucketName,
      objectKey          = key.value,
      acl                = "private",
      additionalMetadata = Map(
        "callback-url"             -> settings.callbackUrl,
        "consuming-service"        -> consumingService,
        "session-id"               -> sessionId,
        "request-id"               -> requestId,
        "upscan-initiate-received" -> receivedAt.toString
      ),
      contentLengthRange  = ContentLengthRange(minFileSize, maxFileSize),
      expectedContentType = settings.expectedContentType,
      successRedirect     = settings.successRedirect,
      errorRedirect       = settings.errorRedirect
    )

    val form     = postSigner.generateFormFields(uploadParameters)
    val endpoint = settings.uploadUrl

    UploadFormTemplate(endpoint, form)
  }

  def globalFileSizeLimit: Int = configuration.globalFileSizeLimit
}
