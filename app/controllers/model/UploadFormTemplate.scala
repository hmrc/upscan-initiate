package controllers.model
import play.api.libs.functional.syntax._
import play.api.libs.json.{OWrites, __}

case class UploadFormTemplate(href: String, fields: Map[String, String])

object UploadFormTemplate {

  val writes: OWrites[UploadFormTemplate] =
    ((__ \ "href").write[String]
      ~ (__ \ "fields").write[Map[String, String]])(unlift(UploadFormTemplate.unapply))

}
