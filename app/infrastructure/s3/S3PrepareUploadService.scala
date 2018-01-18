package infrastructure.s3

import java.time.{Instant, Period}
import java.util.Date

import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import domain.{Link, PrepareUploadService, UploadSettings}

import scala.concurrent.Future


class S3PrepareUploadService(client : AmazonS3) extends PrepareUploadService {

  val bucketName = "test-bucket"

  val expirationPeriod = Period.ofDays(7)

  override def setupUpload(settings: UploadSettings) = {

    val expiration = Instant.now().plus(expirationPeriod)

    val generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, settings.id)
    generatePresignedUrlRequest.setMethod(HttpMethod.PUT)
    generatePresignedUrlRequest.setExpiration(Date.from(expiration))

    val url = client.generatePresignedUrl(generatePresignedUrlRequest)

    Future.successful(Link(url.toString, "PUT"))
  }
}
