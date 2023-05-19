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
import models.{Done, RecentClaim}
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RecentClaimRepository @Inject()(
                                       mongoComponent: MongoComponent,
                                       appConfig: AppConfig
                                     )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[RecentClaim](
    collectionName = "recent-claims",
    mongoComponent = mongoComponent,
    domainFormat   = RecentClaim.mongoFormat,
    indexes        = Seq(
      IndexModel(
        Indexes.ascending("created"),
        IndexOptions()
          .name("created-index")
          .expireAfter(appConfig.recentClaimsTtlInDays, TimeUnit.DAYS)
      ),
      IndexModel(
        Indexes.ascending("nino"),
        IndexOptions()
          .name("nino-index")
          .unique(true)
      )
    )
  ) {

  def get(nino: String): Future[Option[RecentClaim]] =
    collection
      .find(Filters.equal("nino", nino))
      .headOption()

  def set(recentClaim: RecentClaim): Future[Done] =
    collection
      .insertOne(recentClaim)
      .toFuture()
      .map(_ => Done)
}
