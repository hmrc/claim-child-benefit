/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.json.{JsObject, OFormat, OWrites, Reads, __}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

final case class UserData(
                           id: String,
                           data: JsObject,
                           lastUpdated: Instant
                         )

object UserData {

  val reads: Reads[UserData] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "_id").read[String] and
      (__ \ "data").read[JsObject] and
      (__ \ "lastUpdated").read(MongoJavatimeFormats.instantFormat)
    ) (UserData.apply _)
  }

  val writes: OWrites[UserData] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "_id").write[String] and
      (__ \ "data").write[JsObject] and
      (__ \ "lastUpdated").write(MongoJavatimeFormats.instantFormat)
    ) (unlift(UserData.unapply))
  }

  implicit val format: OFormat[UserData] = OFormat(reads, writes)
}
