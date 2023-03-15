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

package controllers

import models.{CheckLimitResponse, Done, ThrottleData}
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.ThrottleRepository

import scala.concurrent.Future

class ThrottleControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with OptionValues
    with BeforeAndAfterEach {

  private val mockThrottleRepository = mock[ThrottleRepository]

  override def beforeEach(): Unit = {
    Mockito.reset(mockThrottleRepository)
    super.beforeEach()
  }

  private val app =
    GuiceApplicationBuilder()
      .overrides(bind[ThrottleRepository].toInstance(mockThrottleRepository))
      .build()

  ".checkLimit" - {

    "must return OK and whether the limit has been reached" in {

      val throttleData = ThrottleData(0, 0)
      when(mockThrottleRepository.get).thenReturn(Future.successful(throttleData))

      val request = FakeRequest(routes.ThrottleController.checkLimit)
      val result = route(app, request).value

      status(result) mustEqual OK
      contentAsJson(result) mustEqual Json.toJson(CheckLimitResponse(throttleData.limitReached))
    }
  }

  ".incrementCount" - {

    "must increment the count and return OK" in {

      when(mockThrottleRepository.incrementCount).thenReturn(Future.successful(Done))

      val request = FakeRequest(routes.ThrottleController.incrementCount)
      val result = route(app, request).value

      status(result) mustEqual OK
      verify(mockThrottleRepository, times(1)).incrementCount
    }
  }
}