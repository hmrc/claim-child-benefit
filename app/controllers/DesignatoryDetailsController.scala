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
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.DesignatoryDetailsService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DesignatoryDetailsController @Inject() (
                                               cc: ControllerComponents,
                                               identify: IdentifierAction,
                                               service: DesignatoryDetailsService,
                                             )(implicit ec: ExecutionContext) extends BackendController(cc) {

  def get: Action[AnyContent] = identify.async { implicit request =>
    request.nino.map { nino =>
      service.getDesignatoryDetails(nino).map { data =>
        Ok(Json.toJson(data))
      }
    }.getOrElse(Future.successful(BadRequest(errorJson)))
  }

  private val errorJson: JsObject =
    Json.obj("error" -> "No NINO available for request")
}
