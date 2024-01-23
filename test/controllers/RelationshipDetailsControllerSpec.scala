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

import models.{RelationshipDetails, RelationshipDetailsResponse, Relationships}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.UserDataRepository
import services.RelationshipDetailsService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.retrieve.~
import utils.NinoGenerator

import scala.concurrent.Future

class RelationshipDetailsControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockService = mock[RelationshipDetailsService]
  private val mockRepo = mock[UserDataRepository]

  override def beforeEach(): Unit = {
    Mockito.reset[Any](
      mockService,
      mockAuthConnector,
      mockRepo
    )
    super.beforeEach()
  }

  private val app = GuiceApplicationBuilder()
    .overrides(
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[RelationshipDetailsService].toInstance(mockService),
      bind[UserDataRepository].toInstance(mockRepo)
    ).build()

  ".get" - {

    "must return OK when the user has a nino" in {

      val nino = NinoGenerator.randomNino()
      val relationshipDetails = RelationshipDetails(Relationships(None))

      when(mockAuthConnector.authorise[Option[String] ~ Option[String]](any(), any())(any(), any()))
        .thenReturn(Future.successful(new~(Some("userId"), Some(nino))))

      when(mockService.getRelationshipDetails(any())(any())) thenReturn Future.successful(relationshipDetails)

      val request = FakeRequest(GET, routes.RelationshipDetailsController.get.url)
      val result = route(app, request).value

      status(result) mustEqual OK
      contentAsJson(result) mustEqual Json.toJson(RelationshipDetailsResponse(false))

      verify(mockService, times(1)).getRelationshipDetails(eqTo(nino))(any())
    }

    "must return BAD_REQUEST when the user does not have a nino" in {

      when(mockAuthConnector.authorise[Option[String] ~ Option[String]](any(), any())(any(), any()))
        .thenReturn(Future.successful(new~(Some("userId"), None)))

      val request = FakeRequest(GET, routes.RelationshipDetailsController.get.url)
      val result = route(app, request).value

      status(result) mustEqual BAD_REQUEST
      contentAsJson(result) mustEqual Json.obj("error" -> "No NINO available for request")
    }
  }
}
