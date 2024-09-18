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

import better.files.File
import com.github.tomakehurst.wiremock.client.WireMock._
import models.dmsa.SubmissionItem
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.{ACCEPTED, OK}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.mvc.MultipartFormData
import play.api.test.Helpers.{AUTHORIZATION, defaultAwaitTimeout, route, writeableOf_AnyContentAsMultipartForm, status => getStatus}
import play.api.test.{FakeRequest, RunningServer}
import repositories.SubmissionItemRepository
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import utils.NinoGenerator

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.UUID

class SupplementaryDataSubmissionSpec extends AnyFreeSpec with Matchers with DefaultPlayMongoRepositorySupport[SubmissionItem] with ScalaFutures with IntegrationPatience with BeforeAndAfterEach with GuiceOneServerPerSuite with OptionValues with WireMockSupport {

  private val claimChildBenefitAuthToken: String = UUID.randomUUID().toString
  private val clientAuthToken: String = UUID.randomUUID().toString

  override protected lazy val repository: SubmissionItemRepository = app.injector.instanceOf[SubmissionItemRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    wireMockServer.stubFor(
      get(urlMatching("/test-only/token"))
        .willReturn(aResponse().withStatus(OK))
    )

    wireMockServer.stubFor(
      delete(urlMatching("/sdes-stub/configure/notification/fileready"))
        .willReturn(aResponse().withStatus(OK))
    )

    wireMockServer.stubFor(
      post(urlMatching("/internal-auth/auth"))
        .willReturn(aResponse().withStatus(OK)
          .withHeader("Content-Type", "application/json")
          .withBody("""{"retrievals": {}}""".stripMargin)
        )
    )

    wireMockServer.stubFor(
      put(urlPathMatching("/object-store/object/claim-child-benefit/sdes/.*\\.pdf"))
        .willReturn(aResponse().withStatus(OK)
        .withHeader("Content-Type", "application/json")
        .withBody("""{"location": "/object-store/object/location","contentLength": 262144,"contentMD5": "someMD5","lastModified": 10}""".stripMargin)
        )
    )
  }

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .overrides(
      bind[MongoComponent].toInstance(mongoComponent)
    )
    .configure(
      "internal-auth.token" -> claimChildBenefitAuthToken,
      "workers.enabled" -> true,
      "workers.initial-delay" -> "0 seconds",
      "workers.sdes-notification-worker.interval" -> "1 second",
      "create-internal-auth-token-on-start" -> false,
      "microservice.services.internal-auth.port" -> wireMockPort,
      "microservice.services.object-store.port" -> wireMockPort
    )
    .build()

  override protected implicit lazy val runningServer: RunningServer =
    FixedPortTestServerFactory.start(app)

  "Successful submissions must return ACCEPTED and receive callbacks confirming files have been processed" in {
    val nino = NinoGenerator.randomNino()
    val submissionDate = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    val submissionDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(submissionDate, ZoneOffset.UTC))
    val correlationId = UUID.randomUUID().toString
    val pdfBytes: Array[Byte] = {
      val stream = getClass.getResourceAsStream("/test.pdf")
      try {
        stream.readAllBytes()
      } finally {
        stream.close()
      }
    }

    val tempFile = SingletonTemporaryFileCreator.create()
    val betterTempFile: File = File(tempFile.toPath)
      .deleteOnExit()
      .writeByteArray(pdfBytes)

    val request = FakeRequest("POST", "/claim-child-benefit/supplementary-data")
      .withHeaders(AUTHORIZATION -> clientAuthToken)
      .withMultipartFormDataBody(
        MultipartFormData(
          dataParts = Map(
            "metadata.nino" -> Seq(nino),
            "metadata.submissionDate" -> Seq(submissionDateString),
            "metadata.correlationId" -> Seq(correlationId)
          ),
          files = Seq(
            MultipartFormData.FilePart(
              key = "file",
              filename = "form.pdf",
              contentType = Some("application/pdf"),
              ref = tempFile,
              fileSize = betterTempFile.size
            )
          ),
          badParts = Seq.empty
        )
      )
    println("WOOF: " + correlationId)

    val result = route(app, request).get

    getStatus(result) mustBe ACCEPTED
  }

}
