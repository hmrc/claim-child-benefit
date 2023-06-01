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
import models.dmsa.{DailySummary, ListResult, QueryResult, SubmissionItem, SubmissionItemStatus}
import org.bson.conversions.Bson
import org.mongodb.scala.model.{Aggregates, Facet, Filters, FindOneAndUpdateOptions, IndexModel, IndexOptions, Indexes, ReturnDocument, Sorts, Updates}
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.JsonOps
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.{Clock, Duration, LocalDate, ZoneOffset}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.annotation.nowarn
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
      Codecs.playFormatSumCodecs(SubmissionItemStatus.format) ++
        Seq(
          Codecs.playFormatCodec(DailySummary.mongoFormat),
          Codecs.playFormatCodec(ListResult.format)
        )
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

  def dailySummaries: Future[Seq[DailySummary]] = {

    import SubmissionItemStatus._

    @nowarn("msg=possible missing interpolator")
    def countStatus(status: SubmissionItemStatus): JsObject = Json.obj(
      "$sum" -> Json.obj(
        "$cond" -> Json.obj(
          "if" -> Json.obj(
            "$eq" -> Json.arr("$status", status)
          ),
          "then" -> 1,
          "else" -> 0
        )
      )
    )

    val groupExpression = Json.obj(
      "$group" -> Json.obj(
        "_id" -> Json.obj(
          "$dateToString" -> Json.obj(
            "format" -> "%Y-%m-%d",
            "date" -> "$created"
          )
        ),
        Submitted.toString.toLowerCase -> countStatus(Submitted),
        Forwarded.toString.toLowerCase -> countStatus(Forwarded),
        Failed.toString.toLowerCase -> countStatus(Failed),
        Completed.toString.toLowerCase -> countStatus(Completed)
      )
    )

    collection.aggregate[DailySummary](List(
      groupExpression.toDocument()
    )).toFuture()
  }

  def list(
            status: Option[SubmissionItemStatus] = None,
            created: Option[LocalDate] = None,
            limit: Int = 50,
            offset: Int = 0
          ): Future[ListResult] = {

    val statusFilter = status.toList.map(Filters.equal("status", _))
    val createdFilter = created.toList.flatMap { date =>
      List(
        Filters.gte("created", date.atStartOfDay(ZoneOffset.UTC).toInstant),
        Filters.lt("created", date.atStartOfDay(ZoneOffset.UTC).plusDays(1).toInstant)
      )
    }
    val filters = Filters.and(List(List(Filters.empty()), statusFilter, createdFilter).flatten: _*)

    val findCount = Json.obj(
      "$let" -> Json.obj(
        "vars" -> Json.obj(
          "countValue" -> Json.obj(
            "$arrayElemAt" -> Json.arr("$totalCount", 0)
          )
        ),
        "in" -> "$$countValue.count"
      )
    )

    collection.aggregate[ListResult](List(
      Aggregates.`match`(filters),
      Aggregates.sort(Sorts.descending("created")),
      Aggregates.facet(
        Facet("totalCount", Aggregates.count()),
        Facet("summaries", Aggregates.skip(offset), Aggregates.limit(limit))
      ),
      Aggregates.project(Json.obj(
        "totalCount" -> findCount,
        "summaries" -> "$summaries"
      ).toDocument())
    )).head()
  }

  def countByStatus(status: SubmissionItemStatus): Future[Long] =
    collection.countDocuments(Filters.equal("status", status)).toFuture()

  def retry(id: String): Future[Done] = {
    collection.findOneAndUpdate(
      filter = Filters.equal("id", id),
      update = Updates.combine(
        Updates.inc("retries", 1),
        Updates.set("status", SubmissionItemStatus.Submitted),
        Updates.set("lastUpdated", clock.instant())
      ),
      options = FindOneAndUpdateOptions().upsert(false)
    ).headOption().flatMap {
      _.map(_ => Future.successful(Done))
        .getOrElse(Future.failed(SubmissionItemRepository.NothingToUpdateException))
    }
  }
}

object SubmissionItemRepository {

  case object NothingToUpdateException extends Exception {
    override def getMessage: String = "Unable to find submission item"
  }
}
