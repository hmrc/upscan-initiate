package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import infrastructure.stub.StubPrepareUploadService
import org.scalatest.Matchers
import play.api.libs.json.{JsValue, Json}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._

class PrepareUploadControllerSpec extends UnitSpec with Matchers {

  implicit val actorSystem = ActorSystem()

  implicit val materializer = ActorMaterializer()

  implicit val timeout : akka.util.Timeout = 10 seconds

    "PrepareUploadController" should {

      val controller = new PrepareUploadController(new StubPrepareUploadService())

      "build and return upload URL if valid request" in {

        val request: FakeRequest[JsValue] = FakeRequest().withHeaders("Content-type" -> "application/json")
          .withBody(Json.obj("id" -> "1", "callbackUrl" -> "http://www.example.com")
        )

        val result = controller.prepareUpload()(request)

        status(result) shouldBe 200
        val json = Helpers.contentAsJson(result)
        json shouldBe Json.obj("_links" -> Json.obj(
          "upload" -> Json.obj(
            "href" -> "http://localhost:8080/1",
            "method" -> "PUT"
          )
        ))
      }

      "return a bad request error if invalid request" in {

        val request: FakeRequest[JsValue] = FakeRequest().withHeaders("Content-type" -> "application/json")
          .withBody(Json.obj("invalid" -> "body")
          )

        val result = controller.prepareUpload()(request)
        status(result) shouldBe 400

      }

    }

}
