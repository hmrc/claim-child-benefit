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

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import java.time.LocalDate

class NameSpec extends AnyFreeSpec with Matchers with OptionValues {

  "apply" - {

    val input = models.integration.Name(
      nameSequenceNumber = 1,
      nameType = 1,
      titleType = 1,
      firstForename = Some("first"),
      secondForename = Some("middle"),
      surname = Some("surname"),
      nameEndDate = Some(LocalDate.now)
    )

    val output = models.Name(
      title = Some("Mr"),
      firstName = Some("first"),
      middleName = Some("middle"),
      lastName = Some("surname")
    )

    "must convert a name model from the integration framework to the output model" in {
      models.Name(input) mustEqual output
    }

    "must have no title if titleType is 0" in {
      models.Name(input.copy(titleType = 0)).title mustBe None
    }

    "must have `Mr` title if the titleType is 1" in {
      models.Name(input.copy(titleType = 1)).title.value mustEqual "Mr"
    }

    "must have `Mrs` title if the titleType is 2" in {
      models.Name(input.copy(titleType = 2)).title.value mustEqual "Mrs"
    }

    "must have `Miss` title if the titleType is 3" in {
      models.Name(input.copy(titleType = 3)).title.value mustEqual "Miss"
    }

    "must have `Ms` title if the titleType is 4" in {
      models.Name(input.copy(titleType = 4)).title.value mustEqual "Ms"
    }

    "must have `Dr` title if the titleType is 5" in {
      models.Name(input.copy(titleType = 5)).title.value mustEqual "Dr"
    }

    "must have `Rev` title if the titleType is 6" in {
      models.Name(input.copy(titleType = 6)).title.value mustEqual "Rev"
    }

    "must be empty if titleType is anything else" in {
      models.Name(input.copy(titleType = 7)).title mustBe None
    }
  }
}
