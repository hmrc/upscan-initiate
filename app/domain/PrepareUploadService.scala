package domain

case class UploadSettingsErrors(errors: Seq[String])

trait PrepareUploadService {

  def globalFileSizeLimit: Int

  def setupUpload(settings: UploadSettings): PreparedUpload

}
