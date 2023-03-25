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

package models.dmsa

import play.api.libs.json.{Format, Json, OFormat, OWrites}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

final case class SubmissionItem(
                                 id: String,
                                 status: SubmissionItemStatus,
                                 objectSummary: ObjectSummary,
                                 failureReason: Option[String],
                                 metadata: Metadata,
                                 sdesCorrelationId: String,
                                 created: Instant,
                                 lastUpdated: Instant,
                                 lockedAt: Option[Instant] = None
                               )

object SubmissionItem {

  implicit def format(implicit crypto: Encrypter with Decrypter): OFormat[SubmissionItem] = {
    implicit lazy val instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
    Json.format
  }

  val apiWrites: OWrites[SubmissionItem] = {
    implicit lazy val metadataWrites: OWrites[Metadata] = Metadata.apiWrites
    Json.writes
  }
}
