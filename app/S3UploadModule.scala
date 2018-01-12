import domain.PrepareUploadService
import infrastructure.stub.StubPrepareUploadService
import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module}

class S3UploadModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(bind[PrepareUploadService].to[StubPrepareUploadService])
}
