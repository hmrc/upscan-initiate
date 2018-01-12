package domain

import scala.concurrent.Future

trait PrepareUploadService {

  def setupUpload(settings : UploadSettings) : Future[Link]

}
