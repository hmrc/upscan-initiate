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
        uploadParameters.additionalMetadata.map { case (k, v) => s"x-amz-meta-$k" -> v }

    override def buildEndpoint(bucketName: String): String = s"$bucketName.s3"
  }

  "S3 Upload Service" should {

    val service = new S3PrepareUploadService(s3PostSigner, serviceConfiguration)

    "create post form that allows to upload the file" in {

      Given("there are have valid upload settings")

      val callbackUrl = "http://www.callback.com"

      val uploadSettings = UploadSettings(callbackUrl = callbackUrl, minimumFileSize = None, maximumFileSize = None)

      When("we setup the upload")

      val result = service.setupUpload(uploadSettings)

      Then("proper upload request form definition should be returned")

      result.uploadRequest.href shouldBe s"${serviceConfiguration.transientBucketName}.s3"
      result.uploadRequest.fields shouldBe Map(
        "bucket"                  -> serviceConfiguration.transientBucketName,
        "key"                     -> result.reference.value,
        "x-amz-meta-callback-url" -> callbackUrl,
        "minSize"                 -> "0",
        "maxSize"                 -> "1024"
      )

    }

    "take in account file size limits if provided in request" in {

      Given("there are have valid upload settings with size limits")

      val callbackUrl = "http://www.callback.com"

      val uploadSettings =
        UploadSettings(callbackUrl = callbackUrl, minimumFileSize = Some(100), maximumFileSize = Some(200))

      When("we setup the upload")

      val result = service.setupUpload(uploadSettings)

      Then("upload request should contain requested min/max size")

      result.uploadRequest.fields("minSize") shouldBe "100"
      result.uploadRequest.fields("maxSize") shouldBe "200"
    }

    "honour global file limit when taking in account size limits from request" in {

      Given("there are have valid upload settings with size limits")

      val callbackUrl = "http://www.callback.com"

      val uploadSettings =
        UploadSettings(callbackUrl = callbackUrl, minimumFileSize = Some(-1), maximumFileSize = Some(2048))

      When("we setup the upload")

      val result = service.setupUpload(uploadSettings)

      Then("upload request should contain requested min/max size")

      result.uploadRequest.fields("minSize") shouldBe "0"
      result.uploadRequest.fields("maxSize") shouldBe "1024"

    }

  }

}
