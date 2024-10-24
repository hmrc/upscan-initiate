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

package uk.gov.hmrc.upscaninitiate.model

import com.codahale.metrics.MetricRegistry
import org.mockito.Mockito.when
import org.scalatest.GivenWhenThen
import uk.gov.hmrc.upscaninitiate.config.ServiceConfiguration
import uk.gov.hmrc.upscaninitiate.connector.model.{UploadFormGenerator, UploadParameters}
import uk.gov.hmrc.upscaninitiate.controller.model.PrepareUploadRequest
import uk.gov.hmrc.upscaninitiate.model.PrepareUploadServiceSpec.{requestTemplate, settingsTemplate}
import uk.gov.hmrc.upscaninitiate.service.PrepareUploadService
import uk.gov.hmrc.upscaninitiate.service.model.UploadSettings
import uk.gov.hmrc.upscaninitiate.test.UnitSpec

import java.time.Instant
import scala.concurrent.duration.DurationInt

class PrepareUploadServiceSpec extends UnitSpec with GivenWhenThen:

  val receivedAt: Instant = Instant.now()

  "S3 Prepare Upload Service" should:
    "create post form that allows to upload the file" in new Setup:
      Given("there are valid upload settings")
      val settings = settingsTemplate

      When("we setup the upload")

      val result =
        service
          .prepareUpload(
            settings,
            "some-request-id",
            "some-session-id",
            receivedAt
          )

      Then("proper upload request form definition should be returned")

      result.uploadRequest.href shouldBe settings.uploadUrl
      result.uploadRequest.fields shouldBe Map(
        "bucket"                              -> serviceConfiguration.inboundBucketName,
        "key"                                 -> result.reference.value,
        "x-amz-meta-callback-url"             -> settings.prepareUploadRequest.callbackUrl,
        "x-amz-meta-consuming-service"        -> settings.consumingService,
        "x-amz-meta-session-id"               -> "some-session-id",
        "x-amz-meta-request-id"               -> "some-request-id",
        "minSize"                             -> "0",
        "maxSize"                             -> "1024",
        "x-amz-meta-upscan-initiate-received" -> receivedAt.toString
      )

      And("uploadInitiated counter has been incremented")
      metricRegistry.counter("uploadInitiated").getCount shouldBe 1

    "take in account file size limits if provided in request" in new Setup:
      Given("there are valid upload settings with size limits")
      val settings =
        settingsTemplate
          .copy(
            prepareUploadRequest =
              requestTemplate.copy(
                minimumFileSize = Some(100),
                maximumFileSize = Some(200),
              )
          )

      When("we setup the upload")
      val result = service.prepareUpload(settings, "some-request-id", "some-session-id", receivedAt)

      Then("upload request should contain requested min/max size")

      result.uploadRequest.fields("minSize") shouldBe "100"
      result.uploadRequest.fields("maxSize") shouldBe "200"

    "fail when minimum file size is less than 0" in new Setup:
      Given("there are upload settings with minimum file size less than zero")
      val settings =
        settingsTemplate
          .copy(
            prepareUploadRequest =
              requestTemplate.copy(
                minimumFileSize = Some(-1),
                maximumFileSize = Some(1024),
              )
          )

      When("we setup the upload")
      Then("an exception should be thrown")

      val thrown = the[IllegalArgumentException] thrownBy service
        .prepareUpload(settings, "some-request-id", "some-session-id", receivedAt)
      thrown.getMessage should include("Minimum file size is less than 0")

      metricRegistry.counter("uploadInitiated").getCount shouldBe 0

    "fail when maximum file size is greater than global limit" in new Setup:
      Given("there upload settings with maximum file size greater than global limit")
      val settings =
        settingsTemplate
          .copy(
            prepareUploadRequest =
              requestTemplate.copy(
                minimumFileSize = Some(0),
                maximumFileSize = Some(1025),
              )
          )

      When("we setup the upload")
      Then("an exception should be thrown")
      val thrown = the[IllegalArgumentException] thrownBy service
        .prepareUpload(settings, "some-request-id", "some-session-id", receivedAt)
      thrown.getMessage should include("Maximum file size is greater than global maximum file size")

      metricRegistry.counter("uploadInitiated").getCount shouldBe 0

    "fail when minimum file size is greater than maximum file size" in new Setup:
      Given("there are upload settings with minimum file size greater than maximum size")
      val settings =
        settingsTemplate
          .copy(
            prepareUploadRequest =
              requestTemplate.copy(
                minimumFileSize = Some(1024),
                maximumFileSize = Some(0),
              )
          )

      When("we setup the upload")
      Then("an exception should be thrown")
      val thrown = the[IllegalArgumentException] thrownBy service
        .prepareUpload(settings, "some-request-id", "some-session-id", receivedAt)
      thrown.getMessage should include("Minimum file size is greater than maximum file size")

      metricRegistry.counter("uploadInitiated").getCount shouldBe 0

    "create post form that allows to upload the file with redirect on success" in new Setup:
      Given("there are valid upload settings")
      val settings =
        settingsTemplate
          .copy(
            prepareUploadRequest =
              requestTemplate.copy(
                successRedirect = Some("https://new.service/page1")
              )
          )

      When("we setup the upload")

      val result = service.prepareUpload(settings, "some-request-id", "some-session-id", receivedAt)

      Then("proper upload request form definition should be returned")
      result.uploadRequest.href shouldBe settings.uploadUrl
      result.uploadRequest.fields shouldBe Map(
        "bucket"                              -> serviceConfiguration.inboundBucketName,
        "key"                                 -> result.reference.value,
        "x-amz-meta-callback-url"             -> settings.prepareUploadRequest.callbackUrl,
        "x-amz-meta-consuming-service"        -> settings.consumingService,
        "x-amz-meta-session-id"               -> "some-session-id",
        "x-amz-meta-request-id"               -> "some-request-id",
        "minSize"                             -> "0",
        "maxSize"                             -> "1024",
        "x-amz-meta-upscan-initiate-received" -> receivedAt.toString,
        "success_redirect_url"                -> settings.prepareUploadRequest.successRedirect.get
      )

      And("uploadInitiated counter has been incremented")
      metricRegistry.counter("uploadInitiated").getCount shouldBe 1

    "create post form that allows to upload the file with redirect on error" in new Setup:
      Given("there are valid upload settings")
      val settings =
        settingsTemplate
          .copy(
            prepareUploadRequest =
              requestTemplate.copy(
                errorRedirect = Some("https://new.service/error")
              )
          )

      When("we setup the upload")
      val result = service.prepareUpload(settings, "some-request-id", "some-session-id", receivedAt)

      Then("proper upload request form definition should be returned")
      result.uploadRequest.href shouldBe settings.uploadUrl
      result.uploadRequest.fields shouldBe Map(
        "bucket"                              -> serviceConfiguration.inboundBucketName,
        "key"                                 -> result.reference.value,
        "x-amz-meta-callback-url"             -> settings.prepareUploadRequest.callbackUrl,
        "x-amz-meta-consuming-service"        -> settings.consumingService,
        "x-amz-meta-session-id"               -> "some-session-id",
        "x-amz-meta-request-id"               -> "some-request-id",
        "minSize"                             -> "0",
        "maxSize"                             -> "1024",
        "x-amz-meta-upscan-initiate-received" -> receivedAt.toString,
        "error_redirect_url"                  -> settings.prepareUploadRequest.errorRedirect.get
      )

      And("uploadInitiated counter has been incremented")
      metricRegistry.counter("uploadInitiated").getCount shouldBe 1

  trait Setup:
    val serviceConfiguration = mock[ServiceConfiguration]
    when(serviceConfiguration.inboundBucketName)
      .thenReturn("test-bucket")
    when(serviceConfiguration.fileExpirationPeriod)
      .thenReturn(7.days)
    when(serviceConfiguration.globalFileSizeLimit)
      .thenReturn(1024L)
    when(serviceConfiguration.allowedCallbackProtocols)
      .thenReturn(List("https"))

    val s3PostSigner = new UploadFormGenerator:
      override def generateFormFields(uploadParameters: UploadParameters): Map[String, String] =
        Map(
          "bucket"  -> uploadParameters.bucketName,
          "key"     -> uploadParameters.objectKey,
          "minSize" -> uploadParameters.contentLengthRange.min.toString,
          "maxSize" -> uploadParameters.contentLengthRange.max.toString
        )
          ++ uploadParameters.additionalMetadata.map { case (k, v) => s"x-amz-meta-$k" -> v }
          ++ uploadParameters.successRedirect.map { "success_redirect_url" -> _ }
          ++ uploadParameters.errorRedirect.map { "error_redirect_url"     -> _ }


    val metricRegistry = MetricRegistry()
    val service        = PrepareUploadService(s3PostSigner, serviceConfiguration, metricRegistry)

object PrepareUploadServiceSpec {

  val requestTemplate: PrepareUploadRequest =
    PrepareUploadRequest(
      callbackUrl      = "http://www.callback.com",
      minimumFileSize  = None,
      maximumFileSize  = None,
      successRedirect  = None,
      errorRedirect    = None,
      consumingService = None
    )

  val settingsTemplate: UploadSettings =
    UploadSettings(
      uploadUrl            = s"http://upload-proxy.com",
      userAgent            = "PrepareUploadServiceSpec",
      prepareUploadRequest = requestTemplate
    )
}
