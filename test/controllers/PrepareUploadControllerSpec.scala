package controllers

import java.time.Clock

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import config.ServiceConfiguration
import controllers.model.{PreparedUploadResponse, Reference, UploadFormTemplate}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Action
import play.api.mvc.Results.Ok
import play.api.test.Helpers.contentAsString
import play.api.test.{FakeRequest, Helpers}
import services.PrepareUploadService
import services.model.UploadSettings
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class PrepareUploadControllerSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  implicit val actorSystem: ActorSystem = ActorSystem()

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  implicit val timeout: akka.util.Timeout = 10 seconds

  private val clock: Clock = Clock.systemDefaultZone()

  "PaymentController prepareUploadV1" should {

    behave like prapareUploadTests(_.prepareUploadV1())
  }

  "PaymentController prepareUploadV2" should {

    val extraRequestFields = Json
      .obj("successRedirect" -> "https://www.example.com/nextpage", "errorRedirect" -> "https://www.example.com/error")

    val extraResponseFields = Json.obj(
      "success_action_redirect" -> "https://www.example.com/nextpage",
      "error_action_redirect"   -> "https://www.example.com/error")

    behave like prapareUploadTests(_.prepareUploadV2(), extraRequestFields, extraResponseFields)
  }

  private def prapareUploadTests( // scalastyle:ignore
    prepareUploadAction: PrepareUploadController => Action[JsValue],
    extraRequestFields: JsObject  = JsObject(Seq()),
    extraResponseFields: JsObject = JsObject(Seq())) {

    val config = mock[ServiceConfiguration]
    Mockito.when(config.allowedUserAgents).thenReturn(List("VALID-AGENT"))
    Mockito.when(config.allowedCallbackProtocols).thenReturn(List("https"))

    "build and return upload URL if valid request with all data" in {
      val controller = new PrepareUploadController(prepareUploadService, config, clock)

      Given("there is a valid upload request with all data")

      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(
          ("User-Agent", "VALID-AGENT"),
          ("x-session-id", "some-session-id"),
          ("x-request-id", "some-request-id"))
        .withBody(
          Json.obj(
            "id"              -> "1",
            "callbackUrl"     -> "https://www.example.com",
            "minimumFileSize" -> 0,
            "maximumFileSize" -> 1024) ++ extraRequestFields)

      When("upload initiation has been requested")

      val result = prepareUploadAction(controller)(request)

      Then("service returns valid response with reference and template of upload form")

      withClue(Helpers.contentAsString(result)) { status(result) shouldBe 200 }
      val json = Helpers.contentAsJson(result)
      json shouldBe Json.obj(
        "reference" -> "TEST",
        "uploadRequest" -> Json.obj(
          "href" -> "https://www.example.com",
          "fields" -> (Json.obj(
            "minFileSize" -> "0",
            "maxFileSize" -> "1024",
            "sessionId"   -> "some-session-id",
            "requestId"   -> "some-request-id") ++ extraResponseFields)
        )
      )
    }

    "build and return upload URL if valid request with redirect on success url" in {
      val controller = new PrepareUploadController(prepareUploadService, config, clock)

      Given("there is a valid upload request with all data")

      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(
          ("User-Agent", "VALID-AGENT"),
          ("x-session-id", "some-session-id"),
          ("x-request-id", "some-request-id"))
        .withBody(
          Json.obj(
            "id"              -> "1",
            "callbackUrl"     -> "https://www.example.com",
            "successRedirect" -> "https://www.example.com/nextpage",
            "minimumFileSize" -> 0,
            "maximumFileSize" -> 1024) ++ extraRequestFields)

      When("upload initiation has been requested")

      val result = prepareUploadAction(controller)(request)

      Then("service returns valid response with reference and template of upload form")

      withClue(Helpers.contentAsString(result)) { status(result) shouldBe 200 }
      val json = Helpers.contentAsJson(result)
      json shouldBe Json.obj(
        "reference" -> "TEST",
        "uploadRequest" -> Json.obj(
          "href" -> "https://www.example.com",
          "fields" -> (Json.obj(
            "minFileSize"             -> "0",
            "maxFileSize"             -> "1024",
            "sessionId"               -> "some-session-id",
            "requestId"               -> "some-request-id",
            "success_action_redirect" -> "https://www.example.com/nextpage"
          ) ++ extraResponseFields)
        )
      )
    }

    "build and return upload URL if valid request with minimal data including session id and request id" in {
      val controller = new PrepareUploadController(prepareUploadService, config, clock)

      Given("there is a valid upload request with minimal data")

      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(
          ("User-Agent", "VALID-AGENT"),
          ("x-session-id", "some-session-id"),
          ("x-request-id", "some-request-id"))
        .withBody(Json.obj("callbackUrl" -> "https://www.example.com") ++ extraRequestFields)

      When("upload initiation has been requested")

      val result = prepareUploadAction(controller)(request)

      Then("service returns valid response with reference and template of upload form")

      withClue(Helpers.contentAsString(result)) { status(result) shouldBe 200 }
      val json = Helpers.contentAsJson(result)
      json shouldBe Json.obj(
        "reference" -> "TEST",
        "uploadRequest" -> Json.obj(
          "href" -> "https://www.example.com",
          "fields" -> (Json
            .obj("sessionId" -> "some-session-id", "requestId" -> "some-request-id") ++ extraResponseFields)
        )
      )
    }

    "build and return upload URL if valid request with minimal data excluding session id and request id" in {
      val controller = new PrepareUploadController(prepareUploadService, config, clock)

      Given("there is a valid upload request with minimal data")

      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(("User-Agent", "VALID-AGENT"))
        .withBody(Json.obj("callbackUrl" -> "https://www.example.com") ++ extraRequestFields)

      When("upload initiation has been requested")

      val result = prepareUploadAction(controller)(request)

      Then("service returns valid response with reference and template of upload form")

      withClue(Helpers.contentAsString(result)) { status(result) shouldBe 200 }
      val json = Helpers.contentAsJson(result)
      json shouldBe Json.obj(
        "reference" -> "TEST",
        "uploadRequest" -> Json.obj(
          "href"   -> "https://www.example.com",
          "fields" -> (Json.obj("sessionId" -> "n/a", "requestId" -> "n/a") ++ extraResponseFields)
        )
      )
    }

    "return a bad request error if invalid request - wrong structure" in {
      val controller = new PrepareUploadController(prepareUploadService, config, clock)

      Given("there is an invalid upload request")

      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(
          ("User-Agent", "VALID-AGENT"),
          ("x-session-id", "some-session-id"),
          ("x-request-id", "some-request-id"))
        .withBody(Json.obj("invalid" -> "body"))

      When("upload initiation has been requested")

      val result = prepareUploadAction(controller)(request)

      Then("service returns error response")

      withClue(Helpers.contentAsString(result)) { status(result) shouldBe 400 }
    }

    "return a bad request error if invalid request - incorrect maximum file size " in {
      val controller = new PrepareUploadController(prepareUploadService, config, clock)

      Given("there is an invalid upload request")

      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(("User-Agent", "VALID-AGENT"), ("x-session-id", "some-session-id"))
        .withBody(Json.obj("callbackUrl" -> "https://www.example.com", "maximumFileSize" -> 2048))

      When("upload initiation has been requested")

      val result = prepareUploadAction(controller)(request)

      Then("service returns error response")

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe 400
      }
    }

    "return okay if service is allowed on whitelist " in {
      val controller = new PrepareUploadController(prepareUploadService, config, clock)

      Given("there is a valid upload request from a whitelisted service")

      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(
          ("User-Agent", "VALID-AGENT"),
          ("x-session-id", "some-session-id"),
          ("x-request-id", "some-request-id"))
        .withBody(Json.obj(
          "id"              -> "1",
          "callbackUrl"     -> "https://www.example.com",
          "minimumFileSize" -> 0,
          "maximumFileSize" -> 1024,
          "session-id"      -> "some-session-id",
          "request-id"      -> "some-request-id"
        ) ++ extraRequestFields)

      When("upload initiation has been requested")

      val result = prepareUploadAction(controller)(request)

      Then("service returns error response")

      withClue(Helpers.contentAsString(result)) { status(result) shouldBe 200 }
      val json = Helpers.contentAsJson(result)
      json shouldBe Json.obj(
        "reference" -> "TEST",
        "uploadRequest" -> Json.obj(
          "href" -> "https://www.example.com",
          "fields" -> (Json.obj(
            "minFileSize" -> "0",
            "maxFileSize" -> "1024",
            "sessionId"   -> "some-session-id",
            "requestId"   -> "some-request-id") ++ extraResponseFields)
        )
      )
    }

    "return a forbidden error if service is not whitelisted " in {
      val controller = new PrepareUploadController(prepareUploadService, config, clock)

      Given("there is a valid upload request from a non-whitelisted service")

      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(
          ("User-Agent", "INVALID-AGENT"),
          ("x-session-id", "some-session-id"),
          ("x-request-id", "some-request-id"))
        .withBody(Json.obj(
          "id"              -> "1",
          "callbackUrl"     -> "http://www.example.com",
          "minimumFileSize" -> 0,
          "maximumFileSize" -> 1024,
          "session-id"      -> "some-session-id",
          "request-id"      -> "some-request-id"
        ) ++ extraRequestFields)

      When("upload initiation has been requested")

      val result = prepareUploadAction(controller)(request)

      Then("service returns error response")

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe 403
      }
    }

    "allow https callback urls" in {
      val controller = new PrepareUploadController(prepareUploadService, config, clock)

      val result = controller.withAllowedCallbackProtocol("https://my.callback.url") {
        Future.successful(Ok)
      }

      status(result) shouldBe 200
    }

    "disallow http callback urls" in {
      val controller = new PrepareUploadController(prepareUploadService, config, clock)

      val result = controller.withAllowedCallbackProtocol("http://my.callback.url") {
        Future.failed(new RuntimeException("This block should not have been invoked."))
      }

      status(result)          shouldBe 400
      contentAsString(result) should include("Invalid callback url protocol")
    }

    "disallow invalidly formatted callback urls" in {
      val controller = new PrepareUploadController(prepareUploadService, config, clock)

      val result = controller.withAllowedCallbackProtocol("123") {
        Future.failed(new RuntimeException("This block should not have been invoked."))
      }
      status(result)          shouldBe 400
      contentAsString(result) should include("Invalid callback url format")
    }
  }

  private def prepareUploadService: PrepareUploadService = {
    val service = mock[PrepareUploadService]
    Mockito.when(service.globalFileSizeLimit).thenReturn(1024)
    Mockito
      .when(service.prepareUpload(any(), any(), any(), any(), any()))
      .thenAnswer(new Answer[PreparedUploadResponse]() {
        override def answer(invocationOnMock: InvocationOnMock): PreparedUploadResponse = {
          val settings  = invocationOnMock.getArgument[UploadSettings](0)
          val requestId = invocationOnMock.getArgument[String](2)
          val sessionId = invocationOnMock.getArgument[String](3)
          PreparedUploadResponse(
            Reference("TEST"),
            UploadFormTemplate(
              settings.callbackUrl,
              Map.empty ++
                settings.minimumFileSize.map(s => Map("minFileSize" -> s.toString).head) ++
                settings.maximumFileSize.map(s => Map("maxFileSize" -> s.toString).head) ++
                Map("sessionId" -> sessionId) ++
                Map("requestId" -> requestId) ++
                settings.successRedirect.map(url => Map("success_action_redirect" -> url)).getOrElse(Map.empty) ++
                settings.errorRedirect.map(url => Map("error_action_redirect"     -> url)).getOrElse(Map.empty)
            )
          )
        }

      })
    service
  }

}
