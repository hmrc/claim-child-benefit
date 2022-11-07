/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers.actions

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{Action, AnyContent, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderNames

class IdentifierActionSpec extends AnyFreeSpec with Matchers {

  class Harness(identify: IdentifierAction) {
    def get(): Action[AnyContent] = identify { _ => Results.Ok }
  }

  "identify" - {

    "must execute the request when the request has a session id" in {

      val app = new GuiceApplicationBuilder().build()

      running(app) {
        val identifierAction = app.injector.instanceOf[IdentifierAction]
        val controller       = new Harness(identifierAction)

        val request = FakeRequest().withHeaders(HeaderNames.xSessionId -> "foo")

        val result = controller.get()(request)

        status(result) mustEqual OK
      }
    }

    "must return Bad Request when the request does not have a session id" in {

      val app = new GuiceApplicationBuilder().build()

      running(app) {
        val identifierAction = app.injector.instanceOf[IdentifierAction]
        val controller = new Harness(identifierAction)

        val request = FakeRequest()

        val result = controller.get()(request)

        status(result) mustEqual BAD_REQUEST
      }
    }
  }
}
