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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import models.integration.{Address, DesignatoryDetails, Name}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Application
import play.api.http.HeaderNames
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import util.WireMockHelper
import utils.NinoGenerator

class IfConnectorSpec extends AnyFreeSpec with Matchers with ScalaFutures with IntegrationPatience with WireMockHelper {

  private lazy val app: Application =
    GuiceApplicationBuilder()
      .configure(
        "microservice.services.integration-framework.port" -> server.port(),
        "microservice.services.integration-framework.api-key" -> "api-key"
      )
      .build()

  private lazy val connector = app.injector.instanceOf[IfConnector]

  "getDesignatoryDetails" - {

    val hc = HeaderCarrier()

    val name = Name(
      nameSequenceNumber = 0,
      nameType = 1,
      titleType = 0,
      firstForename = "first",
      secondForename = Some("middle"),
      surname = "surname"
    )

    val address = Address(
      addressSequenceNumber = 0,
      countryCode = Some(1),
      addressType = 1,
      addressLine1 = "line1",
      addressLine2 = None,
      addressLine3 = None,
      addressLine4 = None,
      addressLine5 = None,
      addressPostcode = Some("postcode")
    )

    val expectedResult = DesignatoryDetails(
      names = List(name),
      addresses = List(address)
    )

    "must return designatory details when they exist" in {

      val nino = NinoGenerator.randomNino()
      val url = s"/individuals/details/NINO/$nino"

      server.stubFor(
        get(urlPathEqualTo(url))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("api-key"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.stringify(Json.toJson(expectedResult)))
          )
      )

      connector.getDesignatoryDetails(nino)(hc).futureValue mustEqual expectedResult
    }

    "must return a failed future when the server responds with anything else" in {

      val nino = NinoGenerator.randomNino()
      val url = s"/individuals/details/NINO/$nino"

      server.stubFor(
        get(urlPathEqualTo(url))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("api-key"))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      connector.getDesignatoryDetails(nino)(hc).failed.futureValue
    }

    "must return a failed future when there is a connection error" in {

      val nino = NinoGenerator.randomNino()
      val url = s"/individuals/details/NINO/$nino"

      server.stubFor(
        get(urlPathEqualTo(url))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("api-key"))
          .willReturn(
            aResponse()
              .withFault(Fault.RANDOM_DATA_THEN_CLOSE)
          )
      )

      connector.getDesignatoryDetails(nino)(hc).failed.futureValue
    }
  }
}
