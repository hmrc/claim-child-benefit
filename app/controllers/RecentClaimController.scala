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

import controllers.actions.IdentifierAction
import models.RecentClaim
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.RecentClaimRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RecentClaimController @Inject()(
                                       cc: ControllerComponents,
                                       identify: IdentifierAction,
                                       repository: RecentClaimRepository
                                     )(implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def get: Action[AnyContent] = identify.async {
    implicit request =>
      request.nino.map { nino =>
        repository
          .get(nino)
          .map {
            _.map(recentClaim => Ok(Json.toJson(recentClaim)))
              .getOrElse(NotFound)
          }
      }.getOrElse(Future.successful(NotFound))
  }

  def set: Action[RecentClaim] = identify(parse.json[RecentClaim]).async {
    implicit request =>
      repository
        .set(request.body)
        .map(_ => NoContent)
  }
}
