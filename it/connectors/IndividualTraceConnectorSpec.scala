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
import models.IndividualTraceRequest
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Application
import play.api.http.HeaderNames
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import util.WireMockHelper

import java.time.LocalDate
import java.util.UUID

class IndividualTraceConnectorSpec
  extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with WireMockHelper {


  private lazy val app: Application =
    GuiceApplicationBuilder()
      .configure(
        "microservice.services.individual-trace.port" -> server.port(),
        "microservice.services.individual-trace.path" -> "",
        "microservice.services.individual-trace.auth" -> "api-key",
        "microservice.services.individual-trace.env" -> "env",
        "microservice.services.individual-trace.originator-id" -> "originator-id"
      )
      .build()

  private lazy val connector = app.injector.instanceOf[IndividualTraceConnector]

  ".trace" - {

    val hc = HeaderCarrier()
    val url = "/individuals/trace"
    val request = IndividualTraceRequest("first", "last", LocalDate.of(2000, 1, 2))
    val correlationId = UUID.randomUUID().toString

    "must return true when the server returns OK" in {

      server.stubFor(
        post(urlMatching(url))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(request)(IndividualTraceRequest.desWrites))))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer api-key"))
          .withHeader("CorrelationId", equalTo(correlationId))
          .withHeader("OriginatorId", equalTo("originator-id"))
          .withHeader("Environment", equalTo("env"))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo("application/json"))
          .willReturn(ok())
      )

      connector.trace(request, correlationId)(hc).futureValue mustEqual true
    }

    "must return true when the server returns 400 with an error code of TOO_MANY_MATCHES" in {

      val responseBody = Json.obj(
        "code" -> "TOO_MANY_MATCHES",
        "reason" -> "The remote endpoint has indicated that there were more than 50 matches found."
      )

      server.stubFor(
        post(urlMatching(url))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(request)(IndividualTraceRequest.desWrites))))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer api-key"))
          .withHeader("CorrelationId", equalTo(correlationId))
          .withHeader("OriginatorId", equalTo("originator-id"))
          .withHeader("Environment", equalTo("env"))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo("application/json"))
          .willReturn(badRequest().withBody(Json.stringify(responseBody)))
      )

      connector.trace(request, correlationId)(hc).futureValue mustEqual true
    }

    "must return false when the service returns 404 with an error code of NO_MATCH_FOUND" in {

      val responseBody = Json.obj(
        "code" -> "NO_MATCH_FOUND",
        "reason" -> "The remote endpoint has indicated that no match was found."
      )

      server.stubFor(
        post(urlMatching(url))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(request)(IndividualTraceRequest.desWrites))))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer api-key"))
          .withHeader("CorrelationId", equalTo(correlationId))
          .withHeader("OriginatorId", equalTo("originator-id"))
          .withHeader("Environment", equalTo("env"))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo("application/json"))
          .willReturn(notFound().withBody(Json.stringify(responseBody)))
      )

      connector.trace(request, correlationId)(hc).futureValue mustEqual false
    }

    "must fail when the service returns 400 with any code except TOO_MANY_MATCHES" in {

      val responseBody = Json.obj(
        "code" -> "INVALID_PAYLOAD",
        "reason" -> "Submission has not passed validation. Invalid Payload."
      )

      server.stubFor(
        post(urlMatching(url))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(request)(IndividualTraceRequest.desWrites))))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer api-key"))
          .withHeader("CorrelationId", equalTo(correlationId))
          .withHeader("OriginatorId", equalTo("originator-id"))
          .withHeader("Environment", equalTo("env"))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo("application/json"))
          .willReturn(badRequest().withBody(Json.stringify(responseBody)))
      )

      connector.trace(request, correlationId)(hc).failed.futureValue
    }

    "must fail when the service returns 404 without a code" in {

      server.stubFor(
        post(urlMatching(url))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(request)(IndividualTraceRequest.desWrites))))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer api-key"))
          .withHeader("CorrelationId", equalTo(correlationId))
          .withHeader("OriginatorId", equalTo("originator-id"))
          .withHeader("Environment", equalTo("env"))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo("application/json"))
          .willReturn(notFound())
      )

      connector.trace(request, correlationId)(hc).failed.futureValue
    }

    "must fail when the service returns an error code without a code" in {

      server.stubFor(
        post(urlMatching(url))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(request)(IndividualTraceRequest.desWrites))))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer api-key"))
          .withHeader("CorrelationId", equalTo(correlationId))
          .withHeader("OriginatorId", equalTo("originator-id"))
          .withHeader("Environment", equalTo("env"))
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo("application/json"))
          .willReturn(aResponse().withStatus(500))
      )

      connector.trace(request, correlationId)(hc).failed.futureValue
    }
  }
}