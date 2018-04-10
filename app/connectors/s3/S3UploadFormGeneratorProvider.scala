package connectors.s3

import config.ServiceConfiguration
import java.time.Instant

import domain.UploadFormGenerator
import javax.inject.{Inject, Provider, Singleton}

@Singleton
class S3UploadFormGeneratorProvider @Inject()(configuration: ServiceConfiguration)
    extends Provider[UploadFormGenerator] {

  import configuration._

  override def get() =
    new S3UploadFormGenerator(
      AwsCredentials(accessKeyId, secretAccessKey, sessionToken),
      regionName  = region,
      currentTime = Instant.now
    )

}
