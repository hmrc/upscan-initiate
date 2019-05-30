package controllers.model
import play.api.libs.json.{JsString, JsValue, Writes}

case class Reference(value: String)

object Reference {

  val writes: Writes[Reference] = new Writes[Reference] {
    override def writes(o: Reference): JsValue = JsString(o.value)
  }
}
