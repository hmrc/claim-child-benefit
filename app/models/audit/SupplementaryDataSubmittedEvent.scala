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

package models.audit

import play.api.libs.json.Reads
import play.api.libs.json._
import play.api.libs.functional.syntax._

final case class SupplementaryDataSubmittedEvent(
                                                  nino: String,
                                                  correlationId: String,
                                                  fileName: String,
                                                  hash: String
                                                )

object SupplementaryDataSubmittedEvent {

  implicit lazy val reads: Reads[SupplementaryDataSubmittedEvent] =
    (
      (__ \ "nino").read[String] ~
      (__ \ "correlationId").read[String] ~
      (__ \ "fileName").read[String] ~
      (__ \ "hash").read[String]
    )(SupplementaryDataSubmittedEvent.apply _)

  implicit lazy val writes: OWrites[SupplementaryDataSubmittedEvent] =
    (
      (__ \ "nino").write[String] ~
      (__ \ "correlationId").write[String] ~
      (__ \ "fileName").write[String] ~
      (__ \ "hash").write[String] ~
      (__ \ "mimeType").write[String]
    ) { event =>
      (event.nino, event.correlationId, event.fileName, event.hash, "application/pdf")
    }
}