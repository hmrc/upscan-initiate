package connectors.s3

import domain.UploadFormGenerator
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}

class S3Module extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[UploadFormGenerator].toProvider[S3UploadFormGeneratorProvider]
    )
}
