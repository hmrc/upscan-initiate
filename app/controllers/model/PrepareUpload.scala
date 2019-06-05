package controllers.model
import services.model.UploadSettings

trait PrepareUpload {
  def callbackUrl: String
  def toUploadSettings(uploadUrl: String): UploadSettings
}
