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
import models.dmsa.SubmissionItem
import models.sdes.NotificationCallback
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class AuditService @Inject() (
                               auditConnector: AuditConnector,
                               configuration: Configuration
                             )(implicit ec: ExecutionContext) {

  private val supplementaryDataSubmittedEventName: String = configuration.get[String]("auditing.supplementary-data-submitted-event-name")
  private val supplementaryDataResultEventName: String = configuration.get[String]("auditing.supplementary-data-result-event-name")

  def auditSupplementaryDataSubmitted(submissionItem: SubmissionItem)(implicit hc: HeaderCarrier): Unit = {

    val event = SupplementaryDataSubmittedEvent(
      nino = submissionItem.metadata.nino.decryptedValue,
      correlationId = submissionItem.metadata.correlationId,
      fileName = s"${submissionItem.id}.pdf",
      hash = submissionItem.objectSummary.contentMd5
    )

    auditConnector.sendExplicitAudit(supplementaryDataSubmittedEventName, event)
  }

  def auditSupplementaryDataResult(notification: NotificationCallback, submissionItem: SubmissionItem)(implicit hc: HeaderCarrier): Unit = {

    val event = SupplementaryDataResultEvent(
      nino = submissionItem.metadata.nino.decryptedValue,
      correlationId = submissionItem.metadata.correlationId,
      fileName = s"${submissionItem.id}.pdf",
      hash = submissionItem.objectSummary.contentMd5,
      status = notification.notification,
      failureReason = notification.failureReason
    )

    auditConnector.sendExplicitAudit(supplementaryDataResultEventName, event)
  }
}
