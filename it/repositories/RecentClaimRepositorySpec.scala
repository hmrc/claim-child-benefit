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
import models.{RecentClaim, TaxChargeChoice}
import org.mockito.MockitoSugar
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import utils.NinoGenerator

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global

class RecentClaimRepositorySpec
  extends AnyFreeSpec
  with Matchers
  with DefaultPlayMongoRepositorySupport[RecentClaim]
  with MockitoSugar
  with OptionValues
  with ScalaFutures {


  private val mockAppConfig = mock[AppConfig]
  when(mockAppConfig.recentClaimsTtlInDays) thenReturn 1

  protected override val repository = new RecentClaimRepository(mongoComponent, mockAppConfig)

  private val nino = NinoGenerator.randomNino()
  private val instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  private val recentClaim = RecentClaim(nino, instant, TaxChargeChoice.OptedOut)

  ".get" - {

    "must return a recent claim when one exists for this nino" in {

      insert(recentClaim).futureValue

      val result = repository.get(nino).futureValue

      result.value mustEqual recentClaim
    }

    "must return None when a claim does not exist for this nino" in {

      val result = repository.get(nino).futureValue

      result must not be defined
    }
  }

  ".set" - {

    "must save a recent claim" in {

      repository.set(recentClaim).futureValue
      repository.get(nino).futureValue.value mustEqual recentClaim
    }
  }
}
