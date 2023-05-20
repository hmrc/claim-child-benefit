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

import models.{CrnTraceCacheItem, CrnTraceRequest, Done, RelationshipDetails, RelationshipDetailsCacheItem, Relationships}
import org.mockito.MockitoSugar
import org.mongodb.scala.model.Filters
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import utils.NinoGenerator

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, LocalDate, ZoneId}

class CrnTraceCacheRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[CrnTraceCacheItem]
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with MockitoSugar {

  private val instant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

  private val app = GuiceApplicationBuilder()
    .overrides(
      bind[MongoComponent].toInstance(mongoComponent),
      bind[Clock].toInstance(stubClock)
    )
    .configure(
      "mongodb.crnTraceTtlInSeconds" -> 1
    )
    .build()

  protected override val repository: CrnTraceCacheRepository =
    app.injector.instanceOf[CrnTraceCacheRepository]

  ".set" - {

    "must save the request and result, setting the timestamp to `now`" in {

      val dateOfBirth = LocalDate.of(2000, 1, 2)
      val request = CrnTraceRequest("first", "last", dateOfBirth)
      val exists = true
      val expectedItem = CrnTraceCacheItem("first", "last", dateOfBirth, exists, instant)

      repository.set(request, exists).futureValue
      val insertedRecord = findAll().futureValue.head

      insertedRecord mustEqual expectedItem
    }
  }

  ".get" - {

    "when there is a record for this request" - {

      "must return the record" in {

        val dateOfBirth1 = LocalDate.of(2000, 1, 2)
        val dateOfBirth2 = LocalDate.of(2000, 1, 3)
        val request = CrnTraceRequest("first", "last", dateOfBirth1)
        val exists = true
        val item1 = CrnTraceCacheItem("first", "last", dateOfBirth1, exists, instant)
        val item2 = CrnTraceCacheItem("first", "last", dateOfBirth2, exists, instant)
        val item3 = CrnTraceCacheItem("other first", "last", dateOfBirth2, exists, instant)
        val item4 = CrnTraceCacheItem("first", "other last", dateOfBirth2, exists, instant)

        insert(item1).futureValue
        insert(item2).futureValue
        insert(item3).futureValue
        insert(item4).futureValue

        val result = repository.get(request).futureValue

        result.value mustEqual item1
      }
    }

    "when there is not a record for this request" - {

      "must return None" in {

        val dateOfBirth1 = LocalDate.of(2000, 1, 2)
        val dateOfBirth2 = LocalDate.of(2000, 1, 3)
        val request = CrnTraceRequest("first", "last", dateOfBirth1)
        val exists = true
        val item1 = CrnTraceCacheItem("first", "last", dateOfBirth2, exists, instant)
        val item2 = CrnTraceCacheItem("other first", "last", dateOfBirth2, exists, instant)
        val item3 = CrnTraceCacheItem("first", "other last", dateOfBirth2, exists, instant)

        insert(item1).futureValue
        insert(item2).futureValue
        insert(item3).futureValue

        val result = repository.get(request).futureValue

        result must not be defined
      }
    }
  }
}
