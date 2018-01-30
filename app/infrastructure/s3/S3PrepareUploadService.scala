package infrastructure.s3

import java.time.Instant
import java.util.{Date, UUID}
import javax.inject.{Inject, Singleton}

import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import config.ServiceConfiguration
import domain._

@Singleton
class S3PrepareUploadService @Inject() (client : AmazonS3, configuration: ServiceConfiguration) extends PrepareUploadService {

  override def setupUpload(settings: UploadSettings) : PreparedUpload = {

    val reference = generateReference()

    val expiration = Instant.now().plus(configuration.fileExpirationPeriod)

    val uploadUrl = generatePresignedS3Url(reference, expiration, HttpMethod.PUT)
    val downloadUrl = generatePresignedS3Url(reference, expiration, HttpMethod.GET)

    PreparedUpload(reference = reference, uploadLink = uploadUrl, downloadLink = downloadUrl)
  }

  private def generateReference() : Reference = {
    Reference(UUID.randomUUID().toString)
  }

  private def generatePresignedS3Url(reference : Reference, expiration: Instant, httpMethod: HttpMethod) = {

    val presignedUploadUrlRequest = new GeneratePresignedUrlRequest(configuration.transientBucketName, reference.value)
    presignedUploadUrlRequest.setMethod(httpMethod)
    presignedUploadUrlRequest.setExpiration(Date.from(expiration))
    val url = client.generatePresignedUrl(presignedUploadUrlRequest)

    Link(url.toString, httpMethod.name())
  }
}
