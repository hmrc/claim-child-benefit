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

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.{Instant, LocalDate}

final case class IndividualTraceCacheItem(forename: String, surname: String, dateOfBirth: LocalDate, exists: Boolean, timestamp: Instant)

object IndividualTraceCacheItem {

  lazy val reads: Reads[IndividualTraceCacheItem] =
    (
      (__ \ "forename").read[String] and
      (__ \ "surname").read[String] and
      (__ \ "dateOfBirth").read(MongoJavatimeFormats.localDateFormat) and
      (__ \ "exists").read[Boolean] and
      (__ \ "timestamp").read(MongoJavatimeFormats.instantFormat)
    )(IndividualTraceCacheItem.apply _)

  lazy val writes: OWrites[IndividualTraceCacheItem] =
    (
      (__ \ "forename").write[String] and
      (__ \ "surname").write[String] and
      (__ \ "dateOfBirth").write(MongoJavatimeFormats.localDateFormat) and
      (__ \ "exists").write[Boolean] and
      (__ \ "timestamp").write(MongoJavatimeFormats.instantFormat)
    )(unlift(IndividualTraceCacheItem.unapply))

  implicit lazy val format: OFormat[IndividualTraceCacheItem] = OFormat(reads, writes)
}
