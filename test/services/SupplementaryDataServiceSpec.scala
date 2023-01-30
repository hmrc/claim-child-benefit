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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import better.files.File
import connectors.SdesConnector
import models.Done
import models.dmsa.Metadata
import models.sdes.{FileAudit, FileChecksum, FileMetadata, FileNotifyRequest, FileProperty}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.verify
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.{HeaderCarrier, RequestId}
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Md5Hash, ObjectSummaryWithMd5, Path}

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.Future

class SupplementaryDataServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar with BeforeAndAfterEach with ScalaFutures {

  private val clock = Clock.fixed(Instant.now, ZoneOffset.UTC)
  private val mockSdesConnector = mock[SdesConnector]
  private val mockObjectStoreClient = mock[PlayObjectStoreClient]

  override def beforeEach(): Unit = {
    Mockito.reset(
      mockSdesConnector,
      mockObjectStoreClient
    )
  }

  private val app = GuiceApplicationBuilder()
    .configure(
      "services.sdes.information-type" -> "information-type",
      "services.sdes.recipient-or-sender" -> "recipient-or-sender",
      "services.sdes.object-store-location-prefix" -> "http://prefix/",
    )
    .overrides(
      bind[SdesConnector].toInstance(mockSdesConnector),
      bind[PlayObjectStoreClient].toInstance(mockObjectStoreClient)
    ).build()

  private val service = app.injector.instanceOf[SupplementaryDataService]

  "submitSupplementaryData" - {

    val file = File
      .newTemporaryFile()
      .deleteOnExit()
    file.writeText("barfoo")

    val metadata = Metadata("foobar")

    val hc = HeaderCarrier(requestId = Some(RequestId("requestId")))

    val objectSummaryWithMd5 = ObjectSummaryWithMd5(
      location = Path.File("sdes/requestId.pdf"),
      contentLength = 1337L,
      contentMd5 = Md5Hash("hash"),
      lastModified = clock.instant().minus(2, ChronoUnit.DAYS)
    )

    val expectedRequest = FileNotifyRequest(
      "information-type",
      FileMetadata(
        recipientOrSender = "recipient-or-sender",
        name = "requestId.pdf",
        location = s"http://prefix/${Path.Directory("sdes").file("requestId.pdf").asUri}",
        checksum = FileChecksum("md5", value = "85ab21"),
        size = 1337,
        properties = List(
          FileProperty("nino", "foobar")
        )
      ),
      FileAudit("requestId")
    )

    "must store the file in object store and notify SDES" in {

      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(objectSummaryWithMd5))
      when(mockSdesConnector.notify(any())(any())).thenReturn(Future.successful(Done))

      service.submitSupplementaryData(file.toJava, metadata)(hc).futureValue

      verify(mockObjectStoreClient).putObject(eqTo(Path.File("sdes/requestId.pdf")), eqTo(file.path.toFile), any(), any(), any(), any())(any(), any())
      verify(mockSdesConnector).notify(expectedRequest)(hc)
    }

    "must fail when object store fails" in {

      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.failed(new RuntimeException()))
      when(mockSdesConnector.notify(any())(any())).thenReturn(Future.successful(Done))

      service.submitSupplementaryData(file.toJava, metadata)(hc).failed.futureValue
    }

    "must fail when SDES notification fails" in {

      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(objectSummaryWithMd5))
      when(mockSdesConnector.notify(any())(any())).thenReturn(Future.failed(new RuntimeException()))

      service.submitSupplementaryData(file.toJava, metadata)(hc).failed.futureValue
    }
  }
}
