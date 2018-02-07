import config.{PlayBasedServiceConfiguration, ServiceConfiguration}
import domain.PrepareUploadService
import infrastructure.s3.awsclient.S3PostSigner
import infrastructure.s3.{S3PostSignerProvider, S3PrepareUploadService}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}

class S3UploadModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[ServiceConfiguration].to[PlayBasedServiceConfiguration],
      bind[PrepareUploadService].to[S3PrepareUploadService],
      bind[S3PostSigner].toProvider[S3PostSignerProvider]
    )
}
