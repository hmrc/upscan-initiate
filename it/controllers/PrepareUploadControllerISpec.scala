package controllers

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.HeaderNames.USER_AGENT
import play.api.http.Status.BAD_REQUEST
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.http.HeaderNames.xSessionId

class PrepareUploadControllerISpec extends AnyWordSpecLike with should.Matchers with GuiceOneAppPerSuite with GivenWhenThen {

  import PrepareUploadControllerISpec._

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "uploadProxy.url"     -> "https://upload-proxy.tax.service.gov.uk",
      "aws.s3.bucket.inbound"      -> "inbound-bucket"
    )
    .build()

  "PrepareUploadController prepareUploadV1" should {
    val postBodyJson = Json.parse("""
        |{
        |	"callbackUrl": "https://some-url/callback",
        |	"minimumFileSize" : 0,
        |	"maximumFileSize" : 1024,
        |	"expectedMimeType": "application/xml"
        |}
      """.stripMargin)

    behave like prepareUploadTests(
      postBodyJson = postBodyJson,
      uri          = "/upscan/initiate",
      href         = "https://inbound-bucket.s3.amazonaws.com"
    )
  }

  "PrepareUploadController prepareUploadV2" should {
    val postBodyJson = Json.parse("""
        |{
        |	"callbackUrl": "https://some-url/callback",
        |	"successRedirect": "https://some-url/success",
        |	"errorRedirect": "https://some-url/error",
        |	"minimumFileSize" : 0,
        |	"maximumFileSize" : 1024,
        |	"expectedMimeType": "application/xml"
        |}
      """.stripMargin)

    behave like prepareUploadTests(
      postBodyJson = postBodyJson,
      uri          = "/upscan/v2/initiate",
      href         = "https://upload-proxy.tax.service.gov.uk/v1/uploads/inbound-bucket"
    )
  }

  private def prepareUploadTests( // scalastyle:ignore
    postBodyJson: JsValue,
    uri: String,
    href: String): Unit = {

    "include x-amz-meta-consuming-service in the response" in {
      Given("a request containing a User-Agent header")
      val initiateRequest = FakeRequest(
        POST,
        uri,
        FakeHeaders(Seq((USER_AGENT, SomeConsumingService), (xSessionId, "some-session-id"))),
        postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response should indicate success")
      status(initiateResponse) shouldBe 200

      And("the response should include the expected value for x-amz-meta-consuming-service")
      val responseJson = contentAsJson(initiateResponse)
      (responseJson \ "uploadRequest" \ "fields" \ "x-amz-meta-consuming-service")
        .as[String] shouldBe SomeConsumingService

      And("the href should be the expected url for the upload")
      (responseJson \ "uploadRequest" \ "href")
        .as[String] shouldBe href
    }

    "include x-amz-meta-session-id in the response" in {
      Given("a valid request containing a x-session-id header")
      val initiateRequest = FakeRequest(
        POST,
        uri,
        FakeHeaders(Seq((USER_AGENT, SomeConsumingService), (xSessionId, "some-session-id"))),
        postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response should indicate success")
      status(initiateResponse) shouldBe 200

      And("the response should include the expected value for x-amz-meta-consuming-service")
      val responseJson = contentAsJson(initiateResponse)
      (responseJson \ "uploadRequest" \ "fields" \ "x-amz-meta-session-id")
        .as[String] shouldBe "some-session-id"

      And("the href should be the expected url for the upload")
      (responseJson \ "uploadRequest" \ "href")
        .as[String] shouldBe href
    }

    "set a default x-amz-meta-session-id in the response if no session id passed in" in {
      Given("a request containing a x-session-id header")
      val initiateRequest = FakeRequest(POST, uri, FakeHeaders(Seq((USER_AGENT, SomeConsumingService))), postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response should indicate success")
      status(initiateResponse) shouldBe 200

      And("the response should include the expected value for x-amz-meta-consuming-service")
      val responseJson = contentAsJson(initiateResponse)
      (responseJson \ "uploadRequest" \ "fields" \ "x-amz-meta-session-id")
        .as[String] shouldBe "n/a"

      And("the href should be the expected url for the upload")
      (responseJson \ "uploadRequest" \ "href")
        .as[String] shouldBe href
    }

    "reject requests which do not include a User-Agent header" in {
      Given("a request not containing a User-Agent header")
      val initiateRequest = FakeRequest(POST, uri, FakeHeaders(Seq((xSessionId, "some-session-id"))), postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response should indicate the request is invalid")
      status(initiateResponse) shouldBe BAD_REQUEST
    }
  }
}

private object PrepareUploadControllerISpec {
  val SomeConsumingService = "PrepareUploadControllerISpec"
}