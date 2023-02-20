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
import models.{DesignatoryDetails, DesignatoryDetailsCacheItem, Done}
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DesignatoryDetailsCacheRepository @Inject()(
                                                   mongoComponent: MongoComponent,
                                                   appConfig: AppConfig,
                                                   clock: Clock
                                                 )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[DesignatoryDetailsCacheItem](
    collectionName = "designatory-details",
    mongoComponent = mongoComponent,
    domainFormat   = DesignatoryDetailsCacheItem.format,
    indexes = Seq(
      IndexModel(
        Indexes.ascending("timestamp"),
        IndexOptions()
          .name("timestampIdx")
          .expireAfter(appConfig.designatoryDetailsTtlInSeconds, TimeUnit.SECONDS)
      )
    )
  ) {

  def set(nino: String, details: DesignatoryDetails): Future[Done] = {

    val item = DesignatoryDetailsCacheItem(nino, details, Instant.now(clock))

    collection.replaceOne(
      filter = Filters.equal("_id", item.nino),
      replacement = item,
      options = ReplaceOptions().upsert(true)
    ).toFuture.map(_ => Done)
  }

  def get(nino: String): Future[Option[DesignatoryDetails]] =
    collection.find(Filters.equal("_id", nino))
      .headOption
      .map(_.map(_.details))
}
