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

import play.api.libs.functional.syntax._
import play.api.libs.json._

import java.time.LocalDate

final case class DesignatoryDetails(
                                     dateOfBirth: LocalDate,
                                     names: Seq[Name],
                                     addresses: Seq[Address]
                                   )

object DesignatoryDetails {

  implicit lazy val reads: Reads[DesignatoryDetails] = {
    (
      (__ \ "details" \ "dateOfBirth").read[LocalDate] and
      (__ \ "nameList" \ "name").read[Seq[Name]] and
      (__ \ "addressList" \ "address").read[Seq[Address]]
    )(DesignatoryDetails.apply _)
  }

  implicit lazy val writes: OWrites[DesignatoryDetails] =
    (
      (__ \ "details" \ "dateOfBirth").write[LocalDate] and
      (__ \ "nameList" \ "name").write[Seq[Name]] and
      (__ \ "addressList" \ "address").write[Seq[Address]]
    )(unlift(DesignatoryDetails.unapply))
}

final case class Name(
                       nameSequenceNumber: Int,
                       nameType: Int,
                       titleType: Int,
                       firstForename: String,
                       secondForename: Option[String],
                       surname: String,
                       nameEndDate: Option[LocalDate]
                     )

object Name {
  implicit lazy val format: OFormat[Name] = Json.format
}

final case class Address(
                          addressSequenceNumber: Int,
                          countryCode: Option[Int],
                          addressType: Int,
                          addressLine1: String,
                          addressLine2: Option[String],
                          addressLine3: Option[String],
                          addressLine4: Option[String],
                          addressLine5: Option[String],
                          addressPostcode: Option[String],
                          addressEndDate: Option[LocalDate]
                        )

object Address {
  implicit lazy val format: OFormat[Address] = Json.format
}