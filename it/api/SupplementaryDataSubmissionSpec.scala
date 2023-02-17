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

package api

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.github.tomakehurst.wiremock.client.WireMock._
import models.dmsa.{SubmissionItemStatus, SubmissionResponse}
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.{ACCEPTED, CREATED, NO_CONTENT, OK}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import play.api.test.Helpers.AUTHORIZATION
import play.api.test.RunningServer
import repositories.SubmissionItemRepository
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.http.RequestId
import util.WireMockHelper
import utils.NinoGenerator

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class SupplementaryDataSubmissionSpec extends AnyFreeSpec with Matchers with ScalaFutures with IntegrationPatience with BeforeAndAfterEach with GuiceOneServerPerSuite with OptionValues {

  private implicit val actorSystem: ActorSystem = ActorSystem()
  private val httpClient: StandaloneAhcWSClient = StandaloneAhcWSClient()
  private val internalAuthBaseUrl: String = "http://localhost:8470"
  private val sdesStubBaseUrl: String = "http://localhost:9191"
  private val claimChildBenefitAuthToken: String = UUID.randomUUID().toString
  private val clientAuthToken: String = UUID.randomUUID().toString
  private lazy val submissionRepository: SubmissionItemRepository = fakeApplication().injector.instanceOf[SubmissionItemRepository]

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .configure(
      "internal-auth.token" -> claimChildBenefitAuthToken,
      "workers.initial-delay" -> "0 seconds",
      "workers.sdes-notification-worker.interval" -> "1 second",
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    if (!authTokenIsValid(claimChildBenefitAuthToken)) createClaimChildBenefitToken()
    if (!authTokenIsValid(clientAuthToken)) createClientAuthToken()
    clearSdesCallbacks()
  }

  override protected implicit lazy val runningServer: RunningServer =
    FixedPortTestServerFactory.start(app)

  private val pdfBytes: ByteString = {
    val stream = getClass.getResourceAsStream("/test.pdf")
    try {
      ByteString(stream.readAllBytes())
    } finally {
      stream.close()
    }
  }

  "Successful submissions must return ACCEPTED and receive callbacks confirming files have been processed" in {

    val nino = NinoGenerator.randomNino()
    val requestId = UUID.randomUUID().toString

    val response = httpClient.url(s"http://localhost:$port/claim-child-benefit/supplementary-data")
      .withHttpHeaders(
        AUTHORIZATION -> clientAuthToken,
        xRequestId -> requestId
      )
      .post(
        Source(Seq(
          DataPart("metadata.nino", nino),
          FilePart(
            key = "file",
            filename = "form.pdf",
            contentType = Some("application/octet-stream"),
            ref = Source.single(pdfBytes),
            fileSize = 0
          )
        ))
      ).futureValue

    response.status mustEqual ACCEPTED

    response.body[JsValue].as[SubmissionResponse.Success].id mustEqual requestId

    eventually(Timeout(Span(30, Seconds))) {
      submissionRepository.get(requestId).futureValue.value.status mustEqual SubmissionItemStatus.Completed
    }
  }

  private def createClaimChildBenefitToken(): Unit = {
    val response = httpClient.url(s"$internalAuthBaseUrl/test-only/token")
      .post(
        Json.obj(
          "token" -> claimChildBenefitAuthToken,
          "principal" -> "claim-child-benefit",
          "permissions" -> Seq(
            Json.obj(
              "resourceType" -> "object-store",
              "resourceLocation" -> "claim-child-benefit",
              "actions" -> List("READ", "WRITE", "DELETE")
            )
          )
        )
      ).futureValue
    response.status mustEqual CREATED
  }

  private def createClientAuthToken(): Unit = {
    val response = httpClient.url(s"$internalAuthBaseUrl/test-only/token")
      .post(
        Json.obj(
          "token" -> clientAuthToken,
          "principal" -> "test",
          "permissions" -> Seq(
            Json.obj(
              "resourceType" -> "claim-child-benefit",
              "resourceLocation" -> "*",
              "actions" -> List("WRITE")
            )
          )
        )
      ).futureValue
    response.status mustEqual CREATED
  }

  private def authTokenIsValid(token: String): Boolean = {
    val response = httpClient.url(s"$internalAuthBaseUrl/test-only/token")
      .withHttpHeaders("Authorization" -> token)
      .get()
      .futureValue
    response.status == OK
  }

  private def clearSdesCallbacks(): Unit = {
    val response = httpClient.url(s"$sdesStubBaseUrl/sdes-stub/configure/notification/fileready")
      .delete()
      .futureValue
    response.status mustEqual OK
  }
}
