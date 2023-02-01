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
import models.dmsa.Metadata
import models.sdes.{FileAudit, FileChecksum, FileMetadata, FileNotifyRequest, FileProperty}
import models.submission.{ObjectSummary, SubmissionItem, SubmissionItemStatus}
import play.api.Configuration
import repositories.SubmissionItemRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.{ObjectSummaryWithMd5, Path}
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.play.Implicits._

import java.io.File
import java.time.Clock
import java.util.{Base64, UUID}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SupplementaryDataService @Inject() (
                                           submissionItemRepository: SubmissionItemRepository,
                                           objectStoreClient: PlayObjectStoreClient,
                                           clock: Clock
                                         )(implicit ec: ExecutionContext) {

//  private val informationType: String = configuration.get[String]("services.sdes.information-type")
//  private val recipientOrSender: String = configuration.get[String]("services.sdes.recipient-or-sender")
//  private val objectStoreLocationPrefix: String = configuration.get[String]("services.sdes.object-store-location-prefix")

  def submitSupplementaryData(pdf: File, metadata: Metadata)(implicit hc: HeaderCarrier): Future[Done] = {
    val filename = hc.requestId.map(_.value).getOrElse(UUID.randomUUID().toString)
    for {
      objectSummary <- objectStoreClient.putObject(Path.Directory("sdes").file(s"$filename.pdf"), pdf)
      _             <- submissionItemRepository.insert(createSubmissionItem(objectSummary, filename, metadata))
    } yield Done
  }

  private def createSubmissionItem(objectSummary: ObjectSummaryWithMd5, id: String, metadata: Metadata): SubmissionItem =
    SubmissionItem(
      id = id,
      status = SubmissionItemStatus.Submitted,
      objectSummary = ObjectSummary(
        location = objectSummary.location.asUri,
        contentLength = objectSummary.contentLength,
        contentMd5 = objectSummary.contentMd5.value,
        lastModified = objectSummary.lastModified
      ),
      failureReason = None,
      metadata = metadata,
      created = clock.instant(),
      lastUpdated = clock.instant(),
      sdesCorrelationId = UUID.randomUUID().toString
    )

  // TODO move to SDES Service
//  private def fileNotifyRequest(filename: String, objectSummary: ObjectSummaryWithMd5, metadata: Metadata)(implicit hc: HeaderCarrier): FileNotifyRequest =
//    FileNotifyRequest(
//      informationType = informationType,
//      file = FileMetadata(
//        recipientOrSender = recipientOrSender,
//        name = objectSummary.location.fileName,
//        location = s"$objectStoreLocationPrefix${objectSummary.location.asUri}",
//        checksum = FileChecksum("md5", base64ToHex(objectSummary.contentMd5.value)),
//        size = objectSummary.contentLength,
//        properties = List(
//          FileProperty("nino", metadata.nino)
//        )
//      ),
//      audit = FileAudit(filename)
//    )
//
//  private def base64ToHex(string: String): String =
//    Base64.getDecoder.decode(string).map("%02x".format(_)).mkString
}
