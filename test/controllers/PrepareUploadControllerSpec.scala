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

  implicit val timeout : akka.util.Timeout = 10 seconds

    "PrepareUploadController" should {

      val controller = new PrepareUploadController(new StubPrepareUploadService())

      "build and return upload URL if valid request" in {

        Given("there is a valid upload request")

        val request: FakeRequest[JsValue] = FakeRequest()
          .withBody(Json.obj("id" -> "1", "callbackUrl" -> "http://www.example.com")
        )

        When("upload initiation has been requested")

        val result = controller.prepareUpload()(request)

        Then("service returns valid response with reference and template of upload form")

        status(result) shouldBe 200
        val json = Helpers.contentAsJson(result)
        json shouldBe Json.obj(
          "reference" -> "TEST",
          "uploadRequest" -> Json.obj(
            "href" -> "http://test.com",
            "fields" -> Json.obj(
              "field1" -> "value1",
              "field2" -> "value2"
            )
        ))
      }

      "return a bad request error if invalid request" in {

        Given("there is an invalid upload request")

        val request: FakeRequest[JsValue] = FakeRequest().withBody(Json.obj("invalid" -> "body"))

        When("upload initiation has been requested")

        val result = controller.prepareUpload()(request)

        Then("service returns error response")

        status(result) shouldBe 400

      }

    }

}

class StubPrepareUploadService extends PrepareUploadService {

  override def setupUpload(settings: UploadSettings) =
    PreparedUpload(
      Reference("TEST"),
      PostRequest("http://test.com", Map("field1" -> "value1", "field2" -> "value2"))
    )
}
