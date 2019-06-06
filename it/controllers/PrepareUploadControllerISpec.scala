package controllers

import org.scalatest.GivenWhenThen
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.HeaderNames.USER_AGENT
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.http.HeaderNames.xSessionId
import uk.gov.hmrc.play.test.UnitSpec

class PrepareUploadControllerISpec extends UnitSpec with GuiceOneAppPerSuite with GivenWhenThen {
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "userAgentFilter.allowedUserAgents" -> "PrepareUploadControllerISpec",
      "uploadProxy.url"                   -> "https://upload-proxy.tax.service.gov.uk",
      "aws.s3.bucket.inbound"             -> "inbound-bucket"
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
      Given("a request containing a USER_AGENT header contained in the configuration of allowedUserAgents")
      val initiateRequest = FakeRequest(
        POST,
        uri,
        FakeHeaders(Seq((USER_AGENT, "PrepareUploadControllerISpec"), (xSessionId, "some-session-id"))),
        postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response should indicate success")
      status(initiateResponse) shouldBe 200

      And("the response should include the expected value for x-amz-meta-consuming-service")
      val responseJson = contentAsJson(initiateResponse)

      (responseJson \ "uploadRequest" \ "fields" \ "x-amz-meta-consuming-service")
        .as[String] shouldBe "PrepareUploadControllerISpec"

      And("the href should be the expected url for the upload")

      (responseJson \ "uploadRequest" \ "href")
        .as[String] shouldBe href

    }

    "include x-amz-meta-session-id in the response" in {
      Given("a request containing a x-session-id header")
      val initiateRequest = FakeRequest(
        POST,
        uri,
        FakeHeaders(Seq((USER_AGENT, "PrepareUploadControllerISpec"), (xSessionId, "some-session-id"))),
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
      val initiateRequest =
        FakeRequest(POST, uri, FakeHeaders(Seq((USER_AGENT, "PrepareUploadControllerISpec"))), postBodyJson)

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

    "reject requests which do not include a valid USER_AGENT header" in {
      Given("a request containing a USER_AGENT header not contained in the configuration of allowedUserAgents")
      val initiateRequest =
        FakeRequest(POST, uri, FakeHeaders(Seq((USER_AGENT, "SomeInvalidUserAgent"))), postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response should indicate the request is Forbidden")
      status(initiateResponse) shouldBe 403
    }

    "reject requests which do not include a USER_AGENT header" in {
      Given("a request not containing a USER_AGENT header")
      val initiateRequest =
        FakeRequest(POST, uri, FakeHeaders(Seq((xSessionId, "some-session-id"))), postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response should indicate the request is Forbidden")
      status(initiateResponse) shouldBe 403
    }
  }
}
