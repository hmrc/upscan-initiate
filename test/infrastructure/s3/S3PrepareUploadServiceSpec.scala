package infrastructure.s3

import java.time

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import config.ServiceConfiguration
import domain.UploadSettings
import org.scalatest.Matchers
import play.api.libs.ws.ahc.AhcWSClient
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class S3PrepareUploadServiceSpec extends UnitSpec with Matchers with WithS3Mock {

  implicit val actorSystem = ActorSystem()

  implicit val materializer = ActorMaterializer()

  val serviceConfiguration = new ServiceConfiguration {

    override def accessKeyId: String = ???

    override def secretAccessKey: String = ???

    override def transientBucketName: String = "test-bucket"

    override def sessionToken: Option[String] = ???

    override def region: String = ???

    override def fileExpirationPeriod: time.Duration = time.Duration.ofDays(7)
  }

  "S3 Upload Service" should {

    val service = new S3PrepareUploadService(s3client, serviceConfiguration)

    "create link that allows to upload the file" in {

      s3client.createBucket(serviceConfiguration.transientBucketName)

      val uploadSettings = UploadSettings("http://www.test.com")

      val result = service.setupUpload(uploadSettings)

      uploadTheFile(result.uploadLink.href, "TEST")

      val createdObject = downloadTheFile(result.downloadLink.href)
      createdObject shouldBe "TEST"

    }
  }

  def uploadTheFile(url : String, content : String): Unit = {
    val wsClient = AhcWSClient()
    val result = Await.result(wsClient.url(url).put(content), 10 seconds)
    val status = result.status
    assert(status >= 200 && status <= 299)
  }

  def downloadTheFile(url : String) : String = {
    val wsClient = AhcWSClient()
    val result = Await.result(wsClient.url(url).get(), 10 seconds)
    val status = result.status
    assert(status >= 200 && status <= 299)
    result.body
  }

}
