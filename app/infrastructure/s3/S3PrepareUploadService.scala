package infrastructure.s3

import java.time.Instant
import java.util.Date
import javax.inject.Inject

import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import config.ServiceConfiguration
import domain.{Link, PrepareUploadService, UploadSettings}

class S3PrepareUploadService @Inject() (client : AmazonS3, configuration: ServiceConfiguration) extends PrepareUploadService {

  override def setupUpload(settings: UploadSettings) = {

    val expiration = Instant.now().plus(configuration.fileExpirationPeriod)

    val generatePresignedUrlRequest = new GeneratePresignedUrlRequest(configuration.transientBucketName, settings.id)
    generatePresignedUrlRequest.setMethod(HttpMethod.PUT)
    generatePresignedUrlRequest.setExpiration(Date.from(expiration))

    val url = client.generatePresignedUrl(generatePresignedUrlRequest)

    Link(url.toString, "PUT")
  }

}
