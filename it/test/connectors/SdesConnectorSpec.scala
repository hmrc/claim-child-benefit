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
import models.sdes.{FileAudit, FileChecksum, FileMetadata, FileNotifyRequest}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Application
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NO_CONTENT, OK}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport

class SdesConnectorSpec extends AnyFreeSpec with Matchers with ScalaFutures with IntegrationPatience with WireMockSupport {

  private lazy val app: Application =
    GuiceApplicationBuilder()
      .configure(
        "microservice.services.sdes.port" -> wireMockServer.port(),
        "microservice.services.sdes.path" -> "",
        "services.sdes.client-id" -> "client-id",
        "microservice.services.internal-auth.port" -> wireMockServer.port()
      )
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    wireMockServer.stubFor(
      get(urlMatching("/test-only/token"))
        .willReturn(aResponse().withStatus(OK))
    )
  }

  private lazy val connector = app.injector.instanceOf[SdesConnector]

  "notify" - {

    val hc = HeaderCarrier()
    val url = "/notification/fileready"

    val request = FileNotifyRequest(
      "fraud-reporting",
      FileMetadata(
        "tax-fraud-reporting",
        "file1.dat",
        s"http://localhost:8464/object-store/object/tax-fraud-reporting/file1.dat",
        FileChecksum("md5", value = "hashValue"),
        2000,
        List()
      ),
      FileAudit("uuid")
    )

    "must return Done when SDES responds with NO_CONTENT" in {
      wireMockServer.stubFor(
        post(urlMatching(url))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(request))))
          .withHeader("x-client-id", equalTo("client-id"))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      connector.notify(request)(hc).futureValue
    }

    "must return a failed future when SDES responds with anything else" in {
      wireMockServer.stubFor(
        post(urlMatching(url))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(request))))
          .withHeader("x-client-id", equalTo("client-id"))
          .willReturn(aResponse().withBody("body").withStatus(INTERNAL_SERVER_ERROR))
      )

      val exception = connector.notify(request)(hc).failed.futureValue
      exception mustEqual SdesConnector.UnexpectedResponseException(500, "body")
    }

    "must return a failed future when there is a connection error" in {
      wireMockServer.stubFor(
        post(urlMatching(url))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(request))))
          .withHeader("x-client-id", equalTo("client-id"))
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
      )

      connector.notify(request)(hc).failed.futureValue
    }
  }
}
