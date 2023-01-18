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

package controllers.actions

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results.Ok
import play.api.mvc.{Action, AnyContent, BodyParsers}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.{AuthConnector, MissingBearerToken}
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class IdentifierActionSpec extends AnyFreeSpec with Matchers {

  class Harness(identify: IdentifierAction) {
    def get(): Action[AnyContent] = identify { request =>
      Ok(request.nino.mkString)
    }
  }

  "identify" - {

  val app = new GuiceApplicationBuilder().build()
  val bodyParsers = app.injector.instanceOf[BodyParsers.Default]

    "when the user is authenticated" - {

      "must execute the request when an internal Id can be retrieved" in {

        val identifierAction = new IdentifierAction(new FakeAuthConnector(new ~(Some("internalId"), Some("nino"))), bodyParsers)
        val controller = new Harness(identifierAction)

        val request = FakeRequest()

        val result = controller.get()(request)

        status(result) mustEqual OK
        contentAsString(result) mustEqual "nino"
      }

      "must execute the request when an internal Id can be retrieved but there is no nino" in {

        val identifierAction = new IdentifierAction(new FakeAuthConnector(new ~(Some("internalId"), None)), bodyParsers)
        val controller = new Harness(identifierAction)

        val request = FakeRequest()

        val result = controller.get()(request)

        status(result) mustEqual OK
        contentAsString(result) mustEqual ""
      }

      "must return BadRequest when no internalId can be retrieved" in {

        val identifierAction = new IdentifierAction(new FakeAuthConnector(new ~(None, Some("nino"))), bodyParsers)
        val controller = new Harness(identifierAction)

        val request = FakeRequest()

        val result = controller.get()(request)

        status(result) mustEqual BAD_REQUEST
      }
    }

    "when the user is not authenticated" - {

      "must execute the request when the request has a session id" in {

        val identifierAction = new IdentifierAction(new FakeFailingAuthConnector(MissingBearerToken()), bodyParsers)
        val controller = new Harness(identifierAction)

        val request = FakeRequest().withHeaders(HeaderNames.xSessionId -> "foo")

        val result = controller.get()(request)

        status(result) mustEqual OK
      }

      "must return Bad Request when the request does not have a session id" in {

        val identifierAction = new IdentifierAction(new FakeFailingAuthConnector(MissingBearerToken()), bodyParsers)
        val controller = new Harness(identifierAction)

        val request = FakeRequest()

        val result = controller.get()(request)

        status(result) mustEqual BAD_REQUEST
      }
    }
  }

  class FakeAuthConnector[T](value: T) extends AuthConnector {
    override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
      Future.fromTry(Try(value.asInstanceOf[A]))
  }

  class FakeFailingAuthConnector(exceptionToReturn: Throwable) extends AuthConnector {
    override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
      Future.failed(exceptionToReturn)
  }
}