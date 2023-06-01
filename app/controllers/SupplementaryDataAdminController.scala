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

import models.dmsa.{ListResult, SubmissionItem, SubmissionItemStatus}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.SubmissionItemRepository
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class SupplementaryDataAdminController @Inject()(
                                           override val controllerComponents: ControllerComponents,
                                           submissionItemRepository: SubmissionItemRepository,
                                           auth: BackendAuthComponents
                                         )(implicit ec: ExecutionContext) extends BackendBaseController {

  private val authorised =
    auth.authorizedAction(Permission(
      Resource(
        ResourceType("claim-child-benefit-admin"),
        ResourceLocation("supplementary-data")
      ),
      IAAction("ADMIN")
    ))

  def list(
            status: Option[SubmissionItemStatus],
            created: Option[LocalDate],
            limit: Int,
            offset: Int
          ): Action[AnyContent] = {

    authorised.async {
      submissionItemRepository.list(status, created, limit, offset)
        .map(listResult => Ok(Json.toJson(listResult)(ListResult.apiFormat)))
    }
  }

  def show(id: String): Action[AnyContent] =
    authorised.async {
      submissionItemRepository.get(id).map {
        _.map(item => Ok(Json.toJson(item)(SubmissionItem.apiWrites)))
          .getOrElse(NotFound)
      }
    }

  def dailySummaries(): Action[AnyContent] =
    authorised.async {
      submissionItemRepository
        .dailySummaries
        .map(summaries => Ok(Json.obj("summaries" -> summaries)))
    }

  def retry(id: String): Action[AnyContent] =
    authorised.async {
      submissionItemRepository.retry(id)
        .map(_ => Ok )
        .recover { case SubmissionItemRepository.NothingToUpdateException =>
          NotFound
        }
    }
}
