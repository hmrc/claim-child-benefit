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
import uk.gov.hmrc.http.test.WireMockSupport
import utils.NinoGenerator

import java.time.LocalDate
import java.util.UUID

class DesIndividualDetailsConnectorSpec extends AnyFreeSpec with Matchers with ScalaFutures with IntegrationPatience with WireMockSupport {

  private lazy val app: Application =
    GuiceApplicationBuilder()
      .configure(
        "microservice.services.des.port" -> wireMockServer.port(),
        "microservice.services.des.auth" -> "api-key",
        "microservice.services.des.originator-id" -> "originator-id",
        "microservice.services.des.environment" -> "env",
        "microservice.services.des.resolve-merge" -> "Y",
        "microservice.services.internal-auth.port" -> wireMockServer.port()
      )
      .build()

  private lazy val connector = app.injector.instanceOf[DesIndividualDetailsConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()

    wireMockServer.stubFor(
      get(urlMatching("/test-only/token"))
        .willReturn(aResponse().withStatus(OK))
    )
  }

  "getDesignatoryDetails" - {

    val hc = HeaderCarrier()

    val name = Name(
      nameSequenceNumber = 0,
      nameType = 1,
      titleType = 0,
      firstForename = Some("first"),
      secondForename = Some("middle"),
      surname = Some("surname"),
      nameEndDate = Some(LocalDate.now)
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
      addressPostcode = Some("postcode"),
      addressEndDate = Some(LocalDate.now)
    )

    val expectedResult = DesignatoryDetails(
      dateOfBirth = LocalDate.of(2020, 2, 1),
      names = List(name),
      addresses = List(address)
    )

    "must return designatory details when they exist" in {

      val nino = NinoGenerator.randomNino()
      val url = s"/individuals/details/$nino/Y"
      val correlationId = UUID.randomUUID().toString

      wireMockServer.stubFor(
        get(urlPathEqualTo(url))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer api-key"))
          .withHeader("CorrelationId", equalTo(correlationId))
          .withHeader("OriginatorId", equalTo("originator-id"))
          .withHeader("Environment", equalTo("env"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.stringify(Json.toJson(expectedResult)(DesignatoryDetails.desFormats)))
          )
      )

      connector.getDesignatoryDetails(nino, correlationId)(hc).futureValue mustEqual expectedResult
    }

    "must return a failed future when the server responds with anything else" in {

      val nino = NinoGenerator.randomNino()
      val url = s"/individuals/details/$nino/Y"

      wireMockServer.stubFor(
        get(urlPathEqualTo(url))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer api-key"))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      connector.getDesignatoryDetails(nino)(hc).failed.futureValue
    }

    "must return a failed future when there is a connection error" in {

      val nino = NinoGenerator.randomNino()
      val url = s"/individuals/details/$nino/Y"

      wireMockServer.stubFor(
        get(urlPathEqualTo(url))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer api-key"))
          .willReturn(
            aResponse()
              .withFault(Fault.RANDOM_DATA_THEN_CLOSE)
          )
      )

      connector.getDesignatoryDetails(nino)(hc).failed.futureValue
    }
  }
}
