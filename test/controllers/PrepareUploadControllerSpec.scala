package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import config.ServiceConfiguration
import domain._
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import play.api.libs.json.{JsValue, Json}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._

class PrepareUploadControllerSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  implicit val actorSystem = ActorSystem()

  implicit val materializer = ActorMaterializer()

  implicit val timeout: akka.util.Timeout = 10 seconds

  "PrepareUploadController" should {

    "build and return upload URL if valid request with all data" in {
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedUserAgents).thenReturn(Nil)
      val controller = new PrepareUploadController(prepareUploadService, config)

      Given("there is a valid upload request with all data")

      val request: FakeRequest[JsValue] = FakeRequest()
        .withBody(
          Json.obj(
            "id"              -> "1",
            "callbackUrl"     -> "http://www.example.com",
            "minimumFileSize" -> 0,
            "maximumFileSize" -> 1024))

      When("upload initiation has been requested")

      val result = controller.prepareUpload()(request)

      Then("service returns valid response with reference and template of upload form")

      withClue(Helpers.contentAsString(result)) { status(result) shouldBe 200 }
      val json = Helpers.contentAsJson(result)
      json shouldBe Json.obj(
        "reference" -> "TEST",
        "uploadRequest" -> Json.obj(
          "href" -> "http://www.example.com",
          "fields" -> Json.obj(
            "minFileSize" -> "0",
            "maxFileSize" -> "1024"
          )
        ))
    }

    "build and return upload URL if valid request with minimal data" in {
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedUserAgents).thenReturn(Nil)
      val controller = new PrepareUploadController(prepareUploadService, config)

      Given("there is a valid upload request with minimal data")

      val request: FakeRequest[JsValue] = FakeRequest()
        .withBody(Json.obj("callbackUrl" -> "http://www.example.com"))

      When("upload initiation has been requested")

      val result = controller.prepareUpload()(request)

      Then("service returns valid response with reference and template of upload form")

      withClue(Helpers.contentAsString(result)) { status(result) shouldBe 200 }
      val json = Helpers.contentAsJson(result)
      json shouldBe Json.obj(
        "reference" -> "TEST",
        "uploadRequest" -> Json.obj(
          "href"   -> "http://www.example.com",
          "fields" -> Json.obj()
        ))
    }

    "return a bad request error if invalid request - wrong structure" in {
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedUserAgents).thenReturn(Nil)
      val controller = new PrepareUploadController(prepareUploadService, config)

      Given("there is an invalid upload request")

      val request: FakeRequest[JsValue] = FakeRequest().withBody(Json.obj("invalid" -> "body"))

      When("upload initiation has been requested")

      val result = controller.prepareUpload()(request)

      Then("service returns error response")

      withClue(Helpers.contentAsString(result)) { status(result) shouldBe 400 }
    }

    "return a bad request error if invalid request - incorrect maximum file size " in {
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedUserAgents).thenReturn(Nil)
      val controller = new PrepareUploadController(prepareUploadService, config)

      Given("there is an invalid upload request")

      val request: FakeRequest[JsValue] =
        FakeRequest().withBody(Json.obj("callbackUrl" -> "http://www.example.com", "maximumFileSize" -> 2048))

      When("upload initiation has been requested")

      val result = controller.prepareUpload()(request)

      Then("service returns error response")

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe 400
      }
    }

    "return okay if service is allowed on whitelist " in {
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedUserAgents).thenReturn(List("VALID-AGENT"))
      val controller = new PrepareUploadController(prepareUploadService, config)

      Given("there is a valid upload request from a whitelisted service")

      Given("there is a valid upload request with all data")

      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(("User-Agent", "VALID-AGENT"))
        .withBody(
          Json.obj(
            "id"              -> "1",
            "callbackUrl"     -> "http://www.example.com",
            "minimumFileSize" -> 0,
            "maximumFileSize" -> 1024))

      When("upload initiation has been requested")

      val result = controller.prepareUpload()(request)

      Then("service returns error response")

      withClue(Helpers.contentAsString(result)) { status(result) shouldBe 200 }
      val json = Helpers.contentAsJson(result)
      json shouldBe Json.obj(
        "reference" -> "TEST",
        "uploadRequest" -> Json.obj(
          "href" -> "http://www.example.com",
          "fields" -> Json.obj(
            "minFileSize" -> "0",
            "maxFileSize" -> "1024"
          )
        ))
    }

    "return a forbidden error if service is not whitelisted " in {
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedUserAgents).thenReturn(List("VALID-AGENT"))
      val controller = new PrepareUploadController(prepareUploadService, config)

      Given("there is a valid upload request from a non-whitelisted service")

      Given("there is a valid upload request with all data")

      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(("User-Agent", "INVALID-AGENT"))
        .withBody(
          Json.obj(
            "id"              -> "1",
            "callbackUrl"     -> "http://www.example.com",
            "minimumFileSize" -> 0,
            "maximumFileSize" -> 1024))

      When("upload initiation has been requested")

      val result = controller.prepareUpload()(request)

      Then("service returns error response")

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe 403
      }
    }
  }

  def prepareUploadService = {
    val service = mock[PrepareUploadService]
    Mockito.when(service.globalFileSizeLimit).thenReturn(1024)
    Mockito
      .when(service.prepareUpload(org.mockito.ArgumentMatchers.any()))
      .thenAnswer(new Answer[PreparedUpload]() {
        override def answer(invocationOnMock: InvocationOnMock): PreparedUpload = {
          val settings = invocationOnMock.getArgument[UploadSettings](0)
          PreparedUpload(
            Reference("TEST"),
            UploadFormTemplate(
              settings.callbackUrl,
              Map.empty ++
                settings.minimumFileSize.map(s => Map("minFileSize" -> s.toString).head) ++
                settings.maximumFileSize.map(s => Map("maxFileSize" -> s.toString).head)
            )
          )
        }

      })
    service
  }

}
