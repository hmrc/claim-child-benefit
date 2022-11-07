/*
 * Copyright 2022 HM Revenue & Customs
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
import models.{Done, UserData}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes, ReplaceOptions, Updates}
import play.api.libs.json.Format
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UserDataRepository @Inject()(
                                    mongoComponent: MongoComponent,
                                    appConfig: AppConfig,
                                    clock: Clock
                                  )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[UserData](
    collectionName = "user-data",
    mongoComponent = mongoComponent,
    domainFormat   = UserData.format,
    indexes        = Seq(
      IndexModel(
        Indexes.ascending("lastUpdated"),
        IndexOptions()
          .name("last-updated-index")
          .expireAfter(appConfig.userDataTtlInDays, TimeUnit.DAYS)
      )
    )
  ) {

  implicit val instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  private def byId(id: String): Bson = Filters.equal("_id", id)

  def keepAlive(id: String): Future[Done] =
    collection
      .updateOne(
        filter = byId(id),
        update = Updates.set("lastUpdated", Instant.now(clock)),
      )
      .toFuture
      .map(_ => Done)

  def get(id: String): Future[Option[UserData]] =
    keepAlive(id).flatMap {
      _ =>
        collection
          .find(byId(id))
          .headOption
    }

  def set(userData: UserData): Future[Done] = {

    val updatedUserData = userData copy (lastUpdated = Instant.now(clock))

    collection
      .replaceOne(
        filter = byId(updatedUserData.id),
        replacement = updatedUserData,
        options = ReplaceOptions().upsert(true)
      )
      .toFuture
      .map(_ => Done)
  }
}
