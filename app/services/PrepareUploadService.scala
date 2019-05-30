package services

import java.time.Instant
import java.util.UUID

import com.kenshoo.play.metrics.Metrics
import config.ServiceConfiguration
import connectors.model.{ContentLengthRange, UploadFormGenerator, UploadParameters}
import controllers.model.{PreparedUploadResponse, Reference, UploadFormTemplate}
import javax.inject.{Inject, Singleton}
import org.slf4j.MDC
import play.api.Logger
import services.model.UploadSettings

@Singleton
class PrepareUploadService @Inject()(
  postSigner: UploadFormGenerator,
  configuration: ServiceConfiguration,
  metrics: Metrics) {

  def prepareUpload(
    settings: UploadSettings,
    consumingService: String,
    requestId: String,
    sessionId: String,
    receivedAt: Instant): PreparedUploadResponse = {
    val reference  = generateReference()
    val expiration = receivedAt.plus(configuration.fileExpirationPeriod)

    val result =
      PreparedUploadResponse(
        reference = reference,
        uploadRequest =
          generatePost(reference.value, expiration, settings, consumingService, requestId, sessionId, receivedAt))

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
    consumingService: String,
    requestId: String,
    sessionId: String,
    receivedAt: Instant): UploadFormTemplate = {

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
        "callback-url"             -> settings.callbackUrl,
        "consuming-service"        -> consumingService,
        "session-id"               -> sessionId,
        "request-id"               -> requestId,
        "upscan-initiate-received" -> receivedAt.toString
      ),
      contentLengthRange  = ContentLengthRange(minFileSize, maxFileSize),
      expectedContentType = settings.expectedContentType,
      successRedirect     = settings.successRedirect
    )

    val form     = postSigner.generateFormFields(uploadParameters)
    val endpoint = postSigner.buildEndpoint(configuration.inboundBucketName)

    UploadFormTemplate(endpoint, form)
  }

  def globalFileSizeLimit: Int = configuration.globalFileSizeLimit
}
