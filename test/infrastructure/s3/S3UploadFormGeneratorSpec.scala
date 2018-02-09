package infrastructure.s3

import java.time.Instant
import java.util.Base64
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers, WordSpec}
import play.api.libs.json.{JsValue, Json}

class S3UploadFormGeneratorSpec extends WordSpec with GivenWhenThen with Matchers with MockitoSugar {

  "S3UploadFormGenerator" should {
    "generate required fields for a presigned POST request" in {

      // based on: https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-HTTPPOSTForms.html#sigv4-HTTPPOSTFormFields

      Given("there is a properly configured form generator with AWS credentials")
      val credentials   = AwsCredentials("accessKeyId", "secretKey", Some("session-token"))
      val regionName    = "us-east-1"
      val currentTime   = () => Instant.parse("1997-07-16T19:20:30Z")
      val policySigner  = mock[PolicySigner]
      val testSignature = "test-signature"

      when(policySigner.signPolicy(any(), any(), any(), any())).thenReturn(testSignature)

      val generator = new S3UploadFormGenerator(credentials, regionName, currentTime, policySigner)

      And("there are valid upload parameters")
      val expirationTimestamp = "1997-07-16T19:20:40Z"
      val uploadParameters =
        UploadParameters(
          expirationDateTime = Instant.parse(expirationTimestamp),
          bucketName         = "test-bucket",
          objectKey          = "test-key",
          acl                = "private",
          additionalMetadata = Map("key1" -> "value1")
        )

      When("form fields are generated")
      val result = generator.generateFormFields(uploadParameters)

      Then("valid POST policy is produced")
      val policy = decodePolicyFormResult(result("policy"))

      (policy \ "expiration").as[String]                                      shouldBe "1997-07-16T19:20:40Z"
      ((policy \ "conditions").get \\ "acl").head.as[String]                  shouldBe "private"
      ((policy \ "conditions").get \\ "bucket").head.as[String]               shouldBe "test-bucket"
      ((policy \ "conditions").get \\ "key").head.as[String]                  shouldBe "test-key"
      ((policy \ "conditions").get \\ "x-amz-algorithm").head.as[String]      shouldBe "AWS4-HMAC-SHA256"
      ((policy \ "conditions").get \\ "x-amz-date").head.as[String]           shouldBe "19970716T192030Z"
      ((policy \ "conditions").get \\ "x-amz-security-token").head.as[String] shouldBe "session-token"
      ((policy \ "conditions").get \\ "x-amz-meta-key1").head.as[String]      shouldBe "value1"

      And("policy's signature is correct")
      verify(policySigner).signPolicy(credentials, "19970716", regionName, result("policy"))
      result("x-amz-signature") shouldBe testSignature

      And("all required fields are present and match the policy")
      result("acl")                  shouldBe "private"
      result("key")                  shouldBe "test-key"
      result("x-amz-algorithm")      shouldBe "AWS4-HMAC-SHA256"
      result("x-amz-credential")     shouldBe "accessKeyId/19970716/us-east-1/s3/aws4_request"
      result("x-amz-date")           shouldBe "19970716T192030Z"
      result("x-amz-meta-key1")      shouldBe "value1"
      result("x-amz-security-token") shouldBe "session-token"

    }
  }

  def decodePolicyFormResult(base64EncodedPolicy: String): JsValue = {
    val decoded = new String(Base64.getDecoder.decode(base64EncodedPolicy), "UTF-8")
    Json.parse(decoded)
  }

}
