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

package uk.gov.hmrc.upscaninitiate.service

import com.codahale.metrics.MetricRegistry
import org.slf4j.MDC
import play.api.Logging
import uk.gov.hmrc.upscaninitiate.config.ServiceConfiguration
import uk.gov.hmrc.upscaninitiate.connector.model.{ContentLengthRange, UploadFormGenerator, UploadParameters}
import uk.gov.hmrc.upscaninitiate.controller.model.{PreparedUploadResponse, Reference, UploadFormTemplate}
import uk.gov.hmrc.upscaninitiate.service.model.UploadSettings

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.jdk.DurationConverters.ScalaDurationOps

@Singleton
class PrepareUploadService @Inject()(
  postSigner    : UploadFormGenerator,
  configuration : ServiceConfiguration,
  metricRegistry: MetricRegistry
) extends Logging:

  def prepareUpload(
    settings  : UploadSettings,
    requestId : String,
    sessionId : String,
    receivedAt: Instant
  ): PreparedUploadResponse =

    val reference  = Reference.generate()
    val expiration = receivedAt.plus(configuration.fileExpirationPeriod.toJava)

    val result =
      PreparedUploadResponse(
        reference     = reference,
        uploadRequest = generatePost(
                          key        = reference,
                          expiration = expiration,
                          settings   = settings,
                          requestId  = requestId,
                          sessionId  = sessionId,
                          receivedAt = receivedAt
                        )
      )

    try
      MDC.put("file-reference", reference.toString)
      logger.info(s"Allocated key=[${reference.value}] to uploadRequest with requestId=[$requestId] sessionId=[$sessionId] from [${settings.consumingService}].")
      logger.debug(s"Prepared upload response [$result].")
      metricRegistry.counter("uploadInitiated").inc()

      result
    finally
      MDC.remove("file-reference")

  private def generatePost(
    key       : Reference,
    expiration: Instant,
    settings  : UploadSettings,
    requestId : String,
    sessionId : String,
    receivedAt: Instant
  ): UploadFormTemplate =

    val minFileSize = settings.prepareUploadRequest.minimumFileSize.getOrElse(0L)
    val maxFileSize = settings.prepareUploadRequest.maximumFileSize.getOrElse(defaultMaxFileSize)

    require(minFileSize >= 0                  , "Minimum file size is less than 0")
    require(maxFileSize <= maxFileSizeLimit   , "Maximum file size is greater than global maximum file size")
    require(minFileSize <= maxFileSize        , "Minimum file size is greater than maximum file size")

    val uploadParameters = UploadParameters(
      expirationDateTime = expiration,
      bucketName         = configuration.inboundBucketName,
      objectKey          = key.value,
      acl                = "private",
      additionalMetadata = Map(
                             "callback-url"             -> settings.prepareUploadRequest.callbackUrl,
                             "consuming-service"        -> settings.consumingService,
                             "session-id"               -> sessionId,
                             "request-id"               -> requestId,
                             "upscan-initiate-received" -> receivedAt.toString
                           ),
      contentLengthRange  = ContentLengthRange(minFileSize, maxFileSize),
      successRedirect     = settings.prepareUploadRequest.successRedirect,
      errorRedirect       = settings.prepareUploadRequest.errorRedirect
    )

    val form = postSigner.generateFormFields(uploadParameters)

    UploadFormTemplate(settings.uploadUrl, form)

  def maxFileSizeLimit: Long =
    configuration.maxFileSizeLimit

  def defaultMaxFileSize: Long =
    configuration.defaultMaxFileSize

end PrepareUploadService
