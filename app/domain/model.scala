package domain

case class UploadSettings(callbackUrl: String)

case class Link(href: String, method: String)

case class PostRequest(href: String, fields: Map[String, String])

case class Reference(value: String)

case class PreparedUpload(reference: Reference, uploadRequest: PostRequest)
