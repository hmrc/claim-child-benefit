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

import models.dmsa.{Metadata, ObjectSummary, SubmissionItem, SubmissionItemStatus}
import repositories.SubmissionItemRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.play.Implicits._
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{ObjectSummaryWithMd5, Path}

import java.io.File
import java.time.Clock
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SupplementaryDataService @Inject() (
                                           submissionItemRepository: SubmissionItemRepository,
                                           objectStoreClient: PlayObjectStoreClient,
                                           clock: Clock
                                         )(implicit ec: ExecutionContext) {

  def submitSupplementaryData(pdf: File, metadata: Metadata)(implicit hc: HeaderCarrier): Future[String] = {
    val id = hc.requestId.map(_.value).getOrElse(UUID.randomUUID().toString)
    for {
      objectSummary <- objectStoreClient.putObject(Path.Directory("sdes").file(s"$id.pdf"), pdf)
      _             <- submissionItemRepository.insert(createSubmissionItem(objectSummary, id, metadata))
    } yield id
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
      sdesCorrelationId = metadata.correlationId
    )
}
