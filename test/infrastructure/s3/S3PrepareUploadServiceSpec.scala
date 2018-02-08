package infrastructure.s3

import java.time
import java.util.Date

import config.ServiceConfiguration
import domain.UploadSettings
import infrastructure.s3.awsclient.S3PostSigner
import org.scalatest.{GivenWhenThen, Matchers}
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.JavaConverters._

class S3PrepareUploadServiceSpec extends UnitSpec with Matchers with GivenWhenThen {

  val serviceConfiguration = new ServiceConfiguration {

    override def accessKeyId: String = ???

    override def secretAccessKey: String = ???

    override def transientBucketName: String = "test-bucket"

    override def sessionToken: Option[String] = ???

    override def region: String = ???

    override def fileExpirationPeriod: time.Duration = time.Duration.ofDays(7)

    override def useInstanceProfileCredentials = ???
  }

  val s3PostSigner = new S3PostSigner {
    override def presignForm(
      userSpecifiedExpirationDate: Date,
      bucketName: String,
      key: String,
      acl: String,
      additionalMetadata: java.util.Map[String, String]) =
      Map("bucket" -> bucketName, "key" -> key).asJava

    override def buildEndpoint(bucketName: String): String = s"$bucketName.s3"
  }

  "S3 Upload Service" should {

    val service = new S3PrepareUploadService(s3PostSigner, serviceConfiguration)

    "create post form that allows to upload the file" in {

      Given("there are have valid upload settings")

      val uploadSettings = UploadSettings("http://www.callback.com")

      When("we setup the uploat")

      val result = service.setupUpload(uploadSettings)

      Then("proper upload request form definition should be returned")

      result.uploadRequest.href shouldBe s"${serviceConfiguration.transientBucketName}.s3"
      result.uploadRequest.fields shouldBe Map(
        "bucket" -> serviceConfiguration.transientBucketName,
        "key"    -> result.reference.value
      )

    }
  }

}
