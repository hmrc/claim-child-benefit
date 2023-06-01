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

import models.{Done, ThrottleData}
import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.model.{Filters, Updates}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.annotation.nowarn
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ThrottleRepository @Inject()(
                                    mongoComponent: MongoComponent
                                  )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[ThrottleData](
    collectionName = "throttle-data",
    mongoComponent = mongoComponent,
    domainFormat   = ThrottleData.format,
    indexes        = Nil
  ) {

  private val byId = Filters.equal("_id", ThrottleData.id)
  private val seedRecord = ThrottleData(0, 0)
  private val duplicateErrorCode = 11000

  @nowarn
  private val seedDatabase = seed // Eagerly call seed to make sure a record is inserted on startup if needed

  def seed: Future[Done] =
    collection
      .insertOne(seedRecord)
      .toFuture()
      .map(_ => Done)
      .recover {
        case e: MongoWriteException if e.getError.getCode == duplicateErrorCode => Done
      }

  def incrementCount: Future[Done] =
    collection
      .updateOne(byId, Updates.inc("count", 1))
      .toFuture()
      .map(_ => Done)

  def updateLimit(newLimit: Int): Future[Done] =
    collection
      .updateOne(byId, Updates.set("limit", newLimit))
      .toFuture()
      .map(_ => Done)

  def get: Future[ThrottleData] =
    collection
      .find(byId)
      .head()
}
