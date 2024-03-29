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

import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption

import java.time.Instant

final case class Metadata(
                           nino: SensitiveString,
                           submissionDate: Instant,
                           correlationId: String
                         )

object Metadata {

  def apply(
             nino: String,
             submissionDate: Instant,
             correlationId: String
           ): Metadata =
    new Metadata(
      nino = SensitiveString(nino),
      submissionDate = submissionDate,
      correlationId = correlationId
    )

  implicit def format(implicit crypto: Encrypter with Decrypter): OFormat[Metadata] = {

    implicit val sensitiveStringFormat: Format[SensitiveString] =
      JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)

    Json.format
  }

  val apiWrites: OWrites[Metadata] = (
    (__ \ "submissionDate").write[Instant] and
    ( __ \ "correlationId").write[String]
  )(m => (m.submissionDate, m.correlationId))
}
