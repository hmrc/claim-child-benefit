/*
 * Copyright 2024 HM Revenue & Customs
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

package config

import com.github.tomakehurst.wiremock.client.WireMock._
import org.apache.pekko.Done
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.Configuration
import play.api.http.Status.{BAD_GATEWAY, CREATED, NOT_FOUND, OK}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.running
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}

import scala.concurrent.ExecutionContext.Implicits.global

class InternalAuthTokenInitialiserSpec
  extends AnyFreeSpec
    with WireMockSupport
    with HttpClientV2Support {

  private def builder(createToken: Boolean): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .configure(
        "create-internal-auth-token-on-start"      -> createToken,
        "microservice.services.internal-auth.port" -> wireMockPort
      )

  "InternalAuthTokenInitialiser" - {
    "must return Done with no requests sent to internal-auth" in {
      wireMockServer.stubFor(
        get(urlMatching("/test-only/token"))
          .willReturn(aResponse().withStatus(OK))
      )

      wireMockServer.stubFor(
        post(urlMatching("/test-only/token"))
          .willReturn(aResponse().withStatus(CREATED))
      )

      val app = builder(createToken = false).build()

      running(app) {
        app.injector.instanceOf[InternalAuthTokenInitialiser].initialised.futureValue mustBe Done

        wireMockServer.verify(0, getRequestedFor(urlMatching("/test-only/token")))
        wireMockServer.verify(0, postRequestedFor(urlMatching("/test-only/token")))
      }
    }

    "must return Done with one request to internal-auth" in {
      wireMockServer.stubFor(
        get(urlMatching("/test-only/token"))
          .willReturn(aResponse().withStatus(OK))
      )

      wireMockServer.stubFor(
        post(urlMatching("/test-only/token"))
          .willReturn(aResponse().withStatus(CREATED))
      )

      val app = builder(createToken = true).build()

      running(app) {
        app.injector.instanceOf[InternalAuthTokenInitialiser].initialised.futureValue mustBe Done

        wireMockServer.verify(1, getRequestedFor(urlMatching("/test-only/token")))
        wireMockServer.verify(0, postRequestedFor(urlMatching("/test-only/token")))
      }
    }

    "must return Done with two request to internal-auth" in {
      wireMockServer.stubFor(
        get(urlMatching("/test-only/token"))
          .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      wireMockServer.stubFor(
        post(urlMatching("/test-only/token"))
          .willReturn(aResponse().withStatus(CREATED))
      )

      val app = builder(createToken = true).build()

      running(app) {
        app.injector.instanceOf[InternalAuthTokenInitialiser].initialised.futureValue mustBe Done

        wireMockServer.verify(1, getRequestedFor(urlMatching("/test-only/token")))
        wireMockServer.verify(1, postRequestedFor(urlMatching("/test-only/token")))
      }
    }

    "must return RuntimeException when create token fails" in {
      wireMockServer.stubFor(
        get(urlMatching("/test-only/token"))
          .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      wireMockServer.stubFor(
        post(urlMatching("/test-only/token"))
          .willReturn(aResponse().withStatus(BAD_GATEWAY))
      )

      try new InternalAuthTokenInitialiserImpl(
        configuration = Configuration(
          "microservice.services.internal-auth.port"     -> wireMockPort,
          "microservice.services.internal-auth.host"     -> wireMockHost,
          "microservice.services.internal-auth.protocol" -> "http",
          "internal-auth.token"                          -> "token",
          "appName"                                      -> "appName"
        ),
        httpClient    = httpClientV2
      ) catch {
        case e: RuntimeException =>
          e.getMessage mustBe "Unable to initialise internal-auth token"
      }
    }
  }
}
