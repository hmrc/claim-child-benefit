package models.relationship

import play.api.libs.json.{Json, OFormat}

final case class Relationship(
                               relationshipType: RelationshipType,
                               relationshipSource: RelationshipSource
                             )

object Relationship {

  implicit lazy val format: OFormat[Relationship] = Json.format
}
