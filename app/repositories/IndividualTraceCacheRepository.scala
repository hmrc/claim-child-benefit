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

import config.AppConfig
import models.{IndividualTraceCacheItem, IndividualTraceRequest, Done}
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes, ReplaceOptions}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IndividualTraceCacheRepository @Inject()(
                                         mongoComponent: MongoComponent,
                                         appConfig: AppConfig,
                                         clock: Clock
                                       )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[IndividualTraceCacheItem](
    collectionName = "individual-traces",
    mongoComponent = mongoComponent,
    domainFormat   = IndividualTraceCacheItem.format,
    indexes        = Seq(
      IndexModel(
        Indexes.ascending("forename", "surname", "dateOfBirth"),
        IndexOptions()
          .name("traceDetailsIdx")
          .unique(true)
      ),
      IndexModel(
        Indexes.ascending("timestamp"),
        IndexOptions()
          .name("timestampIdx")
          .expireAfter(appConfig.individualTraceTtlInSeconds, TimeUnit.SECONDS)
      )
    )
  ) {

  private def byRequest(request: IndividualTraceRequest) =
    Filters.and(
      Filters.equal("forename", request.forename),
      Filters.equal("surname", request.surname),
      Filters.equal("dateOfBirth", request.dateOfBirth)
    )

  def set(request: IndividualTraceRequest, exists: Boolean): Future[Done] = {

    val cacheItem = IndividualTraceCacheItem(
      forename = request.forename,
      surname = request.surname,
      dateOfBirth = request.dateOfBirth,
      exists = exists,
      timestamp = Instant.now(clock)
    )

    collection.replaceOne(
      filter = byRequest(request),
      replacement = cacheItem,
      options = ReplaceOptions().upsert(true)
    ).toFuture.map(_ => Done)
  }

  def get(request: IndividualTraceRequest): Future[Option[IndividualTraceCacheItem]] =
    collection.find(byRequest(request))
    .headOption
}