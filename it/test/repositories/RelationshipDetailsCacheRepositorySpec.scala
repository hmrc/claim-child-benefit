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

import models.{RelationshipDetails, RelationshipDetailsCacheItem, Relationships}
import org.apache.pekko.Done
import org.mongodb.scala.model.Filters
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import utils.NinoGenerator

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}

class RelationshipDetailsCacheRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[RelationshipDetailsCacheItem]
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
      "mongodb.relationshipDetailsTtlInSeconds" -> 1
    )
    .build()

  protected override val repository: RelationshipDetailsCacheRepository =
    app.injector.instanceOf[RelationshipDetailsCacheRepository]

  ".set" - {

    "must save the item, setting the timestamp to `now`" in {

      val details = RelationshipDetails(Relationships(None))
      val nino = NinoGenerator.randomNino()
      val expectedRecord = RelationshipDetailsCacheItem(nino, details, instant)

      val result = repository.set(nino, details).futureValue
      val insertedRecord = find(Filters.equal("_id", nino)).futureValue.head

      result mustEqual Done
      insertedRecord mustEqual expectedRecord
    }
  }

  ".get" - {

    "when there is a record for this NINO" - {

      "must return the record" in {

        val nino1 = NinoGenerator.randomNino()
        val nino2 = NinoGenerator.randomNino()
        val details1 = RelationshipDetails(Relationships(None))
        val details2 = RelationshipDetails(Relationships(None))

        val item1 = RelationshipDetailsCacheItem(nino1, details1, instant)
        val item2 = RelationshipDetailsCacheItem(nino2, details2, instant)

        insert(item1).futureValue
        insert(item2).futureValue

        val result1 = repository.get(nino1).futureValue
        val result2 = repository.get(nino2).futureValue

        result1.value mustEqual details1
        result2.value mustEqual details2
      }
    }

    "when there is not a record for this NINO" - {

      "must return None" in {

        val nino = NinoGenerator.randomNino()

        val result = repository.get(nino).futureValue

        result must not be defined
      }
    }
  }
}
