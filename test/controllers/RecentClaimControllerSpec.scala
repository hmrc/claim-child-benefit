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

import models.{Done, RecentClaim, TaxChargeChoice}
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchersSugar.eqTo
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
import repositories.RecentClaimRepository
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.retrieve.~
import utils.NinoGenerator

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.Future

class RecentClaimControllerSpec extends AnyFreeSpec
  with Matchers
  with MockitoSugar
  with OptionValues
  with ScalaFutures
  with BeforeAndAfterEach {

  private val mockRepo          = mock[RecentClaimRepository]
  private val mockAuthConnector = mock[AuthConnector]

  private val nino        = NinoGenerator.randomNino()
  private val instant     = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val recentClaim = RecentClaim(nino, instant, TaxChargeChoice.OptedOut)

  override def beforeEach(): Unit = {
    Mockito.reset(mockRepo)
    Mockito.reset(mockAuthConnector)
    super.beforeEach()
  }

  private val app =
    new GuiceApplicationBuilder()
      .overrides(
        bind[RecentClaimRepository].toInstance(mockRepo),
        bind[AuthConnector].toInstance(mockAuthConnector)
      )
      .build()

  ".get" - {

    "must return OK and the data when a recent claim exists for the user's nino" in {

      when(mockRepo.get(eqTo(nino))) thenReturn Future.successful(Some(recentClaim))

      when(mockAuthConnector.authorise[Option[String] ~ Option[String]](any(), any())(any(), any()))
        .thenReturn(Future.successful(new ~(Some("userId"), Some(nino))))

      val request = FakeRequest(GET, routes.RecentClaimController.get.url)
      val result = route(app, request).value

      status(result) mustEqual OK
      contentAsJson(result) mustEqual Json.toJson(recentClaim)

      verify(mockRepo, times(1)).get(eqTo(nino))
    }

    "must return Not Found when no recent claim exists for this user's nino" in {

      when(mockRepo.get(eqTo(nino))) thenReturn Future.successful(None)

      when(mockAuthConnector.authorise[Option[String] ~ Option[String]](any(), any())(any(), any()))
        .thenReturn(Future.successful(new~(Some("userId"), Some(nino))))

      val request = FakeRequest(GET, routes.RecentClaimController.get.url)
      val result = route(app, request).value

      status(result) mustEqual NOT_FOUND

      verify(mockRepo, times(1)).get(eqTo(nino))
    }
  }

  ".set" - {

    "must return No Content and save the recent claim" in {

      when(mockRepo.set(any())) thenReturn Future.successful(Done)

      when(mockAuthConnector.authorise[Option[String] ~ Option[String]](any(), any())(any(), any()))
        .thenReturn(Future.successful(new~(Some("userId"), Some(nino))))

      val request =
        FakeRequest(POST, routes.RecentClaimController.set.url)
          .withJsonBody(Json.toJson(recentClaim))

      val result = route(app, request).value

      status(result) mustEqual(NO_CONTENT)

      verify(mockRepo, times(1)).set(eqTo(recentClaim))
    }
  }
}
