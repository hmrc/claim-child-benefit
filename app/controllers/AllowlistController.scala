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
import models.AllowlistEntry
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.AllowlistRepository
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AllowlistController @Inject()(
                                     cc: ControllerComponents,
                                     identify: IdentifierAction,
                                     repository: AllowlistRepository
                                   )(implicit ec: ExecutionContext) extends BackendController(cc) {

  def get: Action[AnyContent] = identify.async {
    implicit request =>
      request.nino.map { nino =>
        repository.exists(AllowlistEntry(SensitiveString(nino))).map {
          case true => NoContent
          case false => NotFound
        }
      }.getOrElse(Future.successful(NotFound))
  }

  // TODO: Add internal auth
  // TODO: Do we need to validate the NINO format?
  def set: Action[AnyContent] = Action.async {
    implicit request =>
      request.body.asText.map { nino =>
        repository
          .set(AllowlistEntry(SensitiveString(nino)))
          .map(_ => Ok)
      }.getOrElse(Future.successful(BadRequest))
  }
}
