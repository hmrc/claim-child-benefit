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
import models.dmsa.{QueryResult, SubmissionItem, SubmissionItemStatus}
import org.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, FindOneAndUpdateOptions, IndexModel, IndexOptions, Indexes, ReturnDocument, Sorts, Updates}
import play.api.Configuration
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.{Clock, Duration}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionItemRepository @Inject() (
                                           mongoComponent: MongoComponent,
                                           clock: Clock,
                                           configuration: Configuration
                                         )(implicit ec: ExecutionContext, crypto: Encrypter with Decrypter) extends PlayMongoRepository[SubmissionItem](
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

  private val lockTtl: Duration = Duration.ofSeconds(configuration.get[Int]("lock-ttl"))

  def insert(item: SubmissionItem): Future[Done] =
    collection.insertOne(item.copy(lastUpdated = clock.instant()))
      .toFuture()
      .map(_ => Done)

  def get(id: String): Future[Option[SubmissionItem]] =
    collection.find(Filters.equal("id", id)).headOption()

  def getByCorrelationId(id: String): Future[Option[SubmissionItem]] =
    collection.find(Filters.equal("sdesCorrelationId", id)).headOption()

  def update(id: String, status: SubmissionItemStatus, failureReason: Option[String]): Future[SubmissionItem] = {

    val filter = Filters.equal("id", id)

    val updates = List(
      Updates.set("lastUpdated", clock.instant()),
      Updates.set("status", status),
      failureReason.map(Updates.set("failureReason", _))
        .getOrElse(Updates.unset("failureReason"))
    )

    collection.findOneAndUpdate(
      filter = filter,
      update = Updates.combine(updates: _*),
      options = FindOneAndUpdateOptions()
        .returnDocument(ReturnDocument.AFTER)
        .upsert(false)
    ).headOption().flatMap {
      _.map(Future.successful)
        .getOrElse(Future.failed(SubmissionItemRepository.NothingToUpdateException))
    }
  }

  def lockAndReplaceOldestItemByStatus(status: SubmissionItemStatus)(f: SubmissionItem => Future[SubmissionItem]): Future[QueryResult] =
    lockAndReplace(
      filter = Filters.equal("status", status),
      sort = Sorts.ascending("lastUpdated")
    )(f)

  private def lockAndReplace(filter: Bson, sort: Bson)(f: SubmissionItem => Future[SubmissionItem]): Future[QueryResult] =
    collection.findOneAndUpdate(
      filter = Filters.and(
        filter,
        Filters.or(
          Filters.exists("lockedAt", exists = false),
          Filters.lt("lockedAt", clock.instant().minus(lockTtl))
        )
      ),
      update = Updates.set("lockedAt", clock.instant()),
      options = FindOneAndUpdateOptions().sort(sort)
    ).headOption().flatMap {
      _.map { item =>
        f(item)
          .flatMap { updatedItem =>
            collection.replaceOne(
              filter = Filters.equal("sdesCorrelationId", item.sdesCorrelationId),
              replacement = updatedItem.copy(
                lastUpdated = clock.instant(),
                lockedAt = None
              )
            ).toFuture()
          }
          .map(_ => QueryResult.Found)
      }.getOrElse(Future.successful(QueryResult.NotFound))
    }
}

object SubmissionItemRepository {

  case object NothingToUpdateException extends Exception {
    override def getMessage: String = "Unable to find submission item"
  }
}
