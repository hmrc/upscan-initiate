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

    PreparedUpload(reference = reference, uploadRequest = generatePost(reference.value, expiration))
  }

  private def generateReference(): Reference =
    Reference(UUID.randomUUID().toString)

  private def generatePost(key: String, expiration: Instant): PostRequest = {
    val uploadParameters = UploadParameters(
      expirationDateTime = expiration,
      bucketName         = configuration.transientBucketName,
      objectKey          = key,
      acl                = "private",
      additionalMetadata = Map.empty
    )
    val form = postSigner.generateFormFields(uploadParameters)
    PostRequest(postSigner.buildEndpoint(configuration.transientBucketName), form)
  }

}
