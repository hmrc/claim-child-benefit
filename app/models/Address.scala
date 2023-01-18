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

import play.api.libs.json.{Json, OFormat}

final case class Address(
                          line1: String,
                          line2: Option[String],
                          line3: Option[String],
                          line4: Option[String],
                          line5: Option[String],
                          postcode: Option[String],
                          country: Option[Country]
                        )

object Address {

  implicit lazy val format: OFormat[Address] = Json.format

  def apply(input: integration.Address): Address =
    new Address(
      line1 = input.addressLine1,
      line2 = input.addressLine2,
      line3 = input.addressLine3,
      line4 = input.addressLine4,
      line5 = input.addressLine5,
      postcode = input.addressPostcode,
      country = input.countryCode.flatMap(Country.countryByCode.get)
    )
}
