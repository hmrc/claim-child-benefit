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

package services

import connectors.SdesConnector
import org.apache.pekko.Done
import models.dmsa._
import models.sdes._
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Configuration, Environment}
import repositories.SubmissionItemRepository
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, SymmetricCryptoFactory}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import utils.NinoGenerator

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, LocalDateTime, ZoneOffset}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SdesServiceSpec extends AnyFreeSpec with Matchers
  with DefaultPlayMongoRepositorySupport[SubmissionItem]
  with ScalaFutures with IntegrationPatience
  with MockitoSugar with OptionValues with BeforeAndAfterEach {

  private val configuration: Configuration =
    Configuration.load(Environment.simple())

  private implicit val crypto: Encrypter with Decrypter =
    SymmetricCryptoFactory.aesGcmCryptoFromConfig("crypto", configuration.underlying)

  private val clock: Clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
  private val mockSdesConnector: SdesConnector = mock[SdesConnector]
  private val mockAuditService: AuditService = mock[AuditService]

  override protected lazy val repository = new SubmissionItemRepository(
    mongoComponent = mongoComponent,
    clock = clock,
    configuration = Configuration("lock-ttl" -> 30)
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset[Any](mockSdesConnector, mockAuditService)
  }

  private val app = GuiceApplicationBuilder()
    .configure(
      "services.sdes.information-type" -> "information-type",
      "services.sdes.recipient-or-sender" -> "recipient-or-sender",
      "services.sdes.object-store-location-prefix" -> "http://prefix/"
    )
    .overrides(
      bind[SdesConnector].toInstance(mockSdesConnector),
      bind[SubmissionItemRepository].toInstance(repository),
      bind[AuditService].toInstance(mockAuditService)
    )
    .build()

  private val nino: Nino = Nino(NinoGenerator.randomNino())

  private val service = app.injector.instanceOf[SdesService]

  private def randomItem = {
    val correlationId = UUID.randomUUID().toString
    SubmissionItem(
      id = UUID.randomUUID().toString,
      status = SubmissionItemStatus.Submitted,
      objectSummary = ObjectSummary(
        location = "location",
        contentLength = 1337,
        contentMd5 = "hash",
        lastModified = clock.instant().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS)
      ),
      metadata = Metadata(nino.value, clock.instant(), correlationId),
      failureReason = None,
      created = clock.instant().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS),
      lastUpdated = clock.instant().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS),
      sdesCorrelationId = correlationId
    )
  }

  "notifyOldestSubmittedItem" - {

    "must notify SDES for the latest submitted item, update the status to Forwarded, and return Found when there is an item to process" in {

      val item = {
        val baseItem = randomItem
        baseItem.copy(
          id = "someid",
          metadata = baseItem.metadata.copy(
            submissionDate = LocalDateTime.of(2022, 3, 15, 12, 30, 45).toInstant(ZoneOffset.UTC),
            correlationId = "correlationId"
          ),
          sdesCorrelationId = "correlationId"
        )
      }

      when(mockSdesConnector.notify(any())(any())).thenReturn(Future.successful(Done))

      repository.insert(item).futureValue
      service.notifyOldestSubmittedItem().futureValue mustBe QueryResult.Found

      val expectedRequest = FileNotifyRequest(
        "information-type",
        FileMetadata(
          recipientOrSender = "recipient-or-sender",
          name = s"${item.id}.pdf",
          location = "http://prefix/location",
          checksum = FileChecksum("md5", value = "85ab21"),
          size = 1337,
          properties = List(
            FileProperty("nino", nino.withoutSuffix),
            FileProperty("mimeType", "application/pdf"),
            FileProperty("submissionDate", "2022-03-15T12:30:45Z"),
            FileProperty("correlationId", "correlationId")
          )
        ),
        FileAudit(item.sdesCorrelationId)
      )

      verify(mockSdesConnector, times(1)).notify(eqTo(expectedRequest))(any())

      val updatedItem = repository.get(item.id).futureValue.value
      updatedItem.status mustEqual SubmissionItemStatus.Forwarded

      verify(mockAuditService, times(1)).auditSupplementaryDataSubmitted(eqTo(updatedItem))(any())
    }

    "must return NotFound when there is no item to process" in {
      service.notifyOldestSubmittedItem().futureValue mustBe QueryResult.NotFound
      verify(mockSdesConnector, times(0)).notify(any())(any())
    }

    "must return Found when the call to SDES fails" in {

      val item = randomItem

      when(mockSdesConnector.notify(any())(any())).thenReturn(Future.failed(new RuntimeException()))

      repository.insert(item).futureValue
      service.notifyOldestSubmittedItem().futureValue mustBe QueryResult.Found

      val updatedItem = repository.get(item.id).futureValue.value
      updatedItem.status mustEqual SubmissionItemStatus.Submitted
    }
  }

  "notifySubmittedItems" - {

    "must attempt to notify items until there are no more waiting" in {

      val item1 = randomItem
      val item2 = randomItem
      val item3 = randomItem

      when(mockSdesConnector.notify(any())(any())).thenReturn(Future.successful(Done))

      repository.insert(item1).futureValue
      repository.insert(item2).futureValue
      repository.insert(item3).futureValue

      service.notifySubmittedItems().futureValue

      verify(mockSdesConnector, times(3)).notify(any())(any())

      repository.get(item1.id).futureValue.value.status mustEqual SubmissionItemStatus.Forwarded
      repository.get(item2.id).futureValue.value.status mustEqual SubmissionItemStatus.Forwarded
      repository.get(item3.id).futureValue.value.status mustEqual SubmissionItemStatus.Forwarded
    }

    "must continue to notify other items when there is a failure" in {

      val item1 = randomItem
      val item2 = randomItem
      val item3 = randomItem

      when(mockSdesConnector.notify(any())(any()))
        .thenReturn(Future.successful(Done))
        .thenReturn(Future.failed(new RuntimeException()))
        .thenReturn(Future.successful(Done))

      repository.insert(item1).futureValue
      repository.insert(item2).futureValue
      repository.insert(item3).futureValue

      service.notifySubmittedItems().futureValue

      verify(mockSdesConnector, times(3)).notify(any())(any())

      repository.get(item1.id).futureValue.value.status mustEqual SubmissionItemStatus.Forwarded
      repository.get(item2.id).futureValue.value.status mustEqual SubmissionItemStatus.Submitted
      repository.get(item3.id).futureValue.value.status mustEqual SubmissionItemStatus.Forwarded
    }
  }
}
