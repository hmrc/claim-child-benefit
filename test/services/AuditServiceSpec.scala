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

import models.audit.{SupplementaryDataResultEvent, SupplementaryDataSubmittedEvent}
import models.dmsa.{Metadata, ObjectSummary, SubmissionItem, SubmissionItemStatus}
import models.sdes.{NotificationCallback, NotificationType}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.Instant

class AuditServiceSpec extends AnyFreeSpec with Matchers with OptionValues with MockitoSugar with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    Mockito.reset(mockAuditConnector)
  }

  private val mockAuditConnector = mock[AuditConnector]

  private val app = GuiceApplicationBuilder()
    .configure(
      "auditing.supplementary-data-submitted-event-name" -> "supplementaryDataSubmitted",
      "auditing.supplementary-data-result-event-name" -> "supplementaryDataResult"
    )
    .overrides(
      bind[AuditConnector].toInstance(mockAuditConnector)
    )
    .build()

  private val service = app.injector.instanceOf[AuditService]

  private val hc: HeaderCarrier = HeaderCarrier()

  private val submissionItem = SubmissionItem(
    id = "id",
    status = SubmissionItemStatus.Submitted,
    objectSummary = ObjectSummary(
      location = "location",
      contentLength = 1337,
      contentMd5 = "md5",
      lastModified = Instant.now
    ),
    failureReason = None,
    metadata = Metadata(
      nino = "nino",
      submissionDate = Instant.now,
      correlationId = "correlationId"
    ),
    sdesCorrelationId = "sdesCorrelationId",
    created = Instant.now,
    lastUpdated = Instant.now,
    lockedAt = None
  )

  "auditSupplementaryDataSubmitted" - {

    "must call the audit connector with the correct payload" in {

      val event = SupplementaryDataSubmittedEvent(
        nino = "nino",
        correlationId = "correlationId",
        fileName = "id.pdf",
        hash = "md5"
      )

      service.auditSupplementaryDataSubmitted(submissionItem)(hc)

      verify(mockAuditConnector, times(1)).sendExplicitAudit(eqTo("supplementaryDataSubmitted"), eqTo(event))(eqTo(hc), any(), any())
    }
  }

  "auditSupplementaryDataResult" - {

    "must call the audit connector with the correct payload" in {

      val notification = NotificationCallback(
        notification = NotificationType.FileProcessingFailure,
        filename = "id.pdf",
        correlationID = "correlationId",
        failureReason = Some("foobar")
      )

      val event = SupplementaryDataResultEvent(
        nino = "nino",
        correlationId = "correlationId",
        fileName = "id.pdf",
        hash = "md5",
        status = NotificationType.FileProcessingFailure,
        failureReason = Some("foobar")
      )

      service.auditSupplementaryDataResult(notification, submissionItem)(hc)

      verify(mockAuditConnector, times(1)).sendExplicitAudit(eqTo("supplementaryDataResult"), eqTo(event))(eqTo(hc), any(), any())
    }
  }
}
