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
import models.UserData
import org.mockito.MockitoSugar
import org.mongodb.scala.model.Filters
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global

class UserDataRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[UserData]
    with MockitoSugar
    with OptionValues
    with ScalaFutures {

  private val instant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

  private val userData = UserData("id", Json.obj("foo" -> "bar"), Instant.ofEpochSecond(1))

  private val mockAppConfig = mock[AppConfig]
  when(mockAppConfig.userDataTtlInDays) thenReturn 1

  protected override val repository = new UserDataRepository(
    mongoComponent = mongoComponent,
    appConfig = mockAppConfig,
    clock = stubClock
  )

  ".set" - {

    "must set the last updated time on the supplied user answers to `now`, and save them" in {

      val expectedResult = userData copy (lastUpdated = instant)

      val setResult = repository.set(userData).futureValue
      val updatedRecord = find(Filters.equal("_id", userData.id)).futureValue.headOption.value

      setResult mustEqual true
      updatedRecord mustEqual expectedResult
    }
  }

  ".get" - {

    "when there is a record for this id" - {

      "must update the lastUpdated time and get the record" in {

        insert(userData).futureValue

        val result = repository.get(userData.id).futureValue
        val expectedResult = userData copy (lastUpdated = instant)

        result.value mustEqual expectedResult
      }
    }

    "when there is no record for this id" - {

      "must return None" in {

        repository.get("id that does not exist").futureValue must not be defined
      }
    }
  }
}
