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

package health

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlMatching}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import uk.gov.hmrc.http.test.WireMockSupport

class HealthEndpointIntegrationSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with WireMockSupport {

  private lazy val app: Application =
    GuiceApplicationBuilder()
      .configure(
        "metrics.enabled" -> false,
        "microservice.services.internal-auth.port" -> wireMockServer.port()
      ).build()

  private lazy val wsClient = app.injector.instanceOf[WSClient]
  private lazy val baseUrl  = s"http://localhost:${wireMockServer.port()}"

  "service health endpoint" should {
    "respond with 200 status" in {
      wireMockServer.stubFor(
        get(urlMatching("/test-only/token"))
          .willReturn(aResponse().withStatus(OK))
      )

      wireMockServer.stubFor(
        get(urlMatching("/ping/ping"))
          .willReturn(aResponse().withStatus(OK))
      )

      val response =
        wsClient
          .url(s"$baseUrl/ping/ping")
          .get()
          .futureValue

      response.status shouldBe 200
    }
  }
}
