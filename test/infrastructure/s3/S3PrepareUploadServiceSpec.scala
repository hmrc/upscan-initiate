package infrastructure.s3

import java.nio.file.Files

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.auth.{AWSStaticCredentialsProvider, AnonymousAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import domain.UploadSettings
import io.findify.s3mock.S3Mock
import org.apache.commons.io.IOUtils
import org.scalatest.{BeforeAndAfter, Matchers}
import play.api.libs.ws.ahc.AhcWSClient
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class S3PrepareUploadServiceSpec extends UnitSpec with Matchers with BeforeAndAfter {

  val s3mock = S3Mock(port = 8001, dir = Files.createTempDirectory("s3").toFile.getAbsolutePath)

  lazy val client = AmazonS3ClientBuilder
    .standard
    .withPathStyleAccessEnabled(true)
    .withEndpointConfiguration(new EndpointConfiguration("http://localhost:8001", "us-west-2"))
    .withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
    .build

  before {
    s3mock.start
  }

  after {
    s3mock.stop
  }

  "S3 Upload Service" should {

    val service = new S3PrepareUploadService(client)

    "create link that allows to upload the file" in {

      client.createBucket(service.bucketName)

      val uploadSettings = UploadSettings("foo", "http://www.google.com")

      val link = Await.result(service.setupUpload(uploadSettings), 10 seconds)

      uploadTheFile(link.href, "TEST")

      val createdObject = client.getObject(service.bucketName, "foo")
      assert(createdObject != null)
      IOUtils.toString(createdObject.getObjectContent) shouldBe "TEST"

    }
  }

  def uploadTheFile(url : String, content : String): Unit = {

    implicit val actorSystem = ActorSystem()

    implicit val materializer = ActorMaterializer()

    val wsClient = AhcWSClient()
    val status = Await.result(wsClient.url(url).put(content), 10 seconds).status
    assert(status >= 200 && status <= 299)
  }

}
