package connectors.model

trait UploadFormGenerator {
  def generateFormFields(uploadParameters: UploadParameters): Map[String, String]
}
