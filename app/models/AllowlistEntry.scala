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
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

final case class AllowlistEntry(nino: SensitiveString)

object AllowlistEntry {

  implicit def reads(implicit crypto: Encrypter with Decrypter): Reads[AllowlistEntry] = {

    implicit val sensitiveStringFormat: Format[SensitiveString] =
      JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)

    (__ \ "_id").read[SensitiveString].map(AllowlistEntry.apply)
  }

  implicit def writes(implicit crypto: Encrypter with Decrypter): OWrites[AllowlistEntry] = {

    implicit val sensitiveStringFormat: Format[SensitiveString] =
      JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)

    (__ \ "_id").write[SensitiveString].contramap(_.nino)
  }

  implicit def format(implicit crypto: Encrypter with Decrypter): OFormat[AllowlistEntry] =
    OFormat(reads, writes)
}
