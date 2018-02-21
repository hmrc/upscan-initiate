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

  private def generatePost(key: String, expiration: Instant, settings: UploadSettings): UploadFormTemplate = {

    val minFileSize = settings.minimumFileSize.getOrElse(0)
    val maxFileSize = settings.maximumFileSize.getOrElse(globalFileSizeLimit)

    require(minFileSize >= 0, "Minimum file size is less than 0")
    require(maxFileSize <= globalFileSizeLimit, "Maximum file size is greater than global file size")
    require(minFileSize <= maxFileSize, "Maximum file size is greater than minimum file size")

    val uploadParameters = UploadParameters(
      expirationDateTime = expiration,
      bucketName         = configuration.transientBucketName,
      objectKey          = key,
      acl                = "private",
      additionalMetadata = Map("callback-url" -> settings.callbackUrl),
      contentLengthRange = ContentLengthRange(minFileSize, maxFileSize)
    )

    val form     = postSigner.generateFormFields(uploadParameters)
    val endpoint = postSigner.buildEndpoint(configuration.transientBucketName)

    UploadFormTemplate(endpoint, form)
  }

  override def globalFileSizeLimit: Int = configuration.globalFileSizeLimit
}
