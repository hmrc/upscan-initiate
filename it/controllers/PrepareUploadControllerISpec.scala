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

  "PrepareUploadController prepareUploadV1 with all request values" in {
    val postBodyJson = Json.parse("""|{
                                     |	"callbackUrl": "https://some-url/callback",
                                     |	"minimumFileSize" : 0,
                                     |	"maximumFileSize" : 1024,
                                     |	"expectedContentType": "application/xml",
                                     |  "successRedirect": "https://some-url/success"
                                     |}""".stripMargin)

    Given("a request containing a User-Agent header")
    val headers = FakeHeaders(Seq((USER_AGENT, SomeConsumingService)))
    val initiateRequest = FakeRequest(POST, uri = "/upscan/initiate", headers, postBodyJson)

    When("a request is posted to the /initiate endpoint")
    val initiateResponse = route(app, initiateRequest).get

    Then("the response should indicate success")
    status(initiateResponse) shouldBe OK

    And("the response should include an AWS S3 upload URL")
    val responseJson = contentAsJson(initiateResponse)
    (responseJson \ "uploadRequest" \ "href").as[String] shouldBe "https://inbound-bucket.s3.amazonaws.com"

    And("the response should contain the requested upload fields")
    val fields = (responseJson \ "uploadRequest" \ "fields").as[Map[String, String]]
    fields.get("x-amz-meta-callback-url") should contain ("https://some-url/callback")
    fields.get("Content-Type") should contain ("application/xml")
    fields.get("success_action_redirect") should contain ("https://some-url/success")
  }

  "PrepareUploadController prepareUploadV1 with only mandatory request values" in {
    val postBodyJson = Json.parse("""|{
                                     |	"callbackUrl": "https://some-url/callback"
                                     |}""".stripMargin)

    Given("a request containing a User-Agent header")
    val headers = FakeHeaders(Seq((USER_AGENT, SomeConsumingService)))
    val initiateRequest = FakeRequest(POST, uri = "/upscan/initiate", headers, postBodyJson)

    When("a request is posted to the /initiate endpoint")
    val initiateResponse = route(app, initiateRequest).get

    Then("the response should indicate success")
    status(initiateResponse) shouldBe OK

    And("the response should include an AWS S3 upload URL")
    val responseJson = contentAsJson(initiateResponse)
    (responseJson \ "uploadRequest" \ "href").as[String] shouldBe "https://inbound-bucket.s3.amazonaws.com"

    And("the response should contain the requested upload fields")
    val fields = (responseJson \ "uploadRequest" \ "fields").as[Map[String, String]]
    fields.get("x-amz-meta-callback-url") should contain ("https://some-url/callback")
    fields.get("Content-Type") shouldBe empty
    fields.get("success_action_redirect") shouldBe empty
  }

  "PrepareUploadController prepareUploadV2 with all request values" in {
    val postBodyJson = Json.parse("""
        |{
        |	"callbackUrl": "https://some-url/callback",
        |	"successRedirect": "https://some-url/success",
        |	"errorRedirect": "https://some-url/error",
        |	"minimumFileSize" : 0,
        |	"maximumFileSize" : 1024,
        |	"expectedContentType": "application/xml"
        |}
      """.stripMargin)

    Given("a request containing a User-Agent header")
    val headers = FakeHeaders(Seq((USER_AGENT, SomeConsumingService)))
    val initiateRequest = FakeRequest(POST, uri = "/upscan/v2/initiate", headers, postBodyJson)

    When("a request is posted to the /initiate endpoint")
    val initiateResponse = route(app, initiateRequest).get

    Then("the response should indicate success")
    status(initiateResponse) shouldBe OK

    And("the response should include an upscan-proxy upload URL")
    val responseJson = contentAsJson(initiateResponse)
    (responseJson \ "uploadRequest" \ "href").as[String] shouldBe "https://upload-proxy.tax.service.gov.uk/v1/uploads/inbound-bucket"

    And("the response should contain the requested upload fields")
    val fields = (responseJson \ "uploadRequest" \ "fields").as[Map[String, String]]
    fields.get("x-amz-meta-callback-url") should contain ("https://some-url/callback")
    fields.get("success_action_redirect") should contain ("https://some-url/success")
    fields.get("error_action_redirect") should contain ("https://some-url/error")
    fields.get("Content-Type") should contain ("application/xml")
  }

  "PrepareUploadController prepareUploadV2 with only mandatory request values" in {
    val postBodyJson = Json.parse("""|{
                                     |	"callbackUrl": "https://some-url/callback",
                                     |	"errorRedirect": "https://some-url/error"
                                     |}""".stripMargin)

    Given("a request containing a User-Agent header")
    val headers = FakeHeaders(Seq((USER_AGENT, SomeConsumingService)))
    val initiateRequest = FakeRequest(POST, uri = "/upscan/v2/initiate", headers, postBodyJson)

    When("a request is posted to the /initiate endpoint")
    val initiateResponse = route(app, initiateRequest).get

    Then("the response should indicate success")
    status(initiateResponse) shouldBe OK

    And("the response should include an upscan-proxy upload URL")
    val responseJson = contentAsJson(initiateResponse)
    (responseJson \ "uploadRequest" \ "href").as[String] shouldBe "https://upload-proxy.tax.service.gov.uk/v1/uploads/inbound-bucket"

    And("the response should contain the requested upload fields")
    val fields = (responseJson \ "uploadRequest" \ "fields").as[Map[String, String]]
    fields.get("x-amz-meta-callback-url") should contain ("https://some-url/callback")
    fields.get("error_action_redirect") should contain ("https://some-url/error")
    fields.get("success_action_redirect") shouldBe empty
    fields.get("Content-Type") shouldBe empty
  }

  "Upscan V1" should {
    val requestJson = Json.parse("""{"callbackUrl": "https://some-url/callback"}""")

    behave like upscanInitiate(uri = "/upscan/initiate", requestJson)
  }

  "Upscan V2" should {
    val requestJson = Json.parse("""|{
                                    |	"callbackUrl": "https://some-url/callback",
                                    |	"errorRedirect": "https://some-url/error"
                                    |}""".stripMargin)

    behave like upscanInitiate(uri = "/upscan/v2/initiate", requestJson)
  }

  //noinspection ScalaStyle
  private def upscanInitiate(uri: String, requestJson: JsValue): Unit = {
    "reject requests which do not include a User-Agent header" in {
      Given("a request not containing a User-Agent header")
      val initiateRequest = FakeRequest(POST, uri, FakeHeaders(Seq((xSessionId, "some-session-id"))), requestJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response should indicate the request is invalid")
      status(initiateResponse) shouldBe BAD_REQUEST
    }

    "include x-amz-meta-consuming-service to identify the client service" in {
      Given("a request containing a User-Agent header")
      val initiateRequest = FakeRequest(POST, uri, FakeHeaders(Seq((USER_AGENT, SomeConsumingService))), requestJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response should indicate success")
      status(initiateResponse) shouldBe OK

      And("the response should identify the client service")
      val responseJson = contentAsJson(initiateResponse)
      (responseJson \ "uploadRequest" \ "fields" \ "x-amz-meta-consuming-service").as[String] shouldBe SomeConsumingService
    }

    "include x-amz-meta-session-id in the response when a session exists" in {
      Given("a valid request containing a x-session-id header")
      val headers = FakeHeaders(Seq((USER_AGENT, SomeConsumingService), (xSessionId, "some-session-id")))
      val initiateRequest = FakeRequest(POST, uri, headers, requestJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response should indicate success")
      status(initiateResponse) shouldBe 200

      And("the response should include the expected value for x-amz-meta-session-id")
      val responseJson = contentAsJson(initiateResponse)
      (responseJson \ "uploadRequest" \ "fields" \ "x-amz-meta-session-id").as[String] shouldBe "some-session-id"
    }

    "set a default x-amz-meta-session-id in the response if no session exists" in {
      Given("a request containing a x-session-id header")
      val initiateRequest = FakeRequest(POST, uri, FakeHeaders(Seq((USER_AGENT, SomeConsumingService))), requestJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response should indicate success")
      status(initiateResponse) shouldBe 200

      And("the response should include the expected value for x-amz-meta-consuming-service")
      val responseJson = contentAsJson(initiateResponse)
      (responseJson \ "uploadRequest" \ "fields" \ "x-amz-meta-session-id").as[String] shouldBe "n/a"
    }

    "include standard upload fields" in {
      Given("a request containing a User-Agent header")
      val initiateRequest = FakeRequest(POST, uri, FakeHeaders(Seq((USER_AGENT, SomeConsumingService))), requestJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response should indicate success")
      status(initiateResponse) shouldBe OK

      And("the response should contain the standard upload fields")
      val responseJson = contentAsJson(initiateResponse)
      val reference = (responseJson \ "reference").as[String]
      val fields = (responseJson \ "uploadRequest" \ "fields").as[Map[String, String]]
      fields.get("key") should contain(reference)
      fields.get("acl") should contain("private")
      fields.get("x-amz-algorithm") should contain("AWS4-HMAC-SHA256")
      fields should contain key "x-amz-date"
      fields should contain key "x-amz-credential"
      fields should contain key "x-amz-signature"
      fields should contain key "policy"
    }
  }
}

private object PrepareUploadControllerISpec {
  val SomeConsumingService = "PrepareUploadControllerISpec"
}