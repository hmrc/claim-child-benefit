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

package controllers

import models.dmsa.Metadata
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import utils.NinoGenerator

class SupplementaryDataFormProviderSpec extends AnyFreeSpec with Matchers with OptionValues {

  private val form = new SupplementaryDataFormProvider().form

  "must return metadata when given valid input" in {

    val nino = NinoGenerator.randomNino()
    val metadata = Metadata(nino)
    val data = Map("metadata.nino" -> nino)

    form.bind(data).value.value mustEqual metadata
  }

  "metadata.nino" - {

    "must fail to bind if there is no nino" in {
      val error = form.bind(Map.empty[String, String])("metadata.nino").error.value
      error.message mustEqual "error.required"
    }

    "must fail to bind if the input is not a valid nino" in {
      val error = form.bind(Map("metadata.nino" -> "foobar"))("metadata.nino").error.value
      error.message mustEqual "error.metadata.nino.invalid"
    }
  }
}
