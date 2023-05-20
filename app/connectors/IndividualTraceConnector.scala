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

package connectors

import config.Service
import connectors.IndividualTraceParser._
import models.IndividualTraceRequest
import play.api.Configuration
import play.api.http.HeaderNames
import play.api.libs.json._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

class IndividualTraceConnector @Inject()(
                                   configuration: Configuration,
                                   httpClient: HttpClientV2
                                 )(implicit ec: ExecutionContext) {

  private val service: Service = configuration.get[Service]("microservice.services.individual-trace")
  private val apiKey: String = configuration.get[String]("microservice.services.individual-trace.auth")
  private val originatorId: String = configuration.get[String]("microservice.services.individual-trace.originator-id")
  private val environment: String = configuration.get[String]("microservice.services.individual-trace.environment")

  def trace(traceRequest: IndividualTraceRequest, correlationId: String = UUID.randomUUID().toString)
           (implicit hc: HeaderCarrier): Future[Boolean] = {

    httpClient.post(url"${service.baseUrl}/individuals/trace")
      .setHeader(HeaderNames.AUTHORIZATION -> s"Bearer $apiKey")
      .setHeader("OriginatorId" -> originatorId)
      .setHeader("Environment" -> environment)
      .setHeader("CorrelationId" -> correlationId)
      .setHeader(HeaderNames.CONTENT_TYPE -> "application/json")
      .withBody(Json.toJson(traceRequest)(IndividualTraceRequest.desWrites))
      .execute[Either[Exception, Boolean]]
      .flatMap {
        case Right(result)   => Future.successful(result)
        case Left(exception) => Future.failed(exception)
      }
  }
}

object IndividualTraceConnector {

  case object CannotParseResponseException extends Exception with NoStackTrace {
    override def getMessage: String = s"Unable to parse Individual Trace error response"
  }

  final case class UnexpectedResponseException(status: Int, body: String) extends Exception with NoStackTrace {
    override def getMessage: String = s"Unexpected response from Individual Trace, status: $status, body: $body"
  }
}
