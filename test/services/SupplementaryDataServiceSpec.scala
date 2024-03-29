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

import better.files.File
import connectors.SdesConnector
import models.Done
import models.dmsa.{Metadata, ObjectSummary, SubmissionItem, SubmissionItemStatus}
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.SubmissionItemRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Md5Hash, ObjectSummaryWithMd5, Path}

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.Future

class SupplementaryDataServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar with BeforeAndAfterEach with ScalaFutures {

  private val clock = Clock.fixed(Instant.now, ZoneOffset.UTC)
  private val mockSdesConnector = mock[SdesConnector]
  private val mockObjectStoreClient = mock[PlayObjectStoreClient]
  private val mockSubmissionItemRepository = mock[SubmissionItemRepository]

  override def beforeEach(): Unit = {
    Mockito.reset[Any](
      mockSdesConnector,
      mockObjectStoreClient,
      mockSubmissionItemRepository
    )
  }

  private val app = GuiceApplicationBuilder()
    .configure(
      "services.sdes.information-type" -> "information-type",
      "services.sdes.recipient-or-sender" -> "recipient-or-sender",
      "services.sdes.object-store-location-prefix" -> "http://prefix/",
    )
    .overrides(
      bind[Clock].toInstance(clock),
      bind[SdesConnector].toInstance(mockSdesConnector),
      bind[PlayObjectStoreClient].toInstance(mockObjectStoreClient),
      bind[SubmissionItemRepository].toInstance(mockSubmissionItemRepository)
    ).build()

  private val service = app.injector.instanceOf[SupplementaryDataService]

  "submitSupplementaryData" - {

    val file = File
      .newTemporaryFile()
      .deleteOnExit()
    file.writeText("barfoo")

    val metadata = Metadata("foobar", clock.instant(), "correlationId")

    val hc = HeaderCarrier()

    val objectSummaryWithMd5 = ObjectSummaryWithMd5(
      location = Path.File("sdes/some_id.pdf"),
      contentLength = 1337L,
      contentMd5 = Md5Hash("hash"),
      lastModified = clock.instant().minus(2, ChronoUnit.DAYS)
    )

    val expectedSubmissionItem = SubmissionItem(
      id = "correlationId",
      status = SubmissionItemStatus.Submitted,
      objectSummary = ObjectSummary(
        location = "sdes/some_id.pdf",
        contentLength = 1337,
        contentMd5 = "hash",
        lastModified = clock.instant().minus(2, ChronoUnit.DAYS)
      ),
      failureReason = None,
      metadata = Metadata("foobar", clock.instant(), "correlationId"),
      created = clock.instant(),
      lastUpdated = clock.instant(),
      sdesCorrelationId = "correlationId"
    )

    "must store the file in object store store the submission item in mongo" in {

      val submissionItemCaptor: ArgumentCaptor[SubmissionItem] =
        ArgumentCaptor.forClass(classOf[SubmissionItem])

      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(objectSummaryWithMd5))
      when(mockSubmissionItemRepository.insert(any())).thenReturn(Future.successful(Done))

      val id = service.submitSupplementaryData(file.toJava, metadata)(hc).futureValue

      verify(mockObjectStoreClient).putObject(any(), eqTo(file.path.toFile), any(), any(), any(), any())(any(), any())
      verify(mockSubmissionItemRepository).insert(submissionItemCaptor.capture())

      val actualSubmissionItem = submissionItemCaptor.getValue

      actualSubmissionItem mustEqual expectedSubmissionItem.copy(id = id)
    }

    "must fail when object store fails" in {

      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.failed(new RuntimeException()))
      when(mockSubmissionItemRepository.insert(any())).thenReturn(Future.successful(Done))

      service.submitSupplementaryData(file.toJava, metadata)(hc).failed.futureValue

      verify(mockSubmissionItemRepository, times(0)).insert(any())
    }

    "must fail when submission item repository fails" in {

      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(objectSummaryWithMd5))
      when(mockSubmissionItemRepository.insert(any())).thenReturn(Future.failed(new RuntimeException()))

      service.submitSupplementaryData(file.toJava, metadata)(hc).failed.futureValue
    }
  }
}
