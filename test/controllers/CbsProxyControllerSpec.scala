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

import connectors.CbsProxyConnector
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.internalauth.client._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CbsProxyControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterEach {

  private val mockStubBehaviour = mock[StubBehaviour]
  private val backendAuthComponents: BackendAuthComponents =
    BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)
  private val permission = Predicate.Permission(
    resource = Resource(
      resourceType = ResourceType("claim-child-benefit"),
      resourceLocation = ResourceLocation("submit")
    ),
    action = IAAction("WRITE")
  )

  private val mockConnector = mock[CbsProxyConnector]

  override def beforeEach(): Unit = {
    Mockito.reset[Any](
      mockConnector,
      mockStubBehaviour
    )
    super.beforeEach()
  }

  private val app = GuiceApplicationBuilder()
    .overrides(
      bind[BackendAuthComponents].toInstance(backendAuthComponents),
      bind[CbsProxyConnector].toInstance(mockConnector),
    ).build()

  ".submit" - {

    "must call the CbsProxyConnector and return a result based on the response" in {

      val requestBody = Json.obj("foo" -> "bar")
      val responseBody = Json.obj("bar" -> "foo")
      val httpResponse = HttpResponse(CREATED, responseBody, Map.empty)
      val correlationId = "correlationId"

      when(mockStubBehaviour.stubAuth(Some(permission), Retrieval.EmptyRetrieval))
        .thenReturn(Future.unit)

      when(mockConnector.submit(any(), any())(any()))
        .thenReturn(Future.successful(httpResponse))

      val request = FakeRequest(POST, routes.CbsProxyController.submit.url)
        .withHeaders(
          AUTHORIZATION   -> "my-token",
          "CorrelationId" -> correlationId
        )
        .withJsonBody(requestBody)

      val result = route(app, request).value

      status(result) mustEqual CREATED
      contentAsJson(result) mustEqual responseBody

      verify(mockConnector).submit(eqTo(requestBody), eqTo(Some(correlationId)))(any())
//      verify(mockStubBehaviour).stubAuth(Some(permission), Retrieval.EmptyRetrieval) TODO
    }

    "must return BAD_REQUEST when the request is missing a json body" in {

      val requestBody = Json.obj("foo" -> "bar")

      when(mockStubBehaviour.stubAuth(Some(permission), Retrieval.EmptyRetrieval))
        .thenReturn(Future.unit)

      val request = FakeRequest(POST, routes.CbsProxyController.submit.url)
        .withHeaders(
          AUTHORIZATION -> "my-token",
        )
        .withJsonBody(requestBody)

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.obj("error" -> "CorrelationId header required")
    }

    "must return BAD_REQUEST when the request is missing the CorrelationId header" in {

      val correlationId = "correlationId"

      when(mockStubBehaviour.stubAuth(Some(permission), Retrieval.EmptyRetrieval))
        .thenReturn(Future.unit)

      val request = FakeRequest(POST, routes.CbsProxyController.submit.url)
        .withHeaders(
          AUTHORIZATION -> "my-token",
          "CorrelationId" -> correlationId
        )

      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
    }

    "must fail when the CbsProxyConnector fails" in {

      val requestBody = Json.obj("foo" -> "bar")
      val correlationId = "correlationId"

      when(mockStubBehaviour.stubAuth(Some(permission), Retrieval.EmptyRetrieval))
        .thenReturn(Future.unit)

      when(mockConnector.submit(any(), any())(any()))
        .thenReturn(Future.failed(new RuntimeException()))

      val request = FakeRequest(POST, routes.CbsProxyController.submit.url)
        .withHeaders(
          AUTHORIZATION   -> "my-token",
          "CorrelationId" -> correlationId
        )
        .withJsonBody(requestBody)

      route(app, request).value.failed.futureValue
    }

    "must fail when the user is not authorised" in {

      val requestBody = Json.obj("foo" -> "bar")
      val responseBody = Json.obj("bar" -> "foo")
      val httpResponse = HttpResponse(CREATED, responseBody, Map.empty)
      val correlationId = "correlationId"

      when(mockStubBehaviour.stubAuth(Some(permission), Retrieval.EmptyRetrieval))
        .thenReturn(Future.failed(new RuntimeException()))

      val request = FakeRequest(POST, routes.CbsProxyController.submit.url)
        .withHeaders(
          AUTHORIZATION -> "my-token",
          "CorrelationId" -> correlationId
        )
        .withJsonBody(requestBody)

      route(app, request).value.failed.futureValue
    }
  }
}
