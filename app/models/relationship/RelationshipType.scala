package models.relationship

import models.{Enumerable, WithName}

sealed trait RelationshipType

object RelationshipType extends Enumerable.Implicits {

  case object AdultAdult     extends WithName("ADULT-ADULT") with RelationshipType
  case object AdultDerived   extends WithName("ADULT-DERIVED") with RelationshipType
  case object AdultChild     extends WithName("ADULT-CHILD") with RelationshipType
  case object AdultAppointee extends WithName("ADULT-APPOINTEE") with RelationshipType

  val values: Seq[RelationshipType] =
    Seq(AdultAdult, AdultDerived, AdultChild, AdultAppointee)

  implicit val enumerable: Enumerable[RelationshipType] =
    Enumerable(values.map(v => v.toString -> v): _*)
}
