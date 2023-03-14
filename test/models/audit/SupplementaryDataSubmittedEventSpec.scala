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

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

class SupplementaryDataSubmittedEventSpec extends AnyFreeSpec with Matchers with OptionValues {

  "SupplementaryDataSubmittedEvent" - {

    val json = Json.obj(
      "nino" -> "foobar",
      "correlationId" -> "barfoo",
      "fileName" -> "file.pdf",
      "hash" -> "somehash",
      "mimeType" -> "application/pdf"
    )

    val model = SupplementaryDataSubmittedEvent(
      nino = "foobar",
      correlationId = "barfoo",
      fileName = "file.pdf",
      hash = "somehash"
    )

    "must read from json" in {
      json.as[SupplementaryDataSubmittedEvent] mustEqual model
    }

    "must write to json" in {
      Json.toJson(model) mustEqual json
    }
  }
}
