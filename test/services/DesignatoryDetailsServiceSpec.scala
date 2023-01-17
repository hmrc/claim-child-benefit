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

package services

import connectors.IfConnector
import models.Country
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import uk.gov.hmrc.http.HeaderCarrier
import utils.NinoGenerator

import scala.concurrent.Future

class DesignatoryDetailsServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar with ScalaFutures {

  private val mockIfConnector = mock[IfConnector]

  private val app = GuiceApplicationBuilder()
    .overrides(
      bind[IfConnector].toInstance(mockIfConnector)
    )
    .build()

  private val service = app.injector.instanceOf[DesignatoryDetailsService]

  "getDesignatoryDetails" - {

    "must return designatory details for the given nino" in {

      implicit val hc: HeaderCarrier = HeaderCarrier()
      val nino = NinoGenerator.randomNino()

      val ifResponse = {

        val realName1 = models.integration.Name(
          nameSequenceNumber = 1,
          nameType = 1,
          titleType = 1,
          firstForename = "first",
          secondForename = Some("middle"),
          surname = "real1"
        )

        val realName2 = realName1.copy(
          nameSequenceNumber = 2,
          surname = "real2"
        )

        val knownAs1 = realName1.copy(
          nameSequenceNumber = 3,
          nameType = 2,
          surname = "knownAs1"
        )

        val knownAs2 = realName1.copy(
          nameSequenceNumber = 4,
          nameType = 2,
          surname = "knownAs2"
        )

        val residentialAddress1 = models.integration.Address(
          addressSequenceNumber = 1,
          countryCode = Some(1),
          addressType = 1,
          addressLine1 = "line1",
          addressLine2 = Some("line2"),
          addressLine3 = Some("line3"),
          addressLine4 = Some("line4"),
          addressLine5 = Some("line5"),
          addressPostcode = Some("residentialAddress1")
        )

        val correspondenceAddress1 = residentialAddress1.copy(
          addressSequenceNumber = 2,
          addressType = 2,
          addressPostcode = Some("correspondenceAddress1")
        )

        val residentialAddress2 = residentialAddress1.copy(
          addressSequenceNumber = 3,
          addressPostcode = Some("residentialAddress2")
        )

        val correspondenceAddress2 = residentialAddress1.copy(
          addressSequenceNumber = 4,
          addressType = 2,
          addressPostcode = Some("correspondenceAddress2")
        )

        models.integration.DesignatoryDetails(
          names = List(
            realName1,
            knownAs1,
            realName2,
            knownAs2
          ),
          addresses = List(
            residentialAddress1,
            residentialAddress2,
            correspondenceAddress1,
            correspondenceAddress2
          )
        )
      }

      val expectedResponse = {

        val name = models.Name(
          title = Some("Mr"),
          firstName = "first",
          middleName = Some("middle"),
          lastName = "real2"
        )

        val residentialAddress = models.Address(
          line1 = "line1",
          line2 = Some("line2"),
          line3 = Some("line3"),
          line4 = Some("line4"),
          line5 = Some("line5"),
          country = Some(Country("GB", "United Kingdom")),
          postcode = Some("residentialAddress2")
        )

        val correspondenceAddress = models.Address(
          line1 = "line1",
          line2 = Some("line2"),
          line3 = Some("line3"),
          line4 = Some("line4"),
          line5 = Some("line5"),
          country = Some(Country("GB", "United Kingdom")),
          postcode = Some("correspondenceAddress2")
        )

        models.DesignatoryDetails(
          name = Some(name),
          residentialAddress = Some(residentialAddress),
          correspondenceAddress = Some(correspondenceAddress)
        )
      }

      when(mockIfConnector.getDesignatoryDetails(any())(any())).thenReturn(Future.successful(ifResponse))

      service.getDesignatoryDetails(nino).futureValue mustEqual expectedResponse
    }

    "must return a known-as name when there are no real names" in {

      implicit val hc: HeaderCarrier = HeaderCarrier()
      val nino = NinoGenerator.randomNino()

      val ifResponse = {

        val knownAs1 = models.integration.Name(
          nameSequenceNumber = 1,
          nameType = 2,
          titleType = 1,
          firstForename = "first",
          secondForename = None,
          surname = "knownAs1"
        )

        val knownAs2 = knownAs1.copy(
          nameSequenceNumber = 2,
          surname = "knownAs2"
        )

        models.integration.DesignatoryDetails(
          names = List(knownAs1, knownAs2),
          addresses = List.empty
        )
      }

      val expectedResponse = {

        val name = models.Name(
          title = Some("Mr"),
          firstName = "first",
          middleName = None,
          lastName = "knownAs2"
        )

        models.DesignatoryDetails(
          name = Some(name),
          residentialAddress = None,
          correspondenceAddress = None
        )
      }

      when(mockIfConnector.getDesignatoryDetails(any())(any())).thenReturn(Future.successful(ifResponse))

      service.getDesignatoryDetails(nino).futureValue mustEqual expectedResponse
    }

    "must fail when the IfConnector call fails" in {

      implicit val hc: HeaderCarrier = HeaderCarrier()
      val nino = NinoGenerator.randomNino()

      when(mockIfConnector.getDesignatoryDetails(any())(any())).thenReturn(Future.failed(new RuntimeException()))

      service.getDesignatoryDetails(nino).failed.futureValue
    }
  }
}
