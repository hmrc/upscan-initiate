import config.{PlayBasedServiceConfiguration, ServiceConfiguration}
import domain.PrepareUploadService
import infrastructure.s3.{S3PostSignerProvider, S3PrepareUploadService, UploadFormGenerator}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}

class S3UploadModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[ServiceConfiguration].to[PlayBasedServiceConfiguration],
      bind[PrepareUploadService].to[S3PrepareUploadService],
      bind[UploadFormGenerator].toProvider[S3PostSignerProvider]
    )
}
