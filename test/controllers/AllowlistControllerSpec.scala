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
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.AllowlistRepository
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthConnector, MissingBearerToken}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.http.HeaderNames
import utils.NinoGenerator

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

  override def beforeEach(): Unit = {
    reset(mockRepo)
    reset(mockAuthConnector)
    super.beforeEach()
  }

  private val app =
    new GuiceApplicationBuilder()
      .overrides(
        bind[AllowlistRepository].toInstance(mockRepo),
        bind[AuthConnector].toInstance(mockAuthConnector)
      )
      .build

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

      val nino = NinoGenerator.randomNino()

      when(mockRepo.set(any())).thenReturn(Future.successful(Done))

      val request = FakeRequest(PUT, routes.AllowlistController.set.url).withTextBody(nino)

      val result  = route(app, request).value
      status(result) mustEqual OK
      verify(mockRepo, times(1)).set(eqTo(AllowlistEntry(SensitiveString(nino))))
    }

    "must return BadRequest when there is no body" in {

      val request = FakeRequest(PUT, routes.AllowlistController.set.url)

      val result = route(app, request).value
      status(result) mustEqual BAD_REQUEST
    }
  }
}
