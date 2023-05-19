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


sealed trait TaxChargeChoice

object TaxChargeChoice {

  case object NotRecorded extends TaxChargeChoice
  case object DoesNotApply extends TaxChargeChoice
  case object OptedOut extends TaxChargeChoice
  final case class OptedIn(taxChargePayer: TaxChargePayer) extends TaxChargeChoice

  object OptedIn {
    implicit lazy val format: OFormat[OptedIn] = Json.format
  }

  implicit val reads: Reads[TaxChargeChoice] = new Reads[TaxChargeChoice] {
    override def reads(json: JsValue): JsResult[TaxChargeChoice] =
      json match {
        case JsString("optedOut")     => JsSuccess(OptedOut)
        case JsString("doesNotApply") => JsSuccess(DoesNotApply)
        case JsString("notRecorded")  => JsSuccess(NotRecorded)
        case x: JsObject              => x.validate[OptedIn]
        case _                        => JsError("Unable to read json as a TaxChargeChoice")
      }
  }

  implicit val writes: Writes[TaxChargeChoice] = new Writes[TaxChargeChoice] {
    override def writes(o: TaxChargeChoice): JsValue =
      o match {
        case OptedOut     => JsString("optedOut")
        case DoesNotApply => JsString("doesNotApply")
        case x: OptedIn   => Json.toJson(x)
        case NotRecorded  => JsString("notRecorded")
      }
  }
}
