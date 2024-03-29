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
import models.UserData
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.UserDataRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class UserDataController @Inject()(
                                    cc: ControllerComponents,
                                    identify: IdentifierAction,
                                    repository: UserDataRepository
                                  )(implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def get: Action[AnyContent] = identify.async {
    request =>
      repository
        .get(request.userId)
        .map {
          _.map(userData => Ok(Json.toJson(userData)))
            .getOrElse(NotFound)
        }
  }

  def set: Action[UserData] = identify(parse.json[UserData]).async {
    request =>
      repository
        .set(request.body)
        .map(_ => NoContent)
  }

  def keepAlive: Action[AnyContent] = identify.async {
    request =>
      repository
        .keepAlive(request.userId)
        .map(_ => NoContent)
  }

  def clear: Action[AnyContent] = identify.async {
    request =>
      repository
        .clear(request.userId)
        .map(_ => NoContent)
  }
}
