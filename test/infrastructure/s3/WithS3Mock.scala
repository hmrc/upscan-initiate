package infrastructure.s3

import java.nio.file.Files

import com.amazonaws.auth.{AWSStaticCredentialsProvider, AnonymousAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import io.findify.s3mock.S3Mock
import org.scalatest.{BeforeAndAfter, Suite}

trait WithS3Mock extends BeforeAndAfter {
  this: Suite =>

  val s3mock = S3Mock(port = 8001, dir = Files.createTempDirectory("s3").toFile.getAbsolutePath)

  lazy val s3client = AmazonS3ClientBuilder
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

}
