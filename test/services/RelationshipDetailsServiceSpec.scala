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

package services

import connectors.RelationshipDetailsConnector
import models.{Done, Relationship, RelationshipDetails, RelationshipSource, RelationshipType, Relationships}
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchersSugar.eqTo
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.http.Status.{BAD_GATEWAY, GATEWAY_TIMEOUT, SERVICE_UNAVAILABLE}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.RelationshipDetailsCacheRepository
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import utils.NinoGenerator

import scala.concurrent.Future

class RelationshipDetailsServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar with ScalaFutures with IntegrationPatience with BeforeAndAfterEach {

  private val mockConnector = mock[RelationshipDetailsConnector]
  private val mockRepository = mock[RelationshipDetailsCacheRepository]

  override def beforeEach(): Unit = {
    Mockito.reset(mockConnector)
    Mockito.reset(mockRepository)
    super.beforeEach()
  }

  private lazy val app = GuiceApplicationBuilder()
    .overrides(
      bind[RelationshipDetailsConnector].toInstance(mockConnector),
      bind[RelationshipDetailsCacheRepository].toInstance(mockRepository)
    )

  private lazy val service = app.injector.instanceOf[RelationshipDetailsService]

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val nino = NinoGenerator.randomNino()

  private val relationshipDetailsResponse =
    RelationshipDetails(
      Relationships(Some(List(
        Relationship(RelationshipType.AdultChild, RelationshipSource.CHB),
        Relationship(RelationshipType.AdultDerived, RelationshipSource.TFC)
      )))
    )

  private def upstreamErrorResponse(status: Int) = UpstreamErrorResponse("Upstream error", status, 500, Map.empty)

  "getRelationshipDetails" - {

    "when details have not been cached" - {

      "must get details, cache them, and return them" in {

        when(mockConnector.getRelationships(any(), any())(any())) thenReturn Future.successful(relationshipDetailsResponse)
        when(mockRepository.get(any())) thenReturn Future.successful(None)
        when(mockRepository.set(any(), any())) thenReturn Future.successful(Done)

        service.getRelationshipDetails(nino).futureValue mustEqual relationshipDetailsResponse
        verify(mockRepository, times(1)).get(eqTo(nino))
        verify(mockRepository, times(1)).set(eqTo(nino), eqTo(relationshipDetailsResponse))
      }

      "must return details even if they cannot be cached" in {

        when(mockConnector.getRelationships(any(), any())(any())) thenReturn Future.successful(relationshipDetailsResponse)
        when(mockRepository.get(any())) thenReturn Future.successful(None)
        when(mockRepository.set(any(), any())) thenReturn Future.failed(new RuntimeException("foo"))

        service.getRelationshipDetails(nino).futureValue mustEqual relationshipDetailsResponse
        verify(mockRepository, times(1)).get(eqTo(nino))
        verify(mockRepository, times(1)).set(eqTo(nino), eqTo(relationshipDetailsResponse))
      }

      "must retry the call when the connector call fails with a bad gateway error" in {

        when(mockConnector.getRelationships(any(), any())(any()))
          .thenReturn(
            Future.failed(upstreamErrorResponse(BAD_GATEWAY)),
            Future.successful(relationshipDetailsResponse)
          )
        when(mockRepository.get(any())) thenReturn Future.successful(None)
        when(mockRepository.set(any(), any())) thenReturn Future.successful(Done)

        service.getRelationshipDetails(nino).futureValue mustEqual relationshipDetailsResponse
      }

      "must retry the call when the connector call fails with a service unavailable error" in {

        when(mockConnector.getRelationships(any(), any())(any()))
          .thenReturn(
            Future.failed(upstreamErrorResponse(SERVICE_UNAVAILABLE)),
            Future.successful(relationshipDetailsResponse)
          )
        when(mockRepository.get(any())) thenReturn Future.successful(None)
        when(mockRepository.set(any(), any())) thenReturn Future.successful(Done)

        service.getRelationshipDetails(nino).futureValue mustEqual relationshipDetailsResponse
      }

      "must retry the call when the connector call fails with a gateway timeout error" in {

        when(mockConnector.getRelationships(any(), any())(any()))
          .thenReturn(
            Future.failed(upstreamErrorResponse(GATEWAY_TIMEOUT)),
            Future.successful(relationshipDetailsResponse)
          )
        when(mockRepository.get(any())) thenReturn Future.successful(None)
        when(mockRepository.set(any(), any())) thenReturn Future.successful(Done)

        service.getRelationshipDetails(nino).futureValue mustEqual relationshipDetailsResponse
      }

      "must fail when the connector call fails 3 times" in {

        when(mockConnector.getRelationships(any(), any())(any()))
          .thenReturn(
            Future.failed(upstreamErrorResponse(GATEWAY_TIMEOUT)),
            Future.failed(upstreamErrorResponse(SERVICE_UNAVAILABLE)),
            Future.failed(upstreamErrorResponse(GATEWAY_TIMEOUT)),
            Future.successful(relationshipDetailsResponse)
          )
        when(mockRepository.get(any())) thenReturn Future.successful(None)

        service.getRelationshipDetails(nino).failed.futureValue
      }

      "must fail when the connector call fails with an upstream exception which isn't 502/503/504" in {

        val upstreamError = UpstreamErrorResponse("Upstream error", 500, 500, Map.empty)

        when(mockConnector.getRelationships(any(), any())(any()))
          .thenReturn(
            Future.failed(upstreamError),
            Future.successful(relationshipDetailsResponse)
          )
        when(mockRepository.get(any())) thenReturn Future.successful(None)

        service.getRelationshipDetails(nino).failed.futureValue
      }

      "must fail when the connector call fails when another exception type" in {

        when(mockConnector.getRelationships(any(), any())(any()))
          .thenReturn(
            Future.failed(new RuntimeException()),
            Future.successful(relationshipDetailsResponse)
          )
        when(mockRepository.get(any())) thenReturn Future.successful(None)

        service.getRelationshipDetails(nino).failed.futureValue
      }
    }

    "when details have been cached" - {

      "must return them without calling the connector" in {

        when(mockConnector.getRelationships(any(), any())(any())) thenReturn Future.successful(relationshipDetailsResponse)
        when(mockRepository.get(any())) thenReturn Future.successful(Some(relationshipDetailsResponse))
        when(mockRepository.set(any(), any())) thenReturn Future.successful(Done)

        service.getRelationshipDetails(nino).futureValue mustEqual relationshipDetailsResponse
        verify(mockRepository, times(1)).get(eqTo(nino))
        verify(mockRepository, never).set(any(), any())
        verify(mockConnector, never).getRelationships(any(), any())(any())
      }
    }
  }
}
