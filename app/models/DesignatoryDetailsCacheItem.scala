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

import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.auth.core.Nino
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

final case class DesignatoryDetailsCacheItem(nino: String, details: DesignatoryDetails, timestamp: Instant)

object DesignatoryDetailsCacheItem {

  val reads: Reads[DesignatoryDetailsCacheItem] =
    (
      (__ \ "_id").read[String] and
      (__ \ "details").read[DesignatoryDetails] and
      (__ \ "timestamp").read(MongoJavatimeFormats.instantFormat)
    )(DesignatoryDetailsCacheItem.apply _)

  val writes: OWrites[DesignatoryDetailsCacheItem] =
    (
      (__ \ "_id").write[String] and
      (__ \ "details").write[DesignatoryDetails] and
      (__ \ "timestamp").write(MongoJavatimeFormats.instantFormat)
    )(unlift(DesignatoryDetailsCacheItem.unapply))

  implicit val format: OFormat[DesignatoryDetailsCacheItem] = OFormat(reads, writes)
}
