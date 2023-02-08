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

import models.dmsa.{Metadata, ObjectSummary, SubmissionItem, SubmissionItemStatus}
import models.sdes.{NotificationCallback, NotificationType}
import org.mockito.ArgumentMatchers.any
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.SubmissionItemRepository
import utils.NinoGenerator

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.Future

class SdesCallbackControllerSpec extends AnyFreeSpec with Matchers with OptionValues with MockitoSugar with BeforeAndAfterEach with ScalaFutures {

  private val mockSubmissionItemRepository = mock[SubmissionItemRepository]

  override def beforeEach(): Unit = {
    Mockito.reset[Any](
      mockSubmissionItemRepository
    )
    super.beforeEach()
  }

  private val clock = Clock.fixed(Instant.now, ZoneOffset.UTC)

  private val app = GuiceApplicationBuilder()
    .configure(
      "lockTtl" -> 30
    )
    .overrides(
      bind[Clock].toInstance(clock),
      bind[SubmissionItemRepository].toInstance(mockSubmissionItemRepository),
    )
    .build()

  "callback" - {

    val requestBody = NotificationCallback(
      notification = NotificationType.FileProcessed,
      filename = "filename",
      correlationID = "sdesCorrelationId",
      failureReason = None
    )

    val nino = NinoGenerator.randomNino()

    val item = SubmissionItem(
      id = "id",
      status = SubmissionItemStatus.Submitted,
      objectSummary = ObjectSummary(
        location = "file",
        contentLength = 1337L,
        contentMd5 = "hash",
        lastModified = clock.instant().minus(2, ChronoUnit.DAYS)
      ),
      failureReason = None,
      metadata = Metadata(nino),
      created = clock.instant(),
      lastUpdated = clock.instant(),
      sdesCorrelationId = "sdesCorrelationId"
    )

    "must return OK when the status is updated to FileReady" in {

      when(mockSubmissionItemRepository.get(any())).thenReturn(Future.successful(Some(item)))

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody.copy(notification = NotificationType.FileReady)))

      val response = route(app, request).value

      status(response) mustEqual OK
      verify(mockSubmissionItemRepository, times(1)).get(requestBody.correlationID)
      verify(mockSubmissionItemRepository, times(0)).update(any(), any(), any())
    }

    "must return OK when the status is updated to FileReceived" in {

      when(mockSubmissionItemRepository.get(any())).thenReturn(Future.successful(Some(item)))

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody.copy(notification = NotificationType.FileReceived)))

      val response = route(app, request).value

      status(response) mustEqual OK
      verify(mockSubmissionItemRepository, times(1)).get(requestBody.correlationID)
      verify(mockSubmissionItemRepository, times(0)).update(any(), any(), any())
    }

    "must update the status of the submission to Completed and return OK when the status is updated to Processed" in {

      when(mockSubmissionItemRepository.get(any())).thenReturn(Future.successful(Some(item)))
      when(mockSubmissionItemRepository.update(any(), any(), any())).thenReturn(Future.successful(item))

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody.copy(notification = NotificationType.FileProcessed)))

      val response = route(app, request).value

      status(response) mustEqual OK
      verify(mockSubmissionItemRepository, times(1)).get(requestBody.correlationID)
      verify(mockSubmissionItemRepository, times(1)).update(requestBody.correlationID, SubmissionItemStatus.Completed, None)
    }

    "must update the status of the submission to Failed and return OK when the status is updated to Failed" in {

      when(mockSubmissionItemRepository.get(any())).thenReturn(Future.successful(Some(item)))
      when(mockSubmissionItemRepository.update(any(), any(), any())).thenReturn(Future.successful(item))

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody.copy(notification = NotificationType.FileProcessingFailure, failureReason = Some("failure reason"))))

      val response = route(app, request).value

      status(response) mustEqual OK
      verify(mockSubmissionItemRepository, times(1)).get(requestBody.correlationID)
      verify(mockSubmissionItemRepository, times(1)).update(requestBody.correlationID, SubmissionItemStatus.Failed, Some("failure reason"))
    }

    "must retry when the item is locked" in {

      val lockedItem = item.copy(lockedAt = Some(clock.instant()))

      when(mockSubmissionItemRepository.get(any()))
        .thenReturn(Future.successful(Some(lockedItem)))
        .andThen(Future.successful(Some(item)))
      when(mockSubmissionItemRepository.update(any(), any(), any())).thenReturn(Future.successful(item))

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody.copy(notification = NotificationType.FileProcessed)))

      val response = route(app, request).value

      status(response) mustEqual OK
      verify(mockSubmissionItemRepository, times(2)).get(requestBody.correlationID)
      verify(mockSubmissionItemRepository, times(1)).update(requestBody.correlationID, SubmissionItemStatus.Completed, None)
    }

    "must not have to retry when the lock has expired" in {
      when(mockSubmissionItemRepository.get(any())).thenReturn(Future.successful(Some(item.copy(lockedAt = Some(clock.instant().minusSeconds(30))))))
      when(mockSubmissionItemRepository.update(any(), any(), any())).thenReturn(Future.successful(item))

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody.copy(notification = NotificationType.FileProcessed)))

      val response = route(app, request).value

      status(response) mustEqual OK
      verify(mockSubmissionItemRepository, times(1)).get(requestBody.correlationID)
      verify(mockSubmissionItemRepository, times(1)).update(requestBody.correlationID, SubmissionItemStatus.Completed, None)
    }

    "must return NOT_FOUND when there is no matching submission" in {

      when(mockSubmissionItemRepository.get(any())).thenReturn(Future.successful(None))

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody))

      val response = route(app, request).value

      status(response) mustEqual NOT_FOUND
    }

    "must return BAD_REQUEST when the request is invalid" in {
      val request = FakeRequest(routes.SdesCallbackController.callback).withJsonBody(JsObject.empty)
      val response = route(app, request).value
      status(response) mustEqual BAD_REQUEST
    }

    "must fail when the call to get an item fails" in {

      when(mockSubmissionItemRepository.get(any())).thenReturn(Future.failed(new RuntimeException()))

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody))

      route(app, request).value.failed.futureValue
    }

    "must fail when the call to update an item fails" in {

      when(mockSubmissionItemRepository.get(any())).thenReturn(Future.successful(Some(item)))
      when(mockSubmissionItemRepository.update(any(), any(), any())).thenReturn(Future.failed(new RuntimeException()))

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody))

      route(app, request).value.failed.futureValue
    }
  }
}