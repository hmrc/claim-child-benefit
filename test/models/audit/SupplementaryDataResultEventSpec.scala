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

import models.sdes.NotificationType
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

class SupplementaryDataResultEventSpec extends AnyFreeSpec with Matchers with OptionValues {

  "SupplementaryDataResultEvent" - {

    val json = Json.obj(
      "nino" -> "foobar",
      "correlationId" -> "barfoo",
      "fileName" -> "file.pdf",
      "hash" -> "somehash",
      "status" -> NotificationType.FileProcessingFailure.toString,
      "failureReason" -> "failed",
      "mimeType" -> "application/pdf"
    )

    val model = SupplementaryDataResultEvent(
      nino = "foobar",
      correlationId = "barfoo",
      fileName = "file.pdf",
      hash = "somehash",
      status = NotificationType.FileProcessingFailure,
      failureReason = Some("failed")
    )

    "must read from json" in {
      json.as[SupplementaryDataResultEvent] mustEqual model
    }

    "must read from json when there is no failure reason" in {

      val json = Json.obj(
        "nino" -> "foobar",
        "correlationId" -> "barfoo",
        "fileName" -> "file.pdf",
        "hash" -> "somehash",
        "status" -> NotificationType.FileProcessingFailure.toString,
        "mimeType" -> "application/pdf"
      )

      val newModel = model.copy(failureReason = None)

      json.as[SupplementaryDataResultEvent] mustEqual newModel
    }

    "must write to json" in {
      Json.toJson(model) mustEqual json
    }
  }
}
