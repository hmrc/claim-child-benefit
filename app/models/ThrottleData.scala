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

final case class ThrottleData(count: Int, limit: Int)

object ThrottleData {

  val id = "ThrottleData"

  private val reads: Reads[ThrottleData] = (
    (__ \ "count").read[Int] and
    (__ \ "limit").read[Int]
  )(ThrottleData.apply _)

  private val writes: OWrites[ThrottleData] = OWrites {
    throttleData =>
      Json.obj(
        "_id" -> id,
        "count" -> throttleData.count,
        "limit" -> throttleData.limit
      )
  }

  implicit lazy val format: OFormat[ThrottleData] = OFormat(reads, writes)
}
