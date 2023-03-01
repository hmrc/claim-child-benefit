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

import models.{AllowlistEntry, Done}
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchersSugar.eqTo
import org.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers._
import repositories.AllowlistRepository
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthConnector, MissingBearerToken}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.internalauth.client.{BackendAuthComponents, IAAction, Predicate, Resource, ResourceLocation, ResourceType, Retrieval}
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}
import utils.NinoGenerator

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class AllowlistControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with BeforeAndAfterEach {

  private val mockRepo = mock[AllowlistRepository]
  private val mockAuthConnector = mock[AuthConnector]

  private val mockStubBehaviour = mock[StubBehaviour]
  private val backendAuthComponents: BackendAuthComponents =
    BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)
  private val permission = Predicate.Permission(
    resource = Resource(
      resourceType = ResourceType("claim-child-benefit-admin"),
      resourceLocation = ResourceLocation("allow-list")
    ),
    action = IAAction("ADMIN")
  )

  override def beforeEach(): Unit = {
    reset(mockRepo)
    reset(mockAuthConnector)
    reset(mockStubBehaviour)
    super.beforeEach()
  }

  private val app =
    new GuiceApplicationBuilder()
      .overrides(
        bind[AllowlistRepository].toInstance(mockRepo),
        bind[AuthConnector].toInstance(mockAuthConnector),
        bind[BackendAuthComponents].toInstance(backendAuthComponents)
      )
      .build()

  ".get" - {

    "must return NoContent when called with an authenticated user whose NINO exists in the allowlist" in {

      val nino = NinoGenerator.randomNino()

      when(mockRepo.exists(any())).thenReturn(Future.successful(true))
      when(mockAuthConnector.authorise[Option[String] ~ Option[String]](any(), any())(any(), any()))
        .thenReturn(Future.successful(new ~(Some("internal id"), Some(nino))))

      val request =
        FakeRequest(GET, routes.AllowlistController.get.url)

      val result = route(app, request).value
      status(result) mustEqual NO_CONTENT
    }

    "must return NotFound when called with an authenticated user whose NINO does not exist in the allowlist" in {

      val nino = NinoGenerator.randomNino()

      when(mockRepo.exists(any())).thenReturn(Future.successful(false))
      when(mockAuthConnector.authorise[Option[String] ~ Option[String]](any(), any())(any(), any()))
        .thenReturn(Future.successful(new~(Some("internal id"), Some(nino))))

      val request =
        FakeRequest(GET, routes.AllowlistController.get.url)

      val result = route(app, request).value
      status(result) mustEqual NOT_FOUND
    }

    "must return NotFound when called with an unauthentiacted user" in {

      when(mockAuthConnector.authorise[Option[String] ~ Option[String]](any(), any())(any(), any()))
        .thenReturn(Future.failed(MissingBearerToken()))

      val request =
        FakeRequest(GET, routes.AllowlistController.get.url)
          .withHeaders(HeaderNames.xSessionId -> "userId")

      val result = route(app, request).value
      status(result) mustEqual NOT_FOUND
      verify(mockRepo, never).exists(any())
    }
  }

  ".set" - {

    "must store the supplied NINO" in {

      when(mockStubBehaviour.stubAuth[Unit](any(), any()))
        .thenReturn(Future.unit)

      val nino = NinoGenerator.randomNino()

      when(mockRepo.set(any())).thenReturn(Future.successful(Done))

      val request = FakeRequest(PUT, routes.AllowlistController.set.url)
        .withHeaders(AUTHORIZATION -> "my-token")
        .withTextBody(nino)

      val result  = route(app, request).value
      status(result) mustEqual OK

      verify(mockStubBehaviour, times(1)).stubAuth(Some(permission), Retrieval.EmptyRetrieval)
      verify(mockRepo, times(1)).set(eqTo(AllowlistEntry(SensitiveString(nino))))
    }

    "must return BadRequest when there is no body" in {

      when(mockStubBehaviour.stubAuth[Unit](any(), any()))
        .thenReturn(Future.unit)

      val request = FakeRequest(PUT, routes.AllowlistController.set.url)
        .withHeaders(AUTHORIZATION -> "my-token")

      val result = route(app, request).value
      status(result) mustEqual BAD_REQUEST
    }

    "must fail when the user is not authorised" in {

      when(mockStubBehaviour.stubAuth[Unit](any(), any()))
        .thenReturn(Future.failed(new RuntimeException()))

      val nino = NinoGenerator.randomNino()

      when(mockRepo.set(any())).thenReturn(Future.successful(Done))

      val request = FakeRequest(PUT, routes.AllowlistController.set.url)
        .withHeaders(AUTHORIZATION -> "my-token")
        .withTextBody(nino)

      route(app, request).value.failed.futureValue
    }
  }

  ".delete" - {

    "must delete the supplied NINO" in {

      when(mockStubBehaviour.stubAuth[Unit](any(), any()))
        .thenReturn(Future.unit)

      when(mockRepo.delete(any())).thenReturn(Future.successful(Done))

      val nino = NinoGenerator.randomNino()

      val request = FakeRequest(DELETE, routes.AllowlistController.delete.url)
        .withHeaders(AUTHORIZATION -> "my-token")
        .withTextBody(nino)

      val result = route(app, request).value
      status(result) mustEqual OK

      verify(mockStubBehaviour, times(1)).stubAuth(Some(permission), Retrieval.EmptyRetrieval)
      verify(mockRepo, times(1)).delete(eqTo(AllowlistEntry(SensitiveString(nino))))

    }

    "must return BadRequest when there is no body" in {

      when(mockStubBehaviour.stubAuth[Unit](any(), any()))
        .thenReturn(Future.unit)

      val request = FakeRequest(DELETE, routes.AllowlistController.delete.url)
        .withHeaders(AUTHORIZATION -> "my-token")

      val result = route(app, request).value
      status(result) mustEqual BAD_REQUEST
    }

    "must fail when the user is not authorised" in {
      when(mockStubBehaviour.stubAuth[Unit](any(), any()))
        .thenReturn(Future.failed(new RuntimeException()))

      val nino = NinoGenerator.randomNino()

      when(mockRepo.delete(any())).thenReturn(Future.successful(Done))

      val request = FakeRequest(DELETE, routes.AllowlistController.delete.url)
        .withHeaders(AUTHORIZATION -> "my-token")
        .withTextBody(nino)

      route(app, request).value.failed.futureValue
    }

    }
}
