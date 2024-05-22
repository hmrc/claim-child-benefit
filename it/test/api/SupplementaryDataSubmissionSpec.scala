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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, urlMatching}
import models.dmsa.{SubmissionItem, SubmissionItemStatus, SubmissionResponse}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.{ACCEPTED, CREATED, OK}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import play.api.test.Helpers.AUTHORIZATION
import play.api.test.RunningServer
import repositories.SubmissionItemRepository
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import utils.NinoGenerator

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.UUID

class SupplementaryDataSubmissionSpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[SubmissionItem]
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterEach
    with GuiceOneServerPerSuite
    with OptionValues
    with WireMockSupport {

  private implicit val actorSystem: ActorSystem = ActorSystem()
  private val httpClient: StandaloneAhcWSClient = StandaloneAhcWSClient()
  private val internalAuthBaseUrl: String = "http://localhost:8470"
  private val sdesStubBaseUrl: String = "http://localhost:9191"
  private val claimChildBenefitAuthToken: String = UUID.randomUUID().toString
  private val clientAuthToken: String = UUID.randomUUID().toString

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .overrides(
      bind[MongoComponent].toInstance(mongoComponent)
    )
    .configure(
      "internal-auth.token" -> claimChildBenefitAuthToken,
      "workers.enabled" -> true,
      "workers.initial-delay" -> "0 seconds",
      "workers.sdes-notification-worker.interval" -> "1 second",
    )
    .build()

  override protected lazy val repository: SubmissionItemRepository =
    app.injector.instanceOf[SubmissionItemRepository]

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
    val submissionDate = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    val submissionDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(submissionDate, ZoneOffset.UTC))
    val correlationId = UUID.randomUUID().toString

    wireMockServer.stubFor(
      post(urlMatching("/callback"))
        .willReturn(aResponse().withStatus(OK))
    )

    val response = httpClient.url(s"http://localhost:$port/claim-child-benefit/supplementary-data")
      .withHttpHeaders(
        AUTHORIZATION -> clientAuthToken,
      )
      .post(
        Source(Seq(
          DataPart("metadata.nino", nino),
          DataPart("metadata.submissionDate", submissionDateString),
          DataPart("metadata.correlationId", correlationId),
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

    val id = response.body[JsValue].as[SubmissionResponse.Success].id

    eventually(Timeout(Span(30, Seconds))) {
      repository.get(id).futureValue.value.status mustEqual SubmissionItemStatus.Completed
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
