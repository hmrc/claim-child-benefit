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
import models.{Relationship, RelationshipDetails, RelationshipSource, RelationshipType, Relationships}
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

import java.util.UUID

class RelationshipDetailsConnectorSpec
  extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with WireMockHelper {

  private lazy val app: Application =
    GuiceApplicationBuilder()
      .configure(
        "microservice.services.relationship-details.port" -> server.port(),
        "microservice.services.relationship-details.auth" -> "api-key",
        "microservice.services.relationship-details.originator-id" -> "originator-id",
        "microservice.services.relationship-details.environment" -> "env",
      )
      .build()

  private lazy val connector = app.injector.instanceOf[RelationshipDetailsConnector]

  "getRelationships" - {

    val hc = HeaderCarrier()

    val expectedResult = RelationshipDetails(
      Relationships(
        Some(List(
          Relationship(
            relationshipType = RelationshipType.AdultChild,
            relationshipSource = RelationshipSource.CHB
          )
        )
      )
    ))

    "must return relationships when they exist" in {

      val nino = NinoGenerator.randomNino()
      val trimmedNino = nino.take(8)
      val url = s"/individuals/relationship/$trimmedNino"
      val correlationId = UUID.randomUUID().toString

      server.stubFor(
        get(urlPathEqualTo(url))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer api-key"))
          .withHeader("CorrelationId", equalTo(correlationId))
          .withHeader("OriginatorId", equalTo("originator-id"))
          .withHeader("Environment", equalTo("env"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.stringify(Json.toJson(expectedResult)))
          )
      )

      connector.getRelationships(nino, correlationId)(hc).futureValue mustEqual expectedResult
    }

    "must return a failed future when the server responds with anything else" in {

      val nino = NinoGenerator.randomNino()
      val trimmedNino = nino.take(8)
      val url = s"/individuals/relationship/$trimmedNino"

      server.stubFor(
        get(urlPathEqualTo(url))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer api-key"))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      connector.getRelationships(nino)(hc).failed.futureValue
    }

    "must return a failed future when there is a connection error" in {

      val nino = NinoGenerator.randomNino()
      val trimmedNino = nino.take(8)
      val url = s"/individuals/relationship/$trimmedNino"

      server.stubFor(
        get(urlPathEqualTo(url))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer api-key"))
          .willReturn(
            aResponse()
              .withFault(Fault.RANDOM_DATA_THEN_CLOSE)
          )
      )

      connector.getRelationships(nino)(hc).failed.futureValue
    }
  }
}
