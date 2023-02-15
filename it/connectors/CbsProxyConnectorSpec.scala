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
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Application
import play.api.http.Status.CREATED
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import util.WireMockHelper

class CbsProxyConnectorSpec extends AnyFreeSpec with Matchers with ScalaFutures with IntegrationPatience with WireMockHelper {

  private lazy val app: Application =
    GuiceApplicationBuilder()
      .configure(
        "microservice.services.cbs.port" -> server.port(),
        "microservice.services.cbs.environment" -> "env",
        "microservice.services.cbs.auth" -> "auth"
      )
      .build()

  private lazy val connector = app.injector.instanceOf[CbsProxyConnector]

  "submit" - {

    val hc = HeaderCarrier()
    val url = "/child-benefit/claim"

    "must return the response from calling CBS" in {

      val body = Json.obj("foo" -> "bar")

      server.stubFor(
        post(urlMatching(url))
          .withRequestBody(equalToJson(Json.stringify(Json.obj())))
          .withHeader("Environment", equalTo("env"))
          .withHeader("Authorization", equalTo("Bearer auth"))
          .withHeader("CorrelationId", equalTo("correlationId"))
          .willReturn(
            aResponse()
              .withStatus(CREATED)
              .withBody(Json.stringify(body))
          )
      )

      val result = connector.submit(Json.obj(), Some("correlationId"))(hc).futureValue

      result.status mustEqual CREATED
      result.body mustEqual Json.stringify(body)
    }

    "must fail when there is a connection error" in {

      server.stubFor(
        post(urlMatching(url))
          .withRequestBody(equalToJson(Json.stringify(Json.obj())))
          .withHeader("Environment", equalTo("env"))
          .withHeader("Authorization", equalTo("Bearer auth"))
          .withHeader("CorrelationId", equalTo("correlationId"))
          .willReturn(
            aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE)
          )
      )

      connector.submit(Json.obj(), Some("correlationId"))(hc).failed.futureValue
    }
  }
}
