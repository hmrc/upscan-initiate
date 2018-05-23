package domain

import java.time.Instant
import java.util.UUID

import com.kenshoo.play.metrics.Metrics
import config.ServiceConfiguration
import javax.inject.{Inject, Singleton}
import org.slf4j.MDC
import play.api.Logger
@Singleton
class PrepareUploadService @Inject()(
  postSigner: UploadFormGenerator,
  configuration: ServiceConfiguration,
  metrics: Metrics) {

  def prepareUpload(settings: UploadSettings, consumingService: String): PreparedUpload = {
    val reference  = generateReference()
    val expiration = Instant.now().plus(configuration.fileExpirationPeriod)

    val result =
      PreparedUpload(
        reference     = reference,
        uploadRequest = generatePost(reference.value, expiration, settings, consumingService))

    try {
      MDC.put("file-reference", reference.value)
      Logger.info(
        s"Generated file-reference: [${reference.value}], for settings: [$settings], with expiration at: [$expiration].")

      metrics.defaultRegistry.counter("uploadInitiated").inc()

      result
    } finally {
      MDC.remove("file-reference")
    }
  }

  private def generateReference() = Reference(UUID.randomUUID().toString)

  private def generatePost(
    key: String,
    expiration: Instant,
    settings: UploadSettings,
    consumingService: String): UploadFormTemplate = {

    val minFileSize = settings.minimumFileSize.getOrElse(0)
    val maxFileSize = settings.maximumFileSize.getOrElse(globalFileSizeLimit)

    require(minFileSize >= 0, "Minimum file size is less than 0")
    require(maxFileSize <= globalFileSizeLimit, "Maximum file size is greater than global maximum file size")
    require(minFileSize <= maxFileSize, "Minimum file size is greater than maximum file size")

    val uploadParameters = UploadParameters(
      expirationDateTime = expiration,
      bucketName         = configuration.inboundBucketName,
      objectKey          = key,
      acl                = "private",
      additionalMetadata = Map(
        "callback-url"      -> settings.callbackUrl,
        "consuming-service" -> consumingService
      ),
      contentLengthRange  = ContentLengthRange(minFileSize, maxFileSize),
      expectedContentType = settings.expectedContentType
    )

    val form     = postSigner.generateFormFields(uploadParameters)
    val endpoint = postSigner.buildEndpoint(configuration.inboundBucketName)

    UploadFormTemplate(endpoint, form)
  }

  def globalFileSizeLimit: Int = configuration.globalFileSizeLimit
}
