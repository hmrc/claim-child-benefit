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

import better.files.{Resource => _, _}
import models.dmsa.{Metadata, SubmissionResponse}
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchersSugar.eqTo
import org.mockito.{ArgumentCaptor, Mockito, MockitoSugar}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import play.api.http.Status.ACCEPTED
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import services.SupplementaryDataService
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}
import utils.NinoGenerator

import java.io.{File => JFile}
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class SupplementaryDataControllerSpec extends AnyFreeSpec with Matchers with ScalaFutures with OptionValues with MockitoSugar with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset[Any](mockSupplementaryDataService, mockStubBehaviour)
  }

  private val mockSupplementaryDataService = mock[SupplementaryDataService]

  private val mockStubBehaviour = mock[StubBehaviour]
  private val backendAuthComponents: BackendAuthComponents =
    BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)
  private val permission = Predicate.Permission(
    resource = Resource(
      resourceType = ResourceType("claim-child-benefit"),
      resourceLocation = ResourceLocation("supplementary-data")
    ),
    action = IAAction("WRITE")
  )

  private val app = GuiceApplicationBuilder()
    .overrides(
      bind[SupplementaryDataService].toInstance(mockSupplementaryDataService),
      bind[BackendAuthComponents].toInstance(backendAuthComponents)
    )
    .build()

  private val pdfBytes: Array[Byte] = {
    val stream = getClass.getResourceAsStream("/test.pdf")
    try {
      stream.readAllBytes()
    } finally {
      stream.close()
    }
  }

  "submit" - {

    "submit" - {

      "must return ACCEPTED when a submission is successful" in {

        val fileCaptor: ArgumentCaptor[JFile] = ArgumentCaptor.forClass(classOf[JFile])

        when(mockStubBehaviour.stubAuth(Some(permission), Retrieval.EmptyRetrieval))
          .thenReturn(Future.unit)

        when(mockSupplementaryDataService.submitSupplementaryData(any(), any())(any()))
          .thenReturn(Future.successful("requestId"))

        val nino = NinoGenerator.randomNino()
        val submissionDate = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val submissionDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(submissionDate, ZoneOffset.UTC))
        val correlationId = UUID.randomUUID().toString

        val tempFile = SingletonTemporaryFileCreator.create()
        val betterTempFile = File(tempFile.toPath)
          .deleteOnExit()
          .writeByteArray(pdfBytes)

        val request = FakeRequest(routes.SupplementaryDataController.submit)
          .withHeaders(AUTHORIZATION -> "my-token")
          .withMultipartFormDataBody(
            MultipartFormData(
              dataParts = Map(
                "metadata.nino" -> Seq(nino),
                "metadata.submissionDate" -> Seq(submissionDateString),
                "metadata.correlationId" -> Seq(correlationId)
              ),
              files = Seq(
                MultipartFormData.FilePart(
                  key = "file",
                  filename = "form.pdf",
                  contentType = Some("application/pdf"),
                  ref = tempFile,
                  fileSize = betterTempFile.size
                )
              ),
              badParts = Seq.empty
            )
          )

        val expectedMetadata = Metadata(nino, submissionDate, correlationId)

        val result = route(app, request).value

        status(result) mustEqual ACCEPTED
        contentAsJson(result) mustEqual Json.obj("id" -> "requestId")

        verify(mockSupplementaryDataService, times(1)).submitSupplementaryData(fileCaptor.capture(), eqTo(expectedMetadata))(any())

        fileCaptor.getValue.toScala.contentAsString mustEqual betterTempFile.contentAsString
      }

      "must fail when the submission fails" in {

        when(mockStubBehaviour.stubAuth(Some(permission), Retrieval.EmptyRetrieval))
          .thenReturn(Future.unit)

        when(mockSupplementaryDataService.submitSupplementaryData(any(), any())(any()))
          .thenReturn(Future.failed(new RuntimeException()))

        val tempFile = SingletonTemporaryFileCreator.create()
        val betterTempFile = File(tempFile.toPath)
          .deleteOnExit()
          .writeByteArray(pdfBytes)

        val nino = NinoGenerator.randomNino()
        val submissionDate = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val submissionDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(submissionDate, ZoneOffset.UTC))
        val correlationId = UUID.randomUUID().toString

        val request = FakeRequest(routes.SupplementaryDataController.submit)
          .withHeaders(AUTHORIZATION -> "my-token")
          .withMultipartFormDataBody(
            MultipartFormData(
              dataParts = Map(
                "metadata.nino" -> Seq(nino),
                "metadata.submissionDate" -> Seq(submissionDateString),
                "metadata.correlationId" -> Seq(correlationId)
              ),
              files = Seq(
                MultipartFormData.FilePart(
                  key = "file",
                  filename = "form.pdf",
                  contentType = Some("application/pdf"),
                  ref = tempFile,
                  fileSize = betterTempFile.size
                )
              ),
              badParts = Seq.empty
            )
          )

        route(app, request).value.failed.futureValue
      }

      "must return BAD_REQUEST when the user provides an invalid request" in {

        when(mockStubBehaviour.stubAuth(Some(permission), Retrieval.EmptyRetrieval))
          .thenReturn(Future.unit)

        val request = FakeRequest(routes.SupplementaryDataController.submit)
          .withHeaders(AUTHORIZATION -> "my-token")
          .withMultipartFormDataBody(
            MultipartFormData(
              dataParts = Map.empty,
              files = Seq.empty,
              badParts = Seq.empty
            )
          )

        val result = route(app, request).value

        status(result) mustEqual BAD_REQUEST
        val responseBody = contentAsJson(result).as[SubmissionResponse.Failure]
        responseBody.errors must contain only (
          "metadata.nino: This field is required",
          "metadata.submissionDate: This field is required",
          "metadata.correlationId: This field is required",
          "file: This field is required"
        )

        verify(mockSupplementaryDataService, times(0)).submitSupplementaryData(any(), any())(any())
      }

      "must return BAD_REQUEST when the user provides a file which is not a pdf" in {

        when(mockStubBehaviour.stubAuth(Some(permission), Retrieval.EmptyRetrieval))
          .thenReturn(Future.unit)

        val nino = NinoGenerator.randomNino()

        val tempFile = SingletonTemporaryFileCreator.create()
        val betterTempFile = File(tempFile.toPath)
          .deleteOnExit()
          .writeText("Hello, World!")

        val request = FakeRequest(routes.SupplementaryDataController.submit)
          .withHeaders(AUTHORIZATION -> "my-token")
          .withMultipartFormDataBody(
            MultipartFormData(
              dataParts = Map(
                "metadata.nino" -> Seq(nino)
              ),
              files = Seq(
                MultipartFormData.FilePart(
                  key = "file",
                  filename = "form.pdf",
                  contentType = Some("application/pdf"),
                  ref = tempFile,
                  fileSize = betterTempFile.size
                )
              ),
              badParts = Seq.empty
            )
          )

        val result = route(app, request).value

        status(result) mustEqual BAD_REQUEST
        val responseBody = contentAsJson(result).as[SubmissionResponse.Failure]
        responseBody.errors must contain ("file: error.pdf.invalid")

        verify(mockSupplementaryDataService, times(0)).submitSupplementaryData(any(), any())(any())
      }

      "must fail when the user is not authorised" in {

        when(mockStubBehaviour.stubAuth(Some(permission), Retrieval.EmptyRetrieval))
          .thenReturn(Future.failed(new RuntimeException()))

        val tempFile = SingletonTemporaryFileCreator.create()
        val betterTempFile = File(tempFile.toPath)
          .deleteOnExit()
          .writeText("Hello, World!")

        val nino = NinoGenerator.randomNino()

        val request = FakeRequest(routes.SupplementaryDataController.submit)
          .withHeaders(AUTHORIZATION -> "my-token")
          .withMultipartFormDataBody(
            MultipartFormData(
              dataParts = Map(
                "metadata.nino" -> Seq(nino)
              ),
              files = Seq(
                MultipartFormData.FilePart(
                  key = "file",
                  filename = "form.pdf",
                  contentType = Some("application/pdf"),
                  ref = tempFile,
                  fileSize = betterTempFile.size
                )
              ),
              badParts = Seq.empty
            )
          )

        route(app, request).value.failed.futureValue

        verify(mockSupplementaryDataService, times(0)).submitSupplementaryData(any(), any())(any())
      }
    }
  }
}
