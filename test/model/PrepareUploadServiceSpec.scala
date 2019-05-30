package model

import java.time
import java.time.Instant

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import config.ServiceConfiguration
import connectors.model.{UploadFormGenerator, UploadParameters}
import org.scalatest.{GivenWhenThen, Matchers}
import services.PrepareUploadService
import services.model.UploadSettings
import uk.gov.hmrc.play.test.UnitSpec

class PrepareUploadServiceSpec extends UnitSpec with Matchers with GivenWhenThen {

  val serviceConfiguration = new ServiceConfiguration {

    override def accessKeyId: String = ???

    override def secretAccessKey: String = ???

    override def inboundBucketName: String = "test-bucket"

    override def sessionToken: Option[String] = ???

    override def region: String = ???

    override def fileExpirationPeriod: time.Duration = time.Duration.ofDays(7)

    override def globalFileSizeLimit = 1024

    override def allowedCallbackProtocols: List[String] = List("https")

    override def allowedUserAgents: List[String] = ???
  }

  def metricsStub() = new Metrics {

    override val defaultRegistry: MetricRegistry = new MetricRegistry

    override def toJson: String = ???
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
        } ++
        uploadParameters.successRedirect.map { "success_redirect_url" -> _ }

    override def buildEndpoint(bucketName: String): String = s"$bucketName.s3"
  }

  val receivedAt: Instant = Instant.now()

  "S3 Prepare Upload Service" should {

    def service(metrics: Metrics) = new PrepareUploadService(s3PostSigner, serviceConfiguration, metrics)

    "create post form that allows to upload the file" in {

      val metrics = metricsStub()

      Given("there are valid upload settings")

      val callbackUrl = "http://www.callback.com"

      val uploadSettings = UploadSettings(
        callbackUrl         = callbackUrl,
        minimumFileSize     = None,
        maximumFileSize     = None,
        expectedContentType = Some("application/xml"),
        successRedirect     = None,
        errorRedirect       = None)

      When("we setup the upload")

      val result = service(metrics)
        .prepareUpload(uploadSettings, "PrepareUploadServiceSpec", "some-request-id", "some-session-id", receivedAt)

      Then("proper upload request form definition should be returned")

      result.uploadRequest.href shouldBe s"${serviceConfiguration.inboundBucketName}.s3"
      result.uploadRequest.fields shouldBe Map(
        "bucket"                              -> serviceConfiguration.inboundBucketName,
        "key"                                 -> result.reference.value,
        "x-amz-meta-callback-url"             -> callbackUrl,
        "x-amz-meta-consuming-service"        -> "PrepareUploadServiceSpec",
        "x-amz-meta-session-id"               -> "some-session-id",
        "x-amz-meta-request-id"               -> "some-request-id",
        "minSize"                             -> "0",
        "maxSize"                             -> "1024",
        "Content-Type"                        -> "application/xml",
        "x-amz-meta-upscan-initiate-received" -> receivedAt.toString
      )

      And("uploadInitiated counter has been incremented")
      metrics.defaultRegistry.counter("uploadInitiated").getCount shouldBe 1

    }

    "take in account file size limits if provided in request" in {

      val metrics = metricsStub()

      Given("there are valid upload settings with size limits")

      val callbackUrl = "http://www.callback.com"

      val uploadSettings = UploadSettings(
        callbackUrl         = callbackUrl,
        minimumFileSize     = Some(100),
        maximumFileSize     = Some(200),
        expectedContentType = None,
        successRedirect     = None,
        errorRedirect       = None)

      When("we setup the upload")

      val result = service(metrics)
        .prepareUpload(uploadSettings, "PrepareUploadServiceSpec", "some-request-id", "some-session-id", receivedAt)

      Then("upload request should contain requested min/max size")

      result.uploadRequest.fields("minSize") shouldBe "100"
      result.uploadRequest.fields("maxSize") shouldBe "200"
    }

    "fail when minimum file size is less than 0" in {

      Given("there are upload settings with minimum file size less than zero")

      val metrics = metricsStub()

      val callbackUrl = "http://www.callback.com"

      val uploadSettings = UploadSettings(
        callbackUrl         = callbackUrl,
        minimumFileSize     = Some(-1),
        maximumFileSize     = Some(1024),
        expectedContentType = None,
        successRedirect     = None,
        errorRedirect       = None)

      When("we setup the upload")
      Then("an exception should be thrown")

      val thrown = the[IllegalArgumentException] thrownBy service(metrics)
        .prepareUpload(uploadSettings, "PrepareUploadServiceSpec", "some-request-id", "some-session-id", receivedAt)
      thrown.getMessage should include("Minimum file size is less than 0")

      metrics.defaultRegistry.counter("uploadInitiated").getCount shouldBe 0

    }

    "fail when maximum file size is greater than global limit" in {

      Given("there upload settings with maximum file size greater than global limit")

      val metrics = metricsStub()

      val callbackUrl = "http://www.callback.com"

      val uploadSettings = UploadSettings(
        callbackUrl         = callbackUrl,
        minimumFileSize     = Some(0),
        maximumFileSize     = Some(1025),
        expectedContentType = None,
        successRedirect     = None,
        errorRedirect       = None)

      When("we setup the upload")
      Then("an exception should be thrown")

      val thrown = the[IllegalArgumentException] thrownBy service(metrics)
        .prepareUpload(uploadSettings, "PrepareUploadServiceSpec", "some-request-id", "some-session-id", receivedAt)
      thrown.getMessage should include("Maximum file size is greater than global maximum file size")

      metrics.defaultRegistry.counter("uploadInitiated").getCount shouldBe 0
    }

    "fail when minimum file size is greater than maximum file size" in {

      Given("there are upload settings with minimum file size greater than maximum size")

      val metrics = metricsStub()

      val callbackUrl = "http://www.callback.com"

      val uploadSettings = UploadSettings(
        callbackUrl         = callbackUrl,
        minimumFileSize     = Some(1024),
        maximumFileSize     = Some(0),
        expectedContentType = None,
        successRedirect     = None,
        errorRedirect       = None)

      When("we setup the upload")
      Then("an exception should be thrown")

      val thrown = the[IllegalArgumentException] thrownBy service(metrics)
        .prepareUpload(uploadSettings, "PrepareUploadServiceSpec", "some-request-id", "some-session-id", receivedAt)
      thrown.getMessage should include("Minimum file size is greater than maximum file size")

      metrics.defaultRegistry.counter("uploadInitiated").getCount shouldBe 0
    }

    "create post form that allows to upload the file with redirect on success" in {

      val metrics = metricsStub()

      Given("there are valid upload settings")

      val callbackUrl = "http://www.callback.com"

      val uploadSettings = UploadSettings(
        callbackUrl         = callbackUrl,
        minimumFileSize     = None,
        maximumFileSize     = None,
        expectedContentType = Some("application/xml"),
        successRedirect     = Some("https://new.service/page1"),
        errorRedirect       = None
      )

      When("we setup the upload")

      val result = service(metrics)
        .prepareUpload(uploadSettings, "PrepareUploadServiceSpec", "some-request-id", "some-session-id", receivedAt)

      Then("proper upload request form definition should be returned")

      result.uploadRequest.href shouldBe s"${serviceConfiguration.inboundBucketName}.s3"
      result.uploadRequest.fields shouldBe Map(
        "bucket"                              -> serviceConfiguration.inboundBucketName,
        "key"                                 -> result.reference.value,
        "x-amz-meta-callback-url"             -> callbackUrl,
        "x-amz-meta-consuming-service"        -> "PrepareUploadServiceSpec",
        "x-amz-meta-session-id"               -> "some-session-id",
        "x-amz-meta-request-id"               -> "some-request-id",
        "minSize"                             -> "0",
        "maxSize"                             -> "1024",
        "Content-Type"                        -> "application/xml",
        "x-amz-meta-upscan-initiate-received" -> receivedAt.toString,
        "success_redirect_url"                -> "https://new.service/page1"
      )

      And("uploadInitiated counter has been incremented")
      metrics.defaultRegistry.counter("uploadInitiated").getCount shouldBe 1

    }

  }

}
