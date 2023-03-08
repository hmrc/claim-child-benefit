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

import models.ThrottleData
import org.mockito.MockitoSugar
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global

class ThrottleRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[ThrottleData]
    with OptionValues
    with ScalaFutures
    with MockitoSugar {

  protected override def repository = new ThrottleRepository(mongoComponent)

  "on startup" - {

    "must insert a seed record when one does not already exist" in {

      val repo = repository
      repo.seed.futureValue

      findAll().futureValue must contain only ThrottleData(0, 0)
    }
  }

  ".seed" - {

    "must not fail when a record already exists" in {

      val repo = repository
      repo.seed.futureValue
      repo.seed.futureValue

      findAll().futureValue must contain only ThrottleData(0, 0)
    }
  }

  ".get" - {

    "must return the throttle data" in {

      val repo = repository
      repo.seed.futureValue

      repo.get.futureValue mustEqual ThrottleData(0, 0)
    }
  }

  ".updateLimit" - {

    "must update the limit to the new value and not change the count" in {

      val repo = repository
      repo.seed.futureValue

      repo.updateLimit(123).futureValue
      findAll().futureValue must contain only ThrottleData(0, 123)
    }
  }

  ".incrementCount" - {

    "must increment the count and not change the limit" in {

      val repo = repository
      repo.seed.futureValue

      repo.incrementCount.futureValue
      findAll().futureValue must contain only ThrottleData(1, 0)
      repo.incrementCount.futureValue
      findAll().futureValue must contain only ThrottleData(2, 0)
    }
  }
}
