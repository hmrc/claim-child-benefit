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

package models.integration

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

import java.time.LocalDate

class DesignatoryDetailsSpec extends AnyFreeSpec with Matchers {

  private val name = Name(
    nameSequenceNumber = 0,
    nameType = 1,
    titleType = 0,
    firstForename = Some("first"),
    secondForename = Some("middle"),
    surname = Some("surname"),
    nameEndDate = Some(LocalDate.now)
  )

  private val address = Address(
    addressSequenceNumber = 0,
    countryCode = Some(1),
    addressType = 1,
    addressLine1 = "line1",
    addressLine2 = None,
    addressLine3 = None,
    addressLine4 = None,
    addressLine5 = None,
    addressPostcode = Some("postcode"),
    addressEndDate = Some(LocalDate.now)
  )

  private val model = DesignatoryDetails(
    dateOfBirth = LocalDate.of(2020, 2, 1),
    names = List(name),
    addresses = List(address)
  )

  "IF" - {

    val json = Json.obj(
      "details" -> Json.obj(
        "dateOfBirth" -> "2020-02-01"
      ),
      "nameList" -> Json.obj(
        "name" -> Json.arr(name)
      ),
      "addressList" -> Json.obj(
        "address" -> Json.arr(address)
      )
    )

    "must read from json" in {
      json.as[DesignatoryDetails] mustEqual model
    }

    "must write to json" in {
      Json.toJson(model) mustEqual json
    }
  }

  "DES" - {

    val json = Json.obj(
      "dateOfBirth" -> "2020-02-01",
      "nameList" -> Json.obj(
        "name" -> Json.arr(name)
      ),
      "addressList" -> Json.obj(
        "address" -> Json.arr(address)
      )
    )

    "must read from json" in {
      json.as[DesignatoryDetails](DesignatoryDetails.desFormats) mustEqual model
    }

    "must write to json" in {
      Json.toJson(model)(DesignatoryDetails.desFormats) mustEqual json
    }
  }
}
