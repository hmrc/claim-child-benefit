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

import models.TaxChargeChoice._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json._

class TaxChargeChoiceSpec extends AnyFreeSpec with Matchers {

  "reads" - {

    "must read `optedOut` as Opted Out" in {

      JsString("optedOut").validate[TaxChargeChoice] mustEqual JsSuccess(OptedOut)
    }

    "must read `doesNotApply` as Does Not Apply" in {

      JsString("doesNotApply").validate[TaxChargeChoice] mustEqual JsSuccess(DoesNotApply)
    }

    "must read `notRecorded` as Not Recorded" in {

      JsString("notRecorded").validate[TaxChargeChoice] mustEqual JsSuccess(NotRecorded)
    }

    "must read a tax charge payer as Opted In" in {

      val json = Json.obj(
        "taxChargePayer" -> "applicant"
      )

      json.validate[TaxChargeChoice] mustEqual JsSuccess(OptedIn(TaxChargePayer.Applicant))
    }
  }

  ".writes" - {

    "must write Opted Out" in {

      Json.toJson[TaxChargeChoice](OptedOut) mustEqual JsString("optedOut")
    }

    "must write Does Not Apply" in {

      Json.toJson[TaxChargeChoice](DoesNotApply) mustEqual JsString("doesNotApply")
    }

    "must write Not Recorded" in {

      Json.toJson[TaxChargeChoice](NotRecorded) mustEqual JsString("notRecorded")
    }

    "must write Opted In" in {

      Json.toJson[TaxChargeChoice](OptedIn(TaxChargePayer.Partner)) mustEqual Json.obj("taxChargePayer" -> "partner")
    }
  }
}
