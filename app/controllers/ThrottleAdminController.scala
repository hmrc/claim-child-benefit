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

import models.SetLimitRequest
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.ThrottleRepository
import uk.gov.hmrc.internalauth.client.{BackendAuthComponents, IAAction, Predicate, Resource, ResourceLocation, ResourceType}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ThrottleAdminController @Inject()(
                                         cc: ControllerComponents,
                                         auth: BackendAuthComponents,
                                         repository: ThrottleRepository
                                       )(implicit ec: ExecutionContext) extends BackendController(cc) {

  private val permission = Predicate.Permission(
    resource = Resource(
      resourceType = ResourceType("claim-child-benefit-admin"),
      resourceLocation = ResourceLocation("throttle")
    ),
    action = IAAction("ADMIN")
  )

  private val authorised = auth.authorizedAction(permission)

  def get: Action[AnyContent] = authorised.async {
    implicit request =>
      repository
        .get
        .map(result => Ok(Json.toJson(result)))
  }

  def setLimit: Action[SetLimitRequest] = authorised.compose(Action(parse.json[SetLimitRequest])).async {
    implicit request =>
      repository
        .updateLimit(request.body.limit)
        .map(_ => Ok)
  }
}
