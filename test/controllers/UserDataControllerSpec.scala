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

package controllers

import models.UserData
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.UserDataRepository
import uk.gov.hmrc.http.HeaderNames

import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.Future

class UserDataControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with BeforeAndAfterEach {

  private val mockRepo = mock[UserDataRepository]

  override def beforeEach(): Unit = {
    reset(mockRepo)
    super.beforeEach()
  }

  private val app = new GuiceApplicationBuilder().overrides(bind[UserDataRepository].toInstance(mockRepo)).build()

  ".get" - {

    "must return OK and the data when user data can be found for this session id" in {

      val clock    = Clock.fixed(Instant.now, ZoneId.systemDefault())
      val userId   = "foo"
      val userData = UserData(userId, Json.obj(), Instant.now(clock))

      when(mockRepo.get(eqTo(userId))) thenReturn Future.successful(Some(userData))

      val request =
        FakeRequest(GET, routes.UserDataController.get.url)
          .withHeaders(HeaderNames.xSessionId -> userId)

      val result = route(app, request).value

      status(result) mustEqual OK
      contentAsJson(result) mustEqual Json.toJson(userData)
    }

    "must return Not Found when user data cannot be found for this session id" in {

      when(mockRepo.get(any())) thenReturn Future.successful(None)

      val request =
        FakeRequest(GET, routes.UserDataController.get.url)
          .withHeaders(HeaderNames.xSessionId -> "foo")

      val result = route(app, request).value
      
      status(result) mustEqual NOT_FOUND
    }

    "must return Bad Request when the request does not have a session id" in {

      when(mockRepo.get(any())) thenReturn Future.successful(None)

      val request = FakeRequest(GET, routes.UserDataController.get.url)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
    }
  }
}
