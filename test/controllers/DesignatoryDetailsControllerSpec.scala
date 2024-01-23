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

import models.Country
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.UserDataRepository
import services.DesignatoryDetailsService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.retrieve.~
import utils.NinoGenerator

import java.time.LocalDate
import scala.concurrent.Future

class DesignatoryDetailsControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockService = mock[DesignatoryDetailsService]
  private val mockRepo = mock[UserDataRepository]

  override def beforeEach(): Unit = {
    Mockito.reset[Any](
      mockService,
      mockAuthConnector,
      mockRepo
    )
    super.beforeEach()
  }

  private val app = GuiceApplicationBuilder()
    .overrides(
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[DesignatoryDetailsService].toInstance(mockService),
      bind[UserDataRepository].toInstance(mockRepo)
    ).build()

  ".get" - {

    "must return OK and the data when the user has a nino and the designatory details service has data" in {

      val nino = NinoGenerator.randomNino()

      val response = {

        val name = models.Name(
          title = Some("Mr"),
          firstName = Some("first"),
          middleName = Some("middle"),
          lastName = Some("real2")
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
          realName = Some(name),
          knownAsName = None,
          residentialAddress = Some(residentialAddress),
          correspondenceAddress = Some(correspondenceAddress)
        )
      }

      when(mockAuthConnector.authorise[Option[String] ~ Option[String]](any(), any())(any(), any()))
        .thenReturn(Future.successful(new ~(Some("userId"), Some(nino))))

      when(mockService.getDesignatoryDetails(any())(any()))
        .thenReturn(Future.successful(response))

      val request = FakeRequest(GET, routes.DesignatoryDetailsController.get.url)
      val result = route(app, request).value

      status(result) mustEqual OK
      contentAsJson(result) mustEqual Json.toJson(response)

      verify(mockService).getDesignatoryDetails(eqTo(nino))(any())
    }

    "must fail when the designatory details service fails" in {

      val nino = NinoGenerator.randomNino()

      when(mockAuthConnector.authorise[Option[String] ~ Option[String]](any(), any())(any(), any()))
        .thenReturn(Future.successful(new ~(Some("userId"), Some(nino))))

      when(mockService.getDesignatoryDetails(any())(any()))
        .thenReturn(Future.failed(new RuntimeException()))

      val request = FakeRequest(GET, routes.DesignatoryDetailsController.get.url)
      route(app, request).value.failed.futureValue
    }

    "must return BadRequest when the user has no nino" in {

      when(mockAuthConnector.authorise[Option[String] ~ Option[String]](any(), any())(any(), any()))
        .thenReturn(Future.successful(new ~(Some("userId"), None)))

      when(mockService.getDesignatoryDetails(any())(any()))
        .thenReturn(Future.failed(new RuntimeException()))

      val request = FakeRequest(GET, routes.DesignatoryDetailsController.get.url)
      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.obj("error" -> "No NINO available for request")
    }
  }
}
