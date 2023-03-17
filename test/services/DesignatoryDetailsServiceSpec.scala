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

import connectors.IndividualDetailsConnector
import models.{Country, DesignatoryDetails, Done}
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchersSugar.eqTo
import org.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.DesignatoryDetailsCacheRepository
import uk.gov.hmrc.http.HeaderCarrier
import utils.NinoGenerator

import java.time.LocalDate
import scala.concurrent.Future

class DesignatoryDetailsServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar with ScalaFutures {

  private val mockIndividualDetailsConnector = mock[IndividualDetailsConnector]
  private val mockRepository = mock[DesignatoryDetailsCacheRepository]

  private val app = GuiceApplicationBuilder()
    .overrides(
      bind[IndividualDetailsConnector].toInstance(mockIndividualDetailsConnector),
      bind[DesignatoryDetailsCacheRepository].toInstance(mockRepository)
    )
    .build()

  private val service = app.injector.instanceOf[DesignatoryDetailsService]

  private val individualDetailsResponse = {

    val realName1 = models.integration.Name(
      nameSequenceNumber = 1,
      nameType = 1,
      titleType = 1,
      firstForename = Some("first"),
      secondForename = Some("middle"),
      surname = Some("real1"),
      nameEndDate = None
    )

    val realName2 = realName1.copy(
      nameSequenceNumber = 2,
      surname = Some("real2")
    )

    val realName3 = realName1.copy(
      nameSequenceNumber = 3,
      surname = Some("real3"),
      nameEndDate = Some(LocalDate.now)
    )

    val knownAs1 = realName1.copy(
      nameSequenceNumber = 4,
      nameType = 2,
      surname = Some("knownAs1")
    )

    val knownAs2 = realName1.copy(
      nameSequenceNumber = 5,
      nameType = 2,
      surname = Some("knownAs2")
    )

    val knownAs3 = realName1.copy(
      nameSequenceNumber = 6,
      nameType = 2,
      surname = Some("knownAs3"),
      nameEndDate = Some(LocalDate.now)
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
      addressPostcode = Some("residentialAddress1"),
      addressEndDate = None
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

    val residentialAddress3 = residentialAddress1.copy(
      addressSequenceNumber = 5,
      addressPostcode = Some("residentialAddress3"),
      addressEndDate = Some(LocalDate.now)
    )

    val correspondenceAddress3 = residentialAddress1.copy(
      addressSequenceNumber = 6,
      addressType = 2,
      addressPostcode = Some("correspondenceAddress3"),
      addressEndDate = Some(LocalDate.now)
    )

    models.integration.DesignatoryDetails(
      dateOfBirth = LocalDate.of(2020, 2, 1),
      names = List(
        realName1,
        knownAs1,
        realName2,
        knownAs2,
        realName3,
        knownAs3
      ),
      addresses = List(
        residentialAddress1,
        residentialAddress2,
        correspondenceAddress1,
        correspondenceAddress2,
        residentialAddress3,
        correspondenceAddress3
      )
    )
  }

  val expectedResponse = {

    val realName = models.Name(
      title = Some("Mr"),
      firstName = Some("first"),
      middleName = Some("middle"),
      lastName = Some("real2")
    )

    val knownAsName = models.Name(
      title = Some("Mr"),
      firstName = Some("first"),
      middleName = Some("middle"),
      lastName = Some("knownAs2")
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
      dateOfBirth = LocalDate.of(2020, 2, 1),
      realName = Some(realName),
      knownAsName = Some(knownAsName),
      residentialAddress = Some(residentialAddress),
      correspondenceAddress = Some(correspondenceAddress)
    )
  }

  "getDesignatoryDetails" - {

    "when details have not been cached" - {

      "must cache details and return designatory details for the given nino" in {

        implicit val hc: HeaderCarrier = HeaderCarrier()
        val nino = NinoGenerator.randomNino()

        when(mockIndividualDetailsConnector.getDesignatoryDetails(any(), any())(any())).thenReturn(Future.successful(individualDetailsResponse))
        when(mockRepository.set(any(), any())).thenReturn(Future.successful(Done))
        when(mockRepository.get(any())).thenReturn(Future.successful(None))

        service.getDesignatoryDetails(nino).futureValue mustEqual expectedResponse
        verify(mockRepository, times(1)).get(eqTo(nino))
      }

      "must return details when they cannot be cached" in {

        implicit val hc: HeaderCarrier = HeaderCarrier()
        val nino = NinoGenerator.randomNino()

        when(mockIndividualDetailsConnector.getDesignatoryDetails(any(), any())(any())).thenReturn(Future.successful(individualDetailsResponse))
        when(mockRepository.set(any(), any())).thenReturn(Future.failed(new RuntimeException("foo")))
        when(mockRepository.get(any())).thenReturn(Future.successful(None))

        service.getDesignatoryDetails(nino).futureValue mustEqual expectedResponse
        verify(mockRepository, times(1)).get(eqTo(nino))
      }
    }

    "when details have been cached" - {

      "must return the cached details" in {

        implicit val hc: HeaderCarrier = HeaderCarrier()
        val nino = NinoGenerator.randomNino()

        val details = DesignatoryDetails(LocalDate.now, None, None, None, None)

        when(mockRepository.get(any())).thenReturn(Future.successful(Some(details)))

        service.getDesignatoryDetails(nino).futureValue mustEqual details
      }
    }

    "must fail when the IfConnector call fails" in {

      implicit val hc: HeaderCarrier = HeaderCarrier()
      val nino = NinoGenerator.randomNino()

      when(mockIndividualDetailsConnector.getDesignatoryDetails(any(), any())(any())).thenReturn(Future.failed(new RuntimeException()))
      when(mockRepository.get(any())).thenReturn(Future.successful(None))

      service.getDesignatoryDetails(nino).failed.futureValue
    }
  }
}
