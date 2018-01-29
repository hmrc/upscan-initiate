import com.amazonaws.services.s3.AmazonS3
import config.{PlayBasedServiceConfiguration, ServiceConfiguration}
import domain.PrepareUploadService
import infrastructure.s3.{S3ClientProvider, S3PrepareUploadService}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}

class S3UploadModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[ServiceConfiguration].to[PlayBasedServiceConfiguration],
      bind[PrepareUploadService].to[S3PrepareUploadService],
      bind[AmazonS3].toProvider[S3ClientProvider]
    )
}
