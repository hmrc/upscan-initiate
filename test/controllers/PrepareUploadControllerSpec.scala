package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import domain._
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

        //given

        val request: FakeRequest[JsValue] = FakeRequest().withHeaders("Content-type" -> "application/json")
          .withBody(Json.obj("id" -> "1", "callbackUrl" -> "http://www.example.com")
        )

        //when

        val result = controller.prepareUpload()(request)

        //then

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
        //given
        val request: FakeRequest[JsValue] = FakeRequest().withHeaders("Content-type" -> "application/json")
          .withBody(Json.obj("invalid" -> "body")
          )

        //when
        val result = controller.prepareUpload()(request)

        //then
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
