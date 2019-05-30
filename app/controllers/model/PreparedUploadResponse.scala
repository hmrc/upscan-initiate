package controllers.model

import play.api.libs.functional.syntax._
import play.api.libs.json.{OWrites, __}

case class PreparedUploadResponse(reference: Reference, uploadRequest: UploadFormTemplate)

object PreparedUploadResponse {

  val writes: OWrites[PreparedUploadResponse] =
    ((__ \ "reference").write(Reference.writes)
      ~ (__ \ "uploadRequest").write(UploadFormTemplate.writes))(unlift(PreparedUploadResponse.unapply))

}
