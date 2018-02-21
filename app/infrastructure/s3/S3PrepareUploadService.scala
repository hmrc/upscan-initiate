package infrastructure.s3

import config.ServiceConfiguration
import domain._
import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}

@Singleton
class S3PrepareUploadService @Inject()(postSigner: UploadFormGenerator, configuration: ServiceConfiguration)
    extends PrepareUploadService {

  override def setupUpload(settings: UploadSettings): PreparedUpload = {
    val reference  = generateReference()
    val expiration = Instant.now().plus(configuration.fileExpirationPeriod)

    PreparedUpload(reference = reference, uploadRequest = generatePost(reference.value, expiration, settings))
  }

  private def generateReference(): Reference =
    Reference(UUID.randomUUID().toString)

  private def generatePost(key: String, expiration: Instant, settings: UploadSettings): PostRequest = {

    val globalFileSizeLimit = configuration.globalFileSizeLimit

    val uploadParameters = UploadParameters(
      expirationDateTime = expiration,
      bucketName         = configuration.transientBucketName,
      objectKey          = key,
      acl                = "private",
      additionalMetadata = Map("callback-url" -> settings.callbackUrl),
      contentLengthRange = ContentLengthRange(
        min = settings.minimumFileSize match {
          case Some(value) if value > 0 => value
          case _                        => 0
        },
        max = settings.maximumFileSize match {
          case Some(value) if value <= globalFileSizeLimit => value
          case _                                           => globalFileSizeLimit
        }
      )
    )
    val form = postSigner.generateFormFields(uploadParameters)
    PostRequest(postSigner.buildEndpoint(configuration.transientBucketName), form)
  }

}
