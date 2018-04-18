package domain

import java.time.Instant
import java.util.UUID

import com.kenshoo.play.metrics.Metrics
import config.ServiceConfiguration
import javax.inject.{Inject, Singleton}
import play.api.Logger
@Singleton
class PrepareUploadService @Inject()(
  postSigner: UploadFormGenerator,
  configuration: ServiceConfiguration,
  metrics: Metrics) {

  def prepareUpload(settings: UploadSettings): PreparedUpload = {
    val reference  = generateReference()
    val expiration = Instant.now().plus(configuration.fileExpirationPeriod)

    val result =
      PreparedUpload(reference = reference, uploadRequest = generatePost(reference.value, expiration, settings))

    metrics.defaultRegistry.counter("uploadInitiated").inc()

    result
  }

  private def generateReference() = Reference(UUID.randomUUID().toString)

  private def generatePost(key: String, expiration: Instant, settings: UploadSettings): UploadFormTemplate = {

    val minFileSize = settings.minimumFileSize.getOrElse(0)
    val maxFileSize = settings.maximumFileSize.getOrElse(globalFileSizeLimit)

    require(minFileSize >= 0, "Minimum file size is less than 0")
    require(maxFileSize <= globalFileSizeLimit, "Maximum file size is greater than global maximum file size")
    require(minFileSize <= maxFileSize, "Minimum file size is greater than maximum file size")

    val uploadParameters = UploadParameters(
      expirationDateTime  = expiration,
      bucketName          = configuration.inboundBucketName,
      objectKey           = key,
      acl                 = "private",
      additionalMetadata  = Map("callback-url" -> settings.callbackUrl),
      contentLengthRange  = ContentLengthRange(minFileSize, maxFileSize),
      expectedContentType = settings.expectedContentType
    )

    val form     = postSigner.generateFormFields(uploadParameters)
    val endpoint = postSigner.buildEndpoint(configuration.inboundBucketName)

    UploadFormTemplate(endpoint, form)
  }

  def globalFileSizeLimit: Int = configuration.globalFileSizeLimit
}
