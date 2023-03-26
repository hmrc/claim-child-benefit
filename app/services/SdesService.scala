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
import models.Done
import models.dmsa.{QueryResult, SubmissionItem, SubmissionItemStatus}
import models.sdes._
import play.api.Configuration
import repositories.SubmissionItemRepository
import uk.gov.hmrc.http.HeaderCarrier

import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SdesService @Inject() (
                              connector: SdesConnector,
                              configuration: Configuration,
                              repository: SubmissionItemRepository,
                              auditService: AuditService
                            )(implicit ec: ExecutionContext) extends play.api.Logging {

  private val informationType: String = configuration.get[String]("services.sdes.information-type")
  private val recipientOrSender: String = configuration.get[String]("services.sdes.recipient-or-sender")
  private val objectStoreLocationPrefix: String = configuration.get[String]("services.sdes.object-store-location-prefix")

  private val submissionDateFormat: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

  def notifyOldestSubmittedItem(): Future[QueryResult] =
    repository.lockAndReplaceOldestItemByStatus(SubmissionItemStatus.Submitted) { item =>
      notify(item)(HeaderCarrier()).map { _ =>
        val newItem = item.copy(status = SubmissionItemStatus.Forwarded)
        auditService.auditSupplementaryDataSubmitted(newItem)(HeaderCarrier())
        newItem
      }
    }.recover { case e =>
      logger.error("Error notifying SDES about a submitted item", e)
      QueryResult.Found
    }

  def notifySubmittedItems(): Future[Done] =
    notifyOldestSubmittedItem().flatMap {
      case QueryResult.Found => notifySubmittedItems()
      case QueryResult.NotFound => Future.successful(Done)
    }

  private def notify(item: SubmissionItem)(implicit hc: HeaderCarrier): Future[Done] =
    connector.notify(createRequest(item))

  private def createRequest(item: SubmissionItem): FileNotifyRequest = {

    val submissionDate =
      submissionDateFormat.format(item.metadata.submissionDate)

    FileNotifyRequest(
      informationType = informationType,
      file = FileMetadata(
        recipientOrSender = recipientOrSender,
        name = s"${item.id}.pdf",
        location = s"$objectStoreLocationPrefix${item.objectSummary.location}",
        checksum = FileChecksum("md5", base64ToHex(item.objectSummary.contentMd5)),
        size = item.objectSummary.contentLength,
        properties = List(
          FileProperty("nino", item.metadata.nino.decryptedValue),
          FileProperty("mimeType", "application/pdf"),
          FileProperty("submissionDate", submissionDate),
          FileProperty("correlationId", item.metadata.correlationId)
        )
      ),
      audit = FileAudit(item.sdesCorrelationId)
    )
  }

  private def base64ToHex(string: String): String =
    Base64.getDecoder.decode(string).map("%02x".format(_)).mkString
}
