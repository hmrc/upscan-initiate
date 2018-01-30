package infrastructure.stub

import domain._

class StubPrepareUploadService extends PrepareUploadService {

  override def setupUpload(settings: UploadSettings) =
    PreparedUpload(
      Reference("TEST"),
      uploadLink = Link(s"http://localhost:8080/link", "PUT"),
      downloadLink = Link(s"http://localhost:8080/link", "GET")
    )
}
