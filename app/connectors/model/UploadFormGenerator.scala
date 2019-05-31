package connectors.model

trait UploadFormGenerator {
  def buildEndpoint(bucketName: String): String

  def generateFormFields(uploadParameters: UploadParameters): Map[String, String]
}
