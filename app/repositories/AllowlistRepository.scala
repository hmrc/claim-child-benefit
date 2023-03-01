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

import models.{AllowlistEntry, Done}
import org.mongodb.scala.model.Filters
import org.mongodb.scala.result.{DeleteResult, InsertManyResult}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AllowlistRepository @Inject()(
                                     mongoComponent: MongoComponent,
                                   )(implicit ec: ExecutionContext, crypto: Encrypter with Decrypter)
  extends PlayMongoRepository[AllowlistEntry](
    collectionName = "allowlist",
    mongoComponent = mongoComponent,
    domainFormat   = AllowlistEntry.format,
    indexes        = Nil
  ) {

  def exists(entry: AllowlistEntry): Future[Boolean] =
    collection
      .find()
      .toFuture
      .map(_.contains(entry))

  def set(entry: AllowlistEntry): Future[Done] = {
    exists(entry).flatMap {
      case true =>
        Future.successful(Done)

      case false =>
        collection
          .insertOne(entry)
          .toFuture()
          .map(_ => Done)
    }
  }

  def delete(entry: AllowlistEntry): Future[Done] = {

    def entries: Future[Seq[AllowlistEntry]] =
    collection
      .find()
      .toFuture()

    def deleteEntries: Future[DeleteResult] =
      collection
      .deleteMany(Filters.empty())
        .toFuture()

    def insertEntries(i: Seq[AllowlistEntry]): Future[InsertManyResult] =
      collection
      .insertMany(i)
        .toFuture()

    for {
      i <- entries
      _ <- deleteEntries
      remainingEntries = i.filterNot(_ == entry)
      _ <- if (remainingEntries.nonEmpty) insertEntries(remainingEntries) else Future.unit
    } yield Done

  }
}