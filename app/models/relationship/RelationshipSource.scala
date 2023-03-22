package models.relationship

import models.{Enumerable, WithName}

sealed trait RelationshipSource

object RelationshipSource extends Enumerable.Implicits {

  case object TFC extends WithName("TFC") with RelationshipSource
  case object EFE extends WithName("EFE") with RelationshipSource
  case object CHB extends WithName("CHB") with RelationshipSource

  val values: Seq[RelationshipSource] = Seq(TFC, EFE, CHB)

  implicit val enumerable: Enumerable[RelationshipSource] =
    Enumerable(values.map(v => v.toString -> v): _*)
}
