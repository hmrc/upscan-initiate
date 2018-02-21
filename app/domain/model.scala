package domain

case class UploadSettings(callbackUrl: String, minimumFileSize: Option[Int], maximumFileSize: Option[Int])

case class UploadFormTemplate(href: String, fields: Map[String, String])

case class Reference(value: String)

case class PreparedUpload(reference: Reference, uploadRequest: UploadFormTemplate)
