package infrastructure.s3

import config.ServiceConfiguration
import domain.UploadSettings
import java.time
import org.scalatest.{GivenWhenThen, Matchers}
import uk.gov.hmrc.play.test.UnitSpec

class S3PrepareUploadServiceSpec extends UnitSpec with Matchers with GivenWhenThen {

  val serviceConfiguration = new ServiceConfiguration {

    override def accessKeyId: String = ???

    override def secretAccessKey: String = ???

    override def transientBucketName: String = "test-bucket"

    override def sessionToken: Option[String] = ???

    override def region: String = ???

    override def fileExpirationPeriod: time.Duration = time.Duration.ofDays(7)

    override def useInstanceProfileCredentials = ???

    override def globalFileSizeLimit = 1024
  }

  val s3PostSigner = new UploadFormGenerator {
    override def generateFormFields(uploadParameters: UploadParameters) =
      Map(
        "bucket"  -> uploadParameters.bucketName,
        "key"     -> uploadParameters.objectKey,
        "minSize" -> uploadParameters.contentLengthRange.min.toString,
        "maxSize" -> uploadParameters.contentLengthRange.max.toString
      ) ++
        uploadParameters.additionalMetadata.map { case (k, v) => s"x-amz-meta-$k" -> v } ++
        uploadParameters.expectedContentType.map { contentType =>
          "Content-Type" -> contentType
        }

    override def buildEndpoint(bucketName: String): String = s"$bucketName.s3"
  }

  "S3 Upload Service" should {

    val service = new S3PrepareUploadService(s3PostSigner, serviceConfiguration)

    "create post form that allows to upload the file" in {

      Given("there are have valid upload settings")

      val callbackUrl = "http://www.callback.com"

      val uploadSettings = UploadSettings(
        callbackUrl         = callbackUrl,
        minimumFileSize     = None,
        maximumFileSize     = None,
        expectedContentType = Some("application/xml"))

      When("we setup the upload")

      val result = service.setupUpload(uploadSettings)

      Then("proper upload request form definition should be returned")

      result.uploadRequest.href shouldBe s"${serviceConfiguration.transientBucketName}.s3"
      result.uploadRequest.fields shouldBe Map(
        "bucket"                  -> serviceConfiguration.transientBucketName,
        "key"                     -> result.reference.value,
        "x-amz-meta-callback-url" -> callbackUrl,
        "minSize"                 -> "0",
        "maxSize"                 -> "1024",
        "Content-Type"            -> "application/xml"
      )

    }

    "take in account file size limits if provided in request" in {

      Given("there are have valid upload settings with size limits")

      val callbackUrl = "http://www.callback.com"

      val uploadSettings =
        UploadSettings(
          callbackUrl         = callbackUrl,
          minimumFileSize     = Some(100),
          maximumFileSize     = Some(200),
          expectedContentType = None)

      When("we setup the upload")

      val result = service.setupUpload(uploadSettings)

      Then("upload request should contain requested min/max size")

      result.uploadRequest.fields("minSize") shouldBe "100"
      result.uploadRequest.fields("maxSize") shouldBe "200"
    }

    "fail when minimum file size is less than 0" in {

      Given("there are upload settings with minimum file size less than zero")

      val callbackUrl = "http://www.callback.com"

      val uploadSettings =
        UploadSettings(
          callbackUrl         = callbackUrl,
          minimumFileSize     = Some(-1),
          maximumFileSize     = Some(1024),
          expectedContentType = None)

      When("we setup the upload")
      Then("an exception should be thrown")

      val thrown = the[IllegalArgumentException] thrownBy service.setupUpload(uploadSettings)
      thrown.getMessage should include("Minimum file size is less than 0")

    }

    "fail when maximum file size is greater than global limit" in {

      Given("there are upload settings with maximum file size greater than global limit")

      val callbackUrl = "http://www.callback.com"

      val uploadSettings =
        UploadSettings(
          callbackUrl         = callbackUrl,
          minimumFileSize     = Some(0),
          maximumFileSize     = Some(1025),
          expectedContentType = None)

      When("we setup the upload")
      Then("an exception should be thrown")

      val thrown = the[IllegalArgumentException] thrownBy service.setupUpload(uploadSettings)
      thrown.getMessage should include("Maximum file size is greater than global maximum file size")
    }

    "fail when minimum file size is greater than maximum file size" in {

      Given("there are upload settings with minimum file size greater than maximum size ")

      val callbackUrl = "http://www.callback.com"

      val uploadSettings =
        UploadSettings(
          callbackUrl         = callbackUrl,
          minimumFileSize     = Some(1024),
          maximumFileSize     = Some(0),
          expectedContentType = None)

      When("we setup the upload")
      Then("an exception should be thrown")

      val thrown = the[IllegalArgumentException] thrownBy service.setupUpload(uploadSettings)
      thrown.getMessage should include("Minimum file size is greater than maximum file size")
    }

  }

}
