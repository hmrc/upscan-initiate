package infrastructure.s3

import javax.inject.{Inject, Provider}

import com.amazonaws.auth._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import config.ServiceConfiguration

class S3ClientProvider @Inject() (configuration : ServiceConfiguration) extends Provider[AmazonS3] {


  override def get() = {

    AmazonS3ClientBuilder
      .standard
      .withRegion(configuration.region)
      .withCredentials(buildCredentialsProvider)
      .build()
  }

  private def buildCredentialsProvider = {
    if (configuration.useInstanceProfileCredentials) {
      new EC2ContainerCredentialsProviderWrapper()
    } else {
      new AWSStaticCredentialsProvider(
        configuration.sessionToken match {
          case Some(sessionToken) => new BasicSessionCredentials(configuration.accessKeyId, configuration.secretAccessKey, sessionToken)
          case None => new BasicAWSCredentials(configuration.accessKeyId, configuration.secretAccessKey)
        })
    }
  }
}
