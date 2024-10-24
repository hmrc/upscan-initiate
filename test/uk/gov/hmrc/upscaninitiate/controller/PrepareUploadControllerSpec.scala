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

import org.apache.pekko.actor.ActorSystem
import org.mockito.Mockito.when
import org.scalatest.GivenWhenThen
import play.api.http.HeaderNames.USER_AGENT
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Action
import play.api.mvc.Results.Ok
import play.api.test.Helpers.{contentAsJson, contentAsString, status}
import play.api.test.{FakeRequest, Helpers, StubControllerComponentsFactory}
import uk.gov.hmrc.upscaninitiate.config.ServiceConfiguration
import uk.gov.hmrc.upscaninitiate.controller.model.{PrepareUploadRequest, PreparedUploadResponse, Reference, UploadFormTemplate}
import uk.gov.hmrc.upscaninitiate.service.PrepareUploadService
import uk.gov.hmrc.upscaninitiate.service.model.UploadSettings
import uk.gov.hmrc.upscaninitiate.test.UnitSpec

import java.time.Clock
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PrepareUploadControllerSpec
  extends UnitSpec
     with StubControllerComponentsFactory
     with GivenWhenThen:

  import PrepareUploadControllerSpec._

  private given ActorSystem = ActorSystem()

  private given org.apache.pekko.util.Timeout = 10.seconds

  private val clock = Clock.fixed(Clock.systemDefaultZone().instant(), Clock.systemDefaultZone().getZone)

  private trait WithGlobalFileSizeLimitFixture {
    val globalFileSizeLimit  = 1024L
    val prepareUploadService = mock[PrepareUploadService]
    when(prepareUploadService.globalFileSizeLimit)
      .thenReturn(globalFileSizeLimit)
  }

  private trait WithServiceConfiguration:
    val config = mock[ServiceConfiguration]

  private trait WithAllowedCallbackProtocol:
    this: WithServiceConfiguration =>
    when(config.allowedCallbackProtocols)
      .thenReturn(List("https"))

  private trait WithInboundBucketName:
    this: WithServiceConfiguration =>
    when(config.inboundBucketName)
      .thenReturn("inbound-bucket")

  private trait WithUploadProxyUrl:
    this: WithServiceConfiguration =>
    when(config.uploadProxyUrl)
      .thenReturn("https://upload-proxy.com")

  private trait WithAllowedCallbackProtocolFixture extends WithServiceConfiguration           with WithAllowedCallbackProtocol
  private trait WithV1SuccessFixture               extends WithAllowedCallbackProtocolFixture with WithInboundBucketName
  private trait WithV2SuccessFixture               extends WithV1SuccessFixture               with WithUploadProxyUrl
  private trait WithV1BadRequestFixture            extends WithServiceConfiguration           with WithInboundBucketName
  private trait WithV2BadRequestFixture            extends WithV1BadRequestFixture            with WithUploadProxyUrl

  "V1 initiate with minimal request settings" should:
    val minimalRequestBody =
      Json.obj("callbackUrl" -> "https://www.example.com")

    val expectedUploadSettings =
      settingsTemplate
        .copy(
          uploadUrl = "https://inbound-bucket.s3.amazonaws.com"
        )

    val _ = new WithV1SuccessFixture:
      behave like
        successfulInitiate(
          config                   = config,
          actionUnderTest          = _.prepareUploadV1(),
          requestBody              = minimalRequestBody,
          requestId                = None,
          sessionId                = None,
          expectedUploadSettings   = expectedUploadSettings
        )

  "V1 initiate with all request settings" should:
    val maximalRequestBody =
      Json.obj(
        "callbackUrl" -> "https://www.example.com",
        "minimumFileSize" -> 1,
        "maximumFileSize" -> 1024,
        "successRedirect" -> "https://www.example.com/success",
        "consumingService" -> consumingService
      )

    val expectedUploadSettings =
      settingsTemplate
        .copy(
          uploadUrl            = "https://inbound-bucket.s3.amazonaws.com",
          prepareUploadRequest =
            requestTemplate.copy(
              minimumFileSize  = Some(1),
              maximumFileSize  = Some(1024),
              successRedirect  = Some("https://www.example.com/success"),
              consumingService = Some(consumingService)
            )
        )

    val _ = new WithV1SuccessFixture:
      behave like
        successfulInitiate(
          config                   = config,
          actionUnderTest          = _.prepareUploadV1(),
          requestBody              = maximalRequestBody,
          requestId                = Some("a-request-id"),
          sessionId                = Some("a-session-id"),
          expectedUploadSettings   = expectedUploadSettings
        )

  "V2 initiate with minimal request settings" should:
    val minimalRequestBody =
      Json.obj("callbackUrl" -> "https://www.example.com")

    val expectedUploadSettings =
      settingsTemplate
        .copy(
          uploadUrl = "https://upload-proxy.com/v1/uploads/inbound-bucket"
        )

    val _ = new WithV2SuccessFixture:
      behave like
        successfulInitiate(
          config                   = config,
          actionUnderTest          = _.prepareUploadV2(),
          requestBody              = minimalRequestBody,
          requestId                = None,
          sessionId                = None,
          expectedUploadSettings   = expectedUploadSettings
        )

  "V2 initiate with all request settings" should {
    val maximalRequestBody =
      Json.obj(
        "callbackUrl"      -> "https://www.example.com",
        "minimumFileSize"  -> 1,
        "maximumFileSize"  -> 1024,
        "successRedirect"  -> "https://www.example.com/success",
        "errorRedirect"    -> "https://www.example.com/error",
        "consumingService" -> consumingService
      )

    val expectedUploadSettings =
      settingsTemplate
        .copy(
          uploadUrl            = "https://upload-proxy.com/v1/uploads/inbound-bucket",
          prepareUploadRequest = requestTemplate.copy(
              minimumFileSize  = Some(1),
              maximumFileSize  = Some(1024),
              successRedirect  = Some("https://www.example.com/success"),
              errorRedirect    = Some("https://www.example.com/error"),
              consumingService = Some(consumingService)
            )
        )

    val _ = new WithV2SuccessFixture {
      behave like
        successfulInitiate(
          config                   = config,
          actionUnderTest          = _.prepareUploadV2(),
          requestBody              = maximalRequestBody,
          requestId                = Some("a-request-id"),
          sessionId                = Some("a-session-id"),
          expectedUploadSettings   = expectedUploadSettings
        )
    }
  }

  //noinspection ScalaStyle
  private def successfulInitiate(
    config                : ServiceConfiguration,
    actionUnderTest       : PrepareUploadController => Action[JsValue],
    requestBody           : JsValue,
    requestId             : Option[String],
    sessionId             : Option[String],
    expectedUploadSettings: UploadSettings
  ): Unit =
    "prepare upload request" in new WithGlobalFileSizeLimitFixture:
      val controller = PrepareUploadController(prepareUploadService, config, clock, stubControllerComponents())

      Given("a valid initiate request")
      val headers =
        (USER_AGENT, userAgent)
          +: Seq(
            requestId.map(Tuple2("x-request-id", _)),
            sessionId.map(Tuple2("x-session-id", _))
          ).flatten

      val request = FakeRequest().withHeaders(headers: _*).withBody(requestBody)

      val expectedUploadResponse =
        PreparedUploadResponse(
          reference     = Reference("TEST"),
          uploadRequest = UploadFormTemplate(
            href   = expectedUploadSettings.uploadUrl,
            fields = Map("a" -> "b", "x" -> "y")
          )
        )

      when(prepareUploadService.prepareUpload(
        expectedUploadSettings,
        requestId.getOrElse("n/a"),
        sessionId.getOrElse("n/a"),
        clock.instant)
      ).thenReturn(expectedUploadResponse)

      When("upload initiation is requested")
      val result = actionUnderTest(controller)(request)

      Then("a response containing a reference and template of the upload form is returned")
      status(result) shouldBe OK

      val expectedFieldsAsJson = expectedUploadResponse.uploadRequest.fields.toSeq.map { kvPair =>
        s""""${kvPair._1}": "${kvPair._2}""""
      }.mkString("{", ",", "}")

      contentAsJson(result) shouldBe Json.parse(s"""{
        | "reference": "${expectedUploadResponse.reference.value}",
        | "uploadRequest": {
        |   "href"  : "${expectedUploadResponse.uploadRequest.href}",
        |   "fields": $expectedFieldsAsJson
        | }
        |}""".stripMargin
      )

  "V1 bad initiate request" should:
    val _ = new WithV1BadRequestFixture:
      behave like badRequestInitiate(config, _.prepareUploadV1())

  "V2 bad initiate request" should:
    val _ = new WithV2BadRequestFixture:
      behave like badRequestInitiate(config, _.prepareUploadV2())

  private def badRequestInitiate(// scalastyle:ignore
    config             : ServiceConfiguration,
    prepareUploadAction: PrepareUploadController => Action[JsValue]
  ): Unit =
    "return a bad request error when the request has the wrong structure" in new WithGlobalFileSizeLimitFixture:
      val controller = PrepareUploadController(prepareUploadService, config, clock, stubControllerComponents())

      Given("there is an invalid upload request")
      val request =
        FakeRequest()
          .withHeaders(
            (USER_AGENT, userAgent),
            ("x-session-id", "some-session-id"),
            ("x-request-id", "some-request-id")
          )
          .withBody(Json.obj("invalid" -> "body"))

      When("upload initiation has been requested")
      val result = prepareUploadAction(controller)(request)

      Then("service returns error response")
      withClue(Helpers.contentAsString(result)) { status(result) shouldBe BAD_REQUEST }

    "return a bad request error when the maximum file size is in excess of the global maximum" in new WithGlobalFileSizeLimitFixture:
      val controller = PrepareUploadController(prepareUploadService, config, clock, stubControllerComponents())

      Given("there is an invalid upload request")
      val request =
        FakeRequest()
        .withHeaders(
          (USER_AGENT, userAgent),
          ("x-session-id", "some-session-id")
        )
        .withBody(Json.obj(
          "callbackUrl" -> "https://www.example.com",
          "maximumFileSize" -> (globalFileSizeLimit + 1))
        )

      When("upload initiation has been requested")
      val result = prepareUploadAction(controller)(request)

      Then("service returns error response")
      withClue(Helpers.contentAsString(result)):
        status(result) shouldBe BAD_REQUEST

    "return a bad request error when no user agent is supplied" in new WithGlobalFileSizeLimitFixture:
      val controller = PrepareUploadController(prepareUploadService, config, clock, stubControllerComponents())

      Given("there is an upload request without a user agent header")
      val request = FakeRequest().withHeaders(
        ("x-session-id", "some-session-id")
      ).withBody(Json.obj("callbackUrl" -> "https://www.example.com"))

      When("upload initiation has been requested")
      val result = prepareUploadAction(controller)(request)

      Then("service returns error response")
      withClue(Helpers.contentAsString(result)):
        status(result) shouldBe BAD_REQUEST

  "withAllowedCallbackProtocol" should:
    "allow https callback urls" in new WithGlobalFileSizeLimitFixture with WithAllowedCallbackProtocolFixture:
      val controller = PrepareUploadController(prepareUploadService, config, clock, stubControllerComponents())

      val result =
        controller.withAllowedCallbackProtocol("https://my.callback.url"):
          Future.successful(Ok)

      status(result) shouldBe OK

    "disallow http callback urls" in new WithGlobalFileSizeLimitFixture with WithAllowedCallbackProtocolFixture:
      val controller = PrepareUploadController(prepareUploadService, config, clock, stubControllerComponents())

      val result =
        controller.withAllowedCallbackProtocol("http://my.callback.url"):
          Future.failed(RuntimeException("This block should not have been invoked."))

      status(result)          shouldBe BAD_REQUEST
      contentAsString(result) should include("Invalid callback url protocol")

    "disallow invalidly formatted callback urls" in new WithGlobalFileSizeLimitFixture with WithAllowedCallbackProtocolFixture:
      val controller = PrepareUploadController(prepareUploadService, config, clock, stubControllerComponents())

      val result =
        controller.withAllowedCallbackProtocol("123"):
          Future.failed(RuntimeException("This block should not have been invoked."))

      status(result)          shouldBe BAD_REQUEST
      contentAsString(result) should include("Invalid callback url format")

end PrepareUploadControllerSpec

object PrepareUploadControllerSpec:

  val userAgent        = "user-agent"
  val consumingService = "some-consuming-service"

  val requestTemplate: PrepareUploadRequest =
    PrepareUploadRequest(
      callbackUrl      = "https://www.example.com",
      minimumFileSize  = None,
      maximumFileSize  = None,
      successRedirect  = None,
      errorRedirect    = None,
      consumingService = None
    )

  val settingsTemplate: UploadSettings =
    UploadSettings(
      uploadUrl            = s"http://upload-proxy.com",
      userAgent            = userAgent,
      prepareUploadRequest = requestTemplate
    )
