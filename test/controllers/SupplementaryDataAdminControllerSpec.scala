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

import models.dmsa._
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import repositories.SubmissionItemRepository
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, LocalDate, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SupplementaryDataAdminControllerSpec
  extends AnyFreeSpec
    with Matchers
    with OptionValues
    with MockitoSugar
    with BeforeAndAfterEach
    with ScalaFutures {

  private val mockSubmissionItemRepository: SubmissionItemRepository = mock[SubmissionItemRepository]

  override def beforeEach(): Unit = {
    Mockito.reset[Any](
      mockSubmissionItemRepository,
      mockStubBehaviour,
    )
    super.beforeEach()
  }

  private val clock = Clock.fixed(Instant.now, ZoneOffset.UTC)

  private val mockStubBehaviour = mock[StubBehaviour]
  private val stubBackendAuthComponents =
    BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), implicitly)

  private val app = GuiceApplicationBuilder()
    .overrides(
      bind[Clock].toInstance(clock),
      bind[SubmissionItemRepository].toInstance(mockSubmissionItemRepository),
      bind[BackendAuthComponents].toInstance(stubBackendAuthComponents),
    )
    .build()

  private implicit val crypter: Encrypter with Decrypter = app.injector.instanceOf[Encrypter with Decrypter]

  private val item = SubmissionItem(
    id = "id",
    status = SubmissionItemStatus.Submitted,
    objectSummary = ObjectSummary(
      location = "file",
      contentLength = 1337L,
      contentMd5 = "hash",
      lastModified = clock.instant().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS)
    ),
    failureReason = None,
    metadata = Metadata(nino = "nino", submissionDate = clock.instant(), correlationId = "correlationId"),
    created = clock.instant().truncatedTo(ChronoUnit.MILLIS),
    lastUpdated = clock.instant().truncatedTo(ChronoUnit.MILLIS),
    sdesCorrelationId = "sdesCorrelationId"
  )

  "list" - {

    val listResult = ListResult(
      totalCount = 1,
      summaries = Seq(SubmissionSummary(item))
    )

    "must return a list of submissions for an authorised user" in {

      val predicate = Permission(Resource(ResourceType("claim-child-benefit-admin"), ResourceLocation("supplementary-data")), IAAction("ADMIN"))
      when(mockSubmissionItemRepository.list(any(), any(), any(), any())).thenReturn(Future.successful(listResult))
      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.unit)

      val request =
        FakeRequest(routes.SupplementaryDataAdminController.list(
          status = Some(SubmissionItemStatus.Completed),
          created = Some(LocalDate.now(clock)),
          limit = 10,
          offset = 5
        )).withHeaders("Authorization" -> "Token foo")

      val result = route(app, request).value

      status(result) mustEqual OK

      contentAsJson(result) mustEqual Json.toJson(listResult)

      verify(mockSubmissionItemRepository).list(
        status = Some(SubmissionItemStatus.Completed),
        created = Some(LocalDate.now(clock)),
        limit = 10,
        offset = 5
      )

      verify(mockStubBehaviour).stubAuth(Some(predicate), Retrieval.EmptyRetrieval)
    }

    "must return unauthorised for an unauthenticated user" in {

      val request = FakeRequest(routes.SupplementaryDataAdminController.list()) // No Authorization header

      route(app, request).value.failed.futureValue
      verify(mockSubmissionItemRepository, never).list(any(), any(), any(), any())
    }

    "must return unauthorised for an unauthorised user" in {

      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.failed(new Exception("foo")))

      val request =
        FakeRequest(routes.SupplementaryDataAdminController.list())
          .withHeaders("Authorization" -> "Token foo")

      route(app, request).value.failed.futureValue
      verify(mockSubmissionItemRepository, never).list(any(), any(), any(), any())
    }
  }

  "show" - {

    "must return the data for the requested submission item when it exists and the user is authorised" in {

      val predicate = Permission(Resource(ResourceType("claim-child-benefit-admin"), ResourceLocation("supplementary-data")), IAAction("ADMIN"))
      when(mockSubmissionItemRepository.get(eqTo("id"))).thenReturn(Future.successful(Some(item)))
      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.unit)

      val request =
        FakeRequest(routes.SupplementaryDataAdminController.show("id"))
          .withHeaders("Authorization" -> "Token foo")

      val result = route(app, request).value

      status(result) mustEqual OK
      contentAsJson(result) mustEqual Json.toJson(item)(SubmissionItem.apiWrites)
      verify(mockSubmissionItemRepository, times(1)).get(eqTo("id"))
      verify(mockStubBehaviour).stubAuth(Some(predicate), Retrieval.EmptyRetrieval)
    }

    "must return Not Found when the requested submission item does not exist and the user is authorised" in {

      when(mockSubmissionItemRepository.get(eqTo("id"))).thenReturn(Future.successful(None))
      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.unit)

      val request = FakeRequest(routes.SupplementaryDataAdminController.show( "id"))
        .withHeaders("Authorization" -> "Token foo")

      val result = route(app, request).value

      status(result) mustEqual NOT_FOUND
      verify(mockSubmissionItemRepository, times(1)).get(eqTo("id"))
    }

    "must fail for an unauthenticated user" in {

      when(mockSubmissionItemRepository.get(eqTo("id"))).thenReturn(Future.successful(Some(item)))
      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.unit)

      val request = FakeRequest(routes.SupplementaryDataAdminController.show("id")) // No auth header

      route(app, request).value.failed.futureValue
      verify(mockSubmissionItemRepository, never).get(any())
    }

    "must fail for an unauthorised user" in {

      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.failed(new RuntimeException()))

      val request =
        FakeRequest(routes.SupplementaryDataAdminController.show("id"))
          .withHeaders("Authorization" -> "Token foo")

      route(app, request).value.failed.futureValue
      verify(mockSubmissionItemRepository, never).get(eqTo("id"))
    }
  }

  "dailySummaries" - {

    "must return a list of summaries for an authorised user" in {

      val predicate = Permission(Resource(ResourceType("claim-child-benefit-admin"), ResourceLocation("supplementary-data")), IAAction("ADMIN"))
      val dailySummaries = List(DailySummary(LocalDate.now, 1, 2, 3, 4))
      when(mockSubmissionItemRepository.dailySummaries).thenReturn(Future.successful(dailySummaries))
      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.unit)

      val request =
        FakeRequest(routes.SupplementaryDataAdminController.dailySummaries())
          .withHeaders("Authorization" -> "Token foo")

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResult = Json.obj("summaries" -> dailySummaries)
      contentAsJson(result) mustEqual Json.toJson(expectedResult)
      verify(mockStubBehaviour).stubAuth(Some(predicate), Retrieval.EmptyRetrieval)
    }

    "must fail for an unauthenticated user" in {

      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.unit)

      val request = FakeRequest(routes.SupplementaryDataAdminController.dailySummaries()) // No Authorization header

      route(app, request).value.failed.futureValue
      verify(mockSubmissionItemRepository, never).dailySummaries
    }

    "must fail when the user is not authorised" in {

      when(mockStubBehaviour.stubAuth[Unit](any(), any())).thenReturn(Future.failed(new Exception("foo")))

      val request =
        FakeRequest(routes.SupplementaryDataAdminController.dailySummaries())
          .withHeaders("Authorization" -> "Token foo")

      route(app, request).value.failed.futureValue
      verify(mockSubmissionItemRepository, never).dailySummaries
    }
  }
}
