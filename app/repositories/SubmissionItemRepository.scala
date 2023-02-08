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

package repositories

import models.Done
import models.submission.{SubmissionItem, SubmissionItemStatus}
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import play.api.Configuration
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionItemRepository @Inject() (
                                           mongoComponent: MongoComponent,
                                           clock: Clock,
                                           configuration: Configuration
                                         )(implicit ec: ExecutionContext
                                         ) extends PlayMongoRepository[SubmissionItem](
    collectionName = "submissions",
    mongoComponent = mongoComponent,
    domainFormat = SubmissionItem.format,
    indexes = Seq(
      IndexModel(
        Indexes.ascending("created"),
        IndexOptions()
          .name("createdIdx")
          .expireAfter(30, TimeUnit.DAYS)
      ),
      IndexModel(
        Indexes.ascending("id"),
        IndexOptions()
          .name("idIdx")
          .unique(true)
      ),
      IndexModel(
        Indexes.ascending("sdesCorrelationId"),
        IndexOptions()
          .name("sdesCorrelationIdIdx")
          .unique(true)
      ),
      IndexModel(
        Indexes.ascending("status"),
        IndexOptions()
          .name("statusIdx")
      )
    ),
    extraCodecs =
      Codecs.playFormatSumCodecs(SubmissionItemStatus.format)
  ) {

  def insert(item: SubmissionItem): Future[Done] =
    collection.insertOne(item.copy(lastUpdated = clock.instant()))
      .toFuture()
      .map(_ => Done)

  def get(id: String): Future[Option[SubmissionItem]] =
    collection.find(Filters.equal("id", id)).headOption()
}
