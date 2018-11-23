package connectors.s3

import config.ServiceConfiguration
import java.time.{Clock, Instant}

import domain.UploadFormGenerator
import javax.inject.{Inject, Provider, Singleton}

@Singleton
class S3UploadFormGeneratorProvider @Inject()(configuration: ServiceConfiguration, clock: Clock)
    extends Provider[UploadFormGenerator] {

  import configuration._

  private val tick: () => Instant = () => Instant.now(clock)

  override def get() =
    new S3UploadFormGenerator(
      AwsCredentials(accessKeyId, secretAccessKey, sessionToken),
      regionName  = region,
      currentTime = tick
    )

}
