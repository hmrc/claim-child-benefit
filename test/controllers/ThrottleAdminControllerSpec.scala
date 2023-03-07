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

import models.{Done, SetLimitRequest, ThrottleData}
import org.mockito.ArgumentMatchers.any
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers._
import repositories.ThrottleRepository
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.internalauth.client.{BackendAuthComponents, IAAction, Predicate, Resource, ResourceLocation, ResourceType}
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class ThrottleAdminControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterEach {

  private val mockRepo = mock[ThrottleRepository]
  private val mockAuthConnector = mock[AuthConnector]

  private val mockStubBehaviour = mock[StubBehaviour]
  private val backendAuthComponents: BackendAuthComponents =
    BackendAuthComponentsStub(mockStubBehaviour)(stubControllerComponents(), global)
  private val permission = Predicate.Permission(
    resource = Resource(
      resourceType = ResourceType("claim-child-benefit-admin"),
      resourceLocation = ResourceLocation("throttle")
    ),
    action = IAAction("ADMIN")
  )

  override def beforeEach(): Unit = {
    Mockito.reset(mockAuthConnector, mockRepo, mockStubBehaviour)
    super.beforeEach()
  }

  private val app =
    new GuiceApplicationBuilder()
      .overrides(
        bind[ThrottleRepository].toInstance(mockRepo),
        bind[AuthConnector].toInstance(mockAuthConnector),
        bind[BackendAuthComponents].toInstance(backendAuthComponents)
      )
      .build()

  ".get" - {

    "must return OK and the current throttle data when the calling service is authorised" in {

      val throttleData = ThrottleData(1, 2)

      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.unit)
      when(mockRepo.get).thenReturn(Future.successful(throttleData))

      val request = FakeRequest(routes.ThrottleAdminController.get).withHeaders(AUTHORIZATION -> "token")
      val result = route(app, request).value

      status(result) mustEqual OK
      contentAsJson(result) mustEqual Json.toJson(throttleData)
    }

    "must fail when the calling service is not authorised" in {

      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.unit)
      val request = FakeRequest(routes.ThrottleAdminController.get) // No AUTHORIZATION header

      route(app, request).value.failed.futureValue
    }
  }

  ".setLimit" - {

    "must set the limit and return OK when the calling service is authorised" in {

      val setRequest = SetLimitRequest(123)
      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.unit)
      when(mockRepo.updateLimit(any())).thenReturn(Future.successful(Done))

      val request =
        FakeRequest(routes.ThrottleAdminController.setLimit)
          .withHeaders(AUTHORIZATION -> "token")
          .withJsonBody(Json.toJson(setRequest))

      val result = route(app, request).value

      status(result) mustEqual OK
      verify(mockRepo, times(1)).updateLimit(123)
    }

    "must fail when the calling service is not authorised" in {

      val setRequest = SetLimitRequest(123)
      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.unit)
      val request = FakeRequest(routes.ThrottleAdminController.setLimit).withJsonBody(Json.toJson(setRequest)) // No AUTHORIZATION header

      route(app, request).value.failed.futureValue
      verify(mockRepo, never).updateLimit(any())
    }
  }
}
