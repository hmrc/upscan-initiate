package controllers.model

import java.util.UUID.randomUUID

import play.api.libs.functional.syntax._
import play.api.libs.json.Writes

case class Reference(value: String) extends AnyVal {
  override def toString: String = value.toString
}

object Reference {

  val writes: Writes[Reference] = Writes.of[String].contramap(_.value)

  def generate(): Reference = Reference(randomUUID().toString)

}
