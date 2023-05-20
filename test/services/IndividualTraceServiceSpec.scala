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

import connectors.IndividualTraceConnector
import models.{Done, IndividualTraceCacheItem, IndividualTraceRequest}
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
import repositories.IndividualTraceCacheRepository
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.time.{Instant, LocalDate}
import scala.concurrent.Future

class IndividualTraceServiceSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterEach {

  private val mockConnector = mock[IndividualTraceConnector]
  private val mockRepository = mock[IndividualTraceCacheRepository]

  override def beforeEach(): Unit = {
    Mockito.reset(mockConnector)
    Mockito.reset(mockRepository)
    super.beforeEach()
  }

  private lazy val app = GuiceApplicationBuilder()
    .overrides(
      bind[IndividualTraceConnector].toInstance(mockConnector),
      bind[IndividualTraceCacheRepository].toInstance(mockRepository)
    )

  private lazy val service = app.injector.instanceOf[IndividualTraceService]

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private def upstreamErrorResponse(status: Int) = UpstreamErrorResponse("Upstream error", status, 500, Map.empty)
  private val traceRequest = IndividualTraceRequest("first", "last", LocalDate.now)

  ".get" - {

    "when a trace has not been cached" - {

      "must do a trace, cache it, and return the result" in {

        when(mockConnector.trace(any(), any())(any())) thenReturn Future.successful(true)
        when(mockRepository.get(any())) thenReturn Future.successful(None)
        when(mockRepository.set(any(), any())) thenReturn Future.successful(Done)

        service.trace(traceRequest).futureValue mustEqual true
        verify(mockRepository, times(1)).get(eqTo(traceRequest))
        verify(mockRepository, times(1)).set(eqTo(traceRequest), eqTo(true))
      }

      "must return a result even if details cannot be cached" in {

        when(mockConnector.trace(any(), any())(any())) thenReturn Future.successful(true)
        when(mockRepository.get(any())) thenReturn Future.successful(None)
        when(mockRepository.set(any(), any())) thenReturn Future.failed(new RuntimeException("foo"))

        service.trace(traceRequest).futureValue mustEqual true
        verify(mockRepository, times(1)).get(eqTo(traceRequest))
        verify(mockRepository, times(1)).set(eqTo(traceRequest), eqTo(true))
      }

      "must retry the call when the connector call fails with a bad gateway error" in {

        when(mockConnector.trace(any(), any())(any()))
          .thenReturn(
            Future.failed(upstreamErrorResponse(BAD_GATEWAY)),
            Future.successful(false)
          )
        when(mockRepository.get(any())) thenReturn Future.successful(None)
        when(mockRepository.set(any(), any())) thenReturn Future.successful(Done)

        service.trace(traceRequest).futureValue mustEqual false
        verify(mockRepository, times(1)).get(eqTo(traceRequest))
        verify(mockRepository, times(1)).set(eqTo(traceRequest), eqTo(false))
      }

      "must retry the call when the connector call fails with a service unavailable error" in {

        when(mockConnector.trace(any(), any())(any()))
          .thenReturn(
            Future.failed(upstreamErrorResponse(SERVICE_UNAVAILABLE)),
            Future.successful(false)
          )
        when(mockRepository.get(any())) thenReturn Future.successful(None)
        when(mockRepository.set(any(), any())) thenReturn Future.successful(Done)

        service.trace(traceRequest).futureValue mustEqual false
        verify(mockRepository, times(1)).get(eqTo(traceRequest))
        verify(mockRepository, times(1)).set(eqTo(traceRequest), eqTo(false))
      }

      "must retry the call when the connector call fails with a gateway timeout error" in {

        when(mockConnector.trace(any(), any())(any()))
          .thenReturn(
            Future.failed(upstreamErrorResponse(GATEWAY_TIMEOUT)),
            Future.successful(false)
          )
        when(mockRepository.get(any())) thenReturn Future.successful(None)
        when(mockRepository.set(any(), any())) thenReturn Future.successful(Done)

        service.trace(traceRequest).futureValue mustEqual false
        verify(mockRepository, times(1)).get(eqTo(traceRequest))
        verify(mockRepository, times(1)).set(eqTo(traceRequest), eqTo(false))
      }

      "must fail when the connector call fails 3 times" in {

        when(mockConnector.trace(any(), any())(any()))
          .thenReturn(
            Future.failed(upstreamErrorResponse(GATEWAY_TIMEOUT)),
            Future.failed(upstreamErrorResponse(SERVICE_UNAVAILABLE)),
            Future.failed(upstreamErrorResponse(GATEWAY_TIMEOUT)),
            Future.successful(true)
          )
        when(mockRepository.get(any())) thenReturn Future.successful(None)

        service.trace(traceRequest).failed.futureValue
      }

      "must fail when the connector call fails with an upstream exception which isn't 502/503/504" in {

        val upstreamError = UpstreamErrorResponse("Upstream error", 500, 500, Map.empty)

        when(mockConnector.trace(any(), any())(any()))
          .thenReturn(
            Future.failed(upstreamError),
            Future.successful(true)
          )
        when(mockRepository.get(any())) thenReturn Future.successful(None)

        service.trace(traceRequest).failed.futureValue
      }

      "must fail when the connector call fails when another exception type" in {

        when(mockConnector.trace(any(), any())(any()))
          .thenReturn(
            Future.failed(new RuntimeException()),
            Future.successful(true)
          )
        when(mockRepository.get(any())) thenReturn Future.successful(None)

        service.trace(traceRequest).failed.futureValue
      }
    }

    "when details have been cached" - {

      "must return them without calling the connector" in {

        val cacheItem = IndividualTraceCacheItem("first", "last", LocalDate.now, true, Instant.now)
        when(mockConnector.trace(any(), any())(any())) thenReturn Future.successful(true)
        when(mockRepository.get(any())) thenReturn Future.successful(Some(cacheItem))
        when(mockRepository.set(any(), any())) thenReturn Future.successful(Done)

        service.trace(traceRequest).futureValue mustEqual true
        verify(mockRepository, times(1)).get(eqTo(traceRequest))
        verify(mockRepository, never).set(any(), any())
        verify(mockConnector, never).trace(any(), any())(any())
      }
    }
  }
}
