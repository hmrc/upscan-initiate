package infrastructure.s3

import java.time.Instant
import java.util.{Date, UUID}
import javax.inject.{Inject, Singleton}

import config.ServiceConfiguration
import domain._
import infrastructure.s3.awsclient.S3PostSigner

import scala.collection.JavaConverters._

@Singleton
class S3PrepareUploadService @Inject() (postSigner: S3PostSigner,
                                        configuration: ServiceConfiguration) extends PrepareUploadService {

  override def setupUpload(settings: UploadSettings) : PreparedUpload = {

    val reference = generateReference()

    val expiration = Instant.now().plus(configuration.fileExpirationPeriod)

    PreparedUpload(reference = reference, uploadRequest = generatePost(reference.value, expiration))
  }

  private def generateReference() : Reference = {
    Reference(UUID.randomUUID().toString)
  }

  private def generatePost(key : String, expiration: Instant):PostRequest = {
    val form = postSigner.presignForm(Date.from(expiration), configuration.transientBucketName, key, "private",
      Map.empty[String, String].asJava)
    PostRequest(postSigner.buildEndpoint(configuration.transientBucketName), form.asScala.toMap)
  }

}
