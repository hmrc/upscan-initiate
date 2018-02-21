package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import domain._
import org.scalatest.{GivenWhenThen, Matchers}
import play.api.libs.json.{JsValue, Json}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._

class PrepareUploadControllerSpec extends UnitSpec with Matchers with GivenWhenThen {

  implicit val actorSystem = ActorSystem()

  implicit val materializer = ActorMaterializer()

  implicit val timeout: akka.util.Timeout = 10 seconds

  "PrepareUploadController" should {

    val controller = new PrepareUploadController(new StubPrepareUploadService())

    "build and return upload URL if valid request with all data" in {

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

      Given("there is an invalid upload request")

      val request: FakeRequest[JsValue] = FakeRequest().withBody(Json.obj("invalid" -> "body"))

      When("upload initiation has been requested")

      val result = controller.prepareUpload()(request)

      Then("service returns error response")

      withClue(Helpers.contentAsString(result)) { status(result) shouldBe 400 }

    }

    "return a bad request error if invalid request - incorrect maximum file size " in {

      Given("there is an invalid upload request")

      val request: FakeRequest[JsValue] =
        FakeRequest().withBody(Json.obj("callbackUrl" -> "http://www.example.com", "maximumFileSize" -> 2048))

      When("upload initiation has been requested")

      val result = controller.prepareUpload()(request)

      Then("service returns error response")

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe 400
        println(Helpers.contentAsString(result))
      }

    }

  }

}

class StubPrepareUploadService extends PrepareUploadService {

  override def setupUpload(settings: UploadSettings) =
    PreparedUpload(
      Reference("TEST"),
      UploadFormTemplate(
        settings.callbackUrl,
        Map.empty ++
          settings.minimumFileSize.map(s => Map("minFileSize" -> s.toString).head) ++
          settings.maximumFileSize.map(s => Map("maxFileSize" -> s.toString).head)
      )
    )

  override def globalFileSizeLimit = 1024
}
