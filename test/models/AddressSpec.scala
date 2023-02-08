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

class AddressSpec extends AnyFreeSpec with Matchers with OptionValues {

  "apply" - {

    val input = models.integration.Address(
      addressSequenceNumber = 1,
      countryCode = None,
      addressType = 1,
      addressLine1 = "line1",
      addressLine2 = Some("line2"),
      addressLine3 = Some("line3"),
      addressLine4 = Some("line4"),
      addressLine5 = Some("line5"),
      addressPostcode = Some("postcode"),
      addressEndDate = Some(LocalDate.now)
    )

    val expectedOutput = models.Address(
      line1 = "line1",
      line2 = Some("line2"),
      line3 = Some("line3"),
      line4 = Some("line4"),
      line5 = Some("line5"),
      postcode = Some("postcode"),
      country = None
    )

    "must return an address" in {
      Address(input) mustBe expectedOutput
    }

    "must return an address with no country if the country code is missing" in {
      Address(input.copy(countryCode = None)).country mustBe None
    }

    "must return an address with no country if the country code does not map to a known country" in {
      Address(input.copy(countryCode = Some(0))).country mustBe None
    }

    "must return an address with a country if the country code maps to a known country" in {
      Address(input.copy(countryCode = Some(1))).country mustBe Some(Country("GB", "United Kingdom"))
    }
  }
}
