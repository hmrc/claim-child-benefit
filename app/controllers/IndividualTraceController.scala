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

import models.{IndividualTraceRequest, IndividualTraceResponse}
import play.api.libs.json.Json
import play.api.mvc.{Action, ControllerComponents}
import services.IndividualTraceService
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class IndividualTraceController @Inject()(
                                           cc: ControllerComponents,
                                           auth: BackendAuthComponents,
                                           service: IndividualTraceService
                                         )(implicit ec: ExecutionContext) extends BackendController(cc) {

  private val permission = Predicate.Permission(
    resource = Resource(
      resourceType = ResourceType("claim-child-benefit"),
      resourceLocation = ResourceLocation("individual-trace")
    ),
    action = IAAction("READ")
  )

  private val authorised = auth.authorizedAction(permission)

  def trace: Action[IndividualTraceRequest] = authorised.compose(Action(parse.json[IndividualTraceRequest])).async {
    implicit request =>
      service.trace(request.body).map { result =>
        Ok(Json.toJson(IndividualTraceResponse(result)))
      }
  }
}
