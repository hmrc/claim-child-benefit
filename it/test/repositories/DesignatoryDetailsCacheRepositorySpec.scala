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
import models.{DesignatoryDetails, DesignatoryDetailsCacheItem, Name}
import org.apache.pekko.Done
import org.mockito.Mockito.when
import org.mongodb.scala.model.Filters
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import utils.NinoGenerator

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, LocalDate, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global

class DesignatoryDetailsCacheRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[DesignatoryDetailsCacheItem]
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with MockitoSugar {

  private val instant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

  private val mockAppConfig = mock[AppConfig]
  when(mockAppConfig.designatoryDetailsTtlInSeconds) thenReturn 1

  protected override val repository = new DesignatoryDetailsCacheRepository(
    mongoComponent = mongoComponent,
    appConfig = mockAppConfig,
    clock = stubClock
  )

  ".set" - {

    "must save the item, setting the timestap to `now`" in {

      val details = DesignatoryDetails(LocalDate.of(2020, 2, 1), None, None, None, None)
      val nino = NinoGenerator.randomNino()
      val expectedRecord = DesignatoryDetailsCacheItem(nino, details, instant)

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
        val details1 = DesignatoryDetails(LocalDate.of(2020, 2, 1), Some(Name(None, Some("first 1"), None, Some("last 1"))), None, None, None)
        val details2 = DesignatoryDetails(LocalDate.of(2020, 2, 1), Some(Name(None, Some("first 2"), None, Some("last 2"))), None, None, None)

        val item1 = DesignatoryDetailsCacheItem(nino1, details1, instant)
        val item2 = DesignatoryDetailsCacheItem(nino2, details2, instant)

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
