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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsSuccess, Json}

import java.time.LocalDate

class DailySummarySpec extends AnyFreeSpec with Matchers {

  "mongoFormats" - {

    "must serialise and deserialise to / from json" in {

      val now = LocalDate.of(2022, 1, 2)
      val summary = DailySummary(now, 1, 2, 4, 5)
      val expectedJson = Json.obj(
        "_id" -> "2022-01-02",
        "submitted" -> 1,
        "forwarded" -> 2,
        "failed" -> 4,
        "completed" -> 5
      )

      Json.toJson(summary)(DailySummary.mongoFormat) mustEqual expectedJson
      expectedJson.validate[DailySummary](DailySummary.mongoFormat) mustEqual JsSuccess(summary)
    }
  }

  "formats" - {

    "must serialise and deserialise to / from json" in {

      val now = LocalDate.of(2022, 1, 2)
      val summary = DailySummary(now, 1, 2, 4, 5)
      val expectedJson = Json.obj(
        "date" -> "2022-01-02",
        "submitted" -> 1,
        "forwarded" -> 2,
        "failed" -> 4,
        "completed" -> 5
      )

      Json.toJson(summary)(DailySummary.format) mustEqual expectedJson
      expectedJson.validate[DailySummary](DailySummary.format) mustEqual JsSuccess(summary)
    }
  }
}
