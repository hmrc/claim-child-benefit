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
import org.mockito.ArgumentMatchers.any
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers._
import repositories.ThrottleRepository
import uk.gov.hmrc.internalauth.client.{BackendAuthComponents, IAAction, Resource, ResourceLocation, ResourceType, Retrieval}
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ThrottleControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with OptionValues
    with BeforeAndAfterEach {

  private val mockThrottleRepository = mock[ThrottleRepository]

  private val mockStubBehaviour = mock[StubBehaviour]
  private val stubBackendAuthComponents =
    BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), implicitly)

  override def beforeEach(): Unit = {
    Mockito.reset(mockThrottleRepository)
    super.beforeEach()
  }

  private val app =
    GuiceApplicationBuilder()
      .overrides(
        bind[ThrottleRepository].toInstance(mockThrottleRepository),
        bind[BackendAuthComponents].toInstance(stubBackendAuthComponents)
      )
      .build()

  ".checkLimit" - {

    val predicate = Permission(Resource(ResourceType("claim-child-benefit"), ResourceLocation("throttle")), IAAction("READ"))

    "must return OK and whether the limit has been reached" in {

      val throttleData = ThrottleData(0, 0)
      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.unit)
      when(mockThrottleRepository.get).thenReturn(Future.successful(throttleData))

      val request = FakeRequest(routes.ThrottleController.checkLimit)
        .withHeaders("Authorization" -> "Token foo")
      val result = route(app, request).value

      status(result) mustEqual OK
      contentAsJson(result) mustEqual Json.toJson(CheckLimitResponse(throttleData.limitReached))

      verify(mockStubBehaviour).stubAuth(Some(predicate), Retrieval.EmptyRetrieval)
    }

    "must return unauthorised for an unauthenticated user" in {

      val request = FakeRequest(routes.ThrottleController.checkLimit) // No Authorization header

      route(app, request).value.failed.futureValue
      verify(mockThrottleRepository, never).get
    }

    "must return unauthorised for an unauthorised user" in {

      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.failed(new RuntimeException()))

      val request = FakeRequest(routes.ThrottleController.checkLimit)
        .withHeaders("Authorization" -> "Token foo")

      route(app, request).value.failed.futureValue
      verify(mockThrottleRepository, never).get
    }
  }

  ".incrementCount" - {

    val predicate = Permission(Resource(ResourceType("claim-child-benefit"), ResourceLocation("throttle")), IAAction("WRITE"))

    "must increment the count and return OK" in {

      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.unit)
      when(mockThrottleRepository.incrementCount).thenReturn(Future.successful(Done))

      val request = FakeRequest(routes.ThrottleController.incrementCount)
        .withHeaders("Authorization" -> "Token foo")
      val result = route(app, request).value

      status(result) mustEqual OK
      verify(mockThrottleRepository, times(1)).incrementCount
      verify(mockStubBehaviour).stubAuth(Some(predicate), Retrieval.EmptyRetrieval)
    }

    "must return unauthorised for an unauthenticated user" in {

      val request = FakeRequest(routes.ThrottleController.incrementCount) // No Authorization header

      route(app, request).value.failed.futureValue
      verify(mockThrottleRepository, never).incrementCount
    }

    "must return unauthorised for an unauthorised user" in {

      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.failed(new RuntimeException()))

      val request = FakeRequest(routes.ThrottleController.incrementCount)
        .withHeaders("Authorization" -> "Token foo")

      route(app, request).value.failed.futureValue
      verify(mockThrottleRepository, never).incrementCount
    }
  }
}
