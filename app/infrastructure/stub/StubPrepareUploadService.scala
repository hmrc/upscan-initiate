package infrastructure.stub

import domain.{Link, PrepareUploadService, UploadSettings}

import scala.concurrent.Future

class StubPrepareUploadService extends PrepareUploadService {

  override def setupUpload(settings: UploadSettings) = Future.successful(Link(s"http://localhost:8080/${settings.id}", "PUT"))
}
