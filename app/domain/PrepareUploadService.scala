package domain

trait PrepareUploadService {

  def setupUpload(settings: UploadSettings): PreparedUpload

}
