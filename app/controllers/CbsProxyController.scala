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

import connectors.CbsProxyConnector
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CbsProxyController @Inject() (
                                     cc: ControllerComponents,
                                     connector: CbsProxyConnector
                                   )(implicit ec: ExecutionContext) extends BackendController(cc) {

  def submit: Action[JsObject] = Action.async(parse.tolerantJson[JsObject]) { implicit request =>
    request.headers.get("CorrelationId").map { correlationId =>
      connector.submit(request.body, correlationId).map { response =>
        Status(response.status)(response.json)
      }
    }.getOrElse(Future.successful(BadRequest(missingCorrelationId)))
  }

  private val missingCorrelationId: JsObject = Json.obj(
    "error" -> "CorrelationId header required"
  )
}
