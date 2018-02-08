package infrastructure.s3

import config.ServiceConfiguration
import infrastructure.s3.awsclient.{AwsCredentials, S3PostSigner, S3PostSignerImpl}
import java.time.Instant
import javax.inject.{Inject, Provider, Singleton}

@Singleton
class S3PostSignerProvider @Inject()(configuration: ServiceConfiguration) extends Provider[S3PostSigner] {

  import configuration._

  override def get() =
    new S3PostSignerImpl(
      AwsCredentials(accessKeyId, secretAccessKey, sessionToken),
      regionName  = region,
      currentTime = Instant.now
    )

}
