/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models

import play.api.libs.json.{Json, OFormat, OWrites, Reads, __}
import play.api.libs.functional.syntax._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

final case class RecentClaim(nino: String, created: Instant)

object RecentClaim {

  lazy implicit val format: OFormat[RecentClaim] = Json.format

  val mongoReads: Reads[RecentClaim] =
    (
      (__ \ "nino").read[String] and
      (__ \ "created").read(MongoJavatimeFormats.instantFormat)
    )(RecentClaim.apply _)

  val mongoWrites: OWrites[RecentClaim] =
    (
      (__ \ "nino").write[String] and
      (__ \ "created").write(MongoJavatimeFormats.instantFormat)
    )(unlift(RecentClaim.unapply))

  val mongoFormat: OFormat[RecentClaim] = OFormat(mongoReads, mongoWrites)
}
