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

import better.files.File
import cats.data.{EitherNec, EitherT, NonEmptyChain}
import cats.implicits._
import models.dmsa.{Metadata, SubmissionResponse}
import play.api.i18n.{I18nSupport, Messages}
import play.api.libs.Files
import play.api.libs.json.Json
import play.api.mvc.{Action, BaseController, ControllerComponents, MultipartFormData}
import services.{PdfService, SupplementaryDataService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client.{BackendAuthComponents, IAAction, Predicate, Resource, ResourceLocation, ResourceType, Retrieval}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import java.io.IOException
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SupplementaryDataController @Inject() (
                                              override val controllerComponents: ControllerComponents,
                                              auth: BackendAuthComponents,
                                              supplementaryDataFormProvider: SupplementaryDataFormProvider,
                                              pdfService: PdfService,
                                              supplementaryDataService: SupplementaryDataService
                                            )(implicit ec: ExecutionContext) extends BaseController with I18nSupport {

  private val permission = Predicate.Permission(
    resource = Resource(
      resourceType = ResourceType("claim-child-benefit"),
      resourceLocation = ResourceLocation("submit")
    ),
    action = IAAction("WRITE")
  )

  private val authorised = auth.authorizedAction(permission)

  def submit(): Action[MultipartFormData[Files.TemporaryFile]] = authorised.async(parse.multipartFormData(false)) { implicit request =>

    implicit lazy val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    val result: EitherT[Future, NonEmptyChain[String], String] = (
      EitherT.fromEither[Future](getMetadata(request.body)),
      getPdf(request.body)
    ).parTupled.flatMap { case (metadata, file) =>
      EitherT.liftF(supplementaryDataService.submitSupplementaryData(file.toJava, metadata))
    }

    result.fold(
      errors => BadRequest(Json.toJson(SubmissionResponse.Failure(errors))),
      correlationId => Accepted(Json.toJson(SubmissionResponse.Success(correlationId)))
    )
  }

  private def getMetadata(formData: MultipartFormData[Files.TemporaryFile])(implicit messages: Messages): EitherNec[String, Metadata] =
    supplementaryDataFormProvider.form.bindFromRequest(formData.dataParts).fold(
      formWithErrors => Left(NonEmptyChain.fromSeq(formWithErrors.errors.map(error => formatError(error.key, error.format))).get), // always safe
      _.rightNec[String]
    )

  private def getPdf(formData: MultipartFormData[Files.TemporaryFile])(implicit messages: Messages): EitherT[Future, NonEmptyChain[String], File] =
    for {
      file <- EitherT.fromEither[Future] {
        formData
          .file("file")
          .map(file => File(file.ref))
          .toRight(NonEmptyChain.one(formatError("file", Messages("error.required"))))
      }
      pdf <- EitherT[Future, NonEmptyChain[String], File] {
        pdfService.getPdf(file)
          .map(Right(_))
          .recover[EitherNec[String, File]] { case _: IOException =>
            formatError("file", Messages("error.pdf.invalid")).leftNec
          }
      }
    } yield pdf

  private def formatError(key: String, message: String): String = s"$key: $message"
}
