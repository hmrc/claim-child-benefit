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

import models.IndividualTraceRequest
import org.mockito.ArgumentMatchers.any
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.IndividualTraceService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.internalauth.client.BackendAuthComponents
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class IndividualTraceControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterEach {

  private val mockService = mock[IndividualTraceService]
  private val mockAuthConnector = mock[AuthConnector]

  private val mockStubBehaviour = mock[StubBehaviour]
  private val backendAuthComponents: BackendAuthComponents =
    BackendAuthComponentsStub(mockStubBehaviour)(stubControllerComponents(), global)

  override def beforeEach(): Unit = {
    Mockito.reset(mockAuthConnector, mockService, mockStubBehaviour)
    super.beforeEach()
  }

  private val app =
    new GuiceApplicationBuilder()
      .overrides(
        bind[IndividualTraceService].toInstance(mockService),
        bind[AuthConnector].toInstance(mockAuthConnector),
        bind[BackendAuthComponents].toInstance(backendAuthComponents)
      )
      .build()

  private val traceRequest = IndividualTraceRequest("first", "last", LocalDate.now)

  ".trace" - {

    "must return NO_CONTENT when the calling service is authorised and there is a match" in {

      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.unit)
      when(mockService.trace(any())(any())).thenReturn(Future.successful(true))

      val request =
        FakeRequest(routes.IndividualTraceController.trace)
          .withHeaders(AUTHORIZATION -> "token")
          .withJsonBody(Json.toJson(traceRequest))

      val result = route(app, request).value

      status(result) mustEqual NO_CONTENT
    }

    "must return NOT_FOUND when the calling service is authorised and there is no match" in {

      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.unit)
      when(mockService.trace(any())(any())).thenReturn(Future.successful(false))


      val request =
        FakeRequest(routes.IndividualTraceController.trace)
          .withHeaders(AUTHORIZATION -> "token")
          .withJsonBody(Json.toJson(traceRequest))

      val result = route(app, request).value

      status(result) mustEqual NOT_FOUND
    }

    "must fail when the calling service is not authorised" in {

      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.unit)
      when(mockService.trace(any())(any())).thenReturn(Future.successful(true))

      val request =
        FakeRequest(routes.IndividualTraceController.trace)
          .withJsonBody(Json.toJson(traceRequest)) // No AUTHORIZATION header

      route(app, request).value.failed.futureValue
    }
  }
}
