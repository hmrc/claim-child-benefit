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
import models.Done
import models.sdes.FileNotifyRequest
import play.api.Configuration
import play.api.http.Status.NO_CONTENT
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

@Singleton
class SdesConnector @Inject() (
                                httpClient: HttpClientV2,
                                configuration: Configuration
                              )(implicit ec: ExecutionContext) {

  private val service: Service = configuration.get[Service]("microservice.services.sdes")
  private val clientId: String = configuration.get[String]("services.sdes.client-id")
  private val path: Option[String] = Some(configuration.get[String]("microservice.services.sdes.path")).filter(_.nonEmpty)
  private val baseUrl: String = List(Some(service.baseUrl), path).flatten.mkString("/")

  def notify(request: FileNotifyRequest)(implicit hc: HeaderCarrier): Future[Done] =
    httpClient
      .post(url"$baseUrl/notification/fileready")
      .withBody(Json.toJson(request))
      .setHeader("x-client-id" -> clientId)
      .execute
      .flatMap { response =>
        if (response.status == NO_CONTENT) {
          Future.successful(Done)
        } else {
          Future.failed(SdesConnector.UnexpectedResponseException(response.status, response.body))
        }
      }
}

object SdesConnector {

  final case class UnexpectedResponseException(status: Int, body: String) extends Exception with NoStackTrace {
    override def getMessage: String = s"Unexpected response from SDES, status: $status, body: $body"
  }
}