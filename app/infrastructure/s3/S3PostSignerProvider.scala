package infrastructure.s3

import javax.inject.{Inject, Provider}

import com.amazonaws.auth._
import config.ServiceConfiguration
import infrastructure.s3.awsclient.{JavaAWSClientBasedS3PostSigner, S3PostSigner}

class S3PostSignerProvider @Inject()(configuration: ServiceConfiguration) extends Provider[S3PostSigner] {

  override def get() = new JavaAWSClientBasedS3PostSigner(configuration.region, credentialsProvider)

  private lazy val credentialsProvider: AWSCredentialsProvider = {
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

