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
import play.api.Configuration
import play.api.libs.json.JsObject
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CbsProxyConnector @Inject()(
                               configuration: Configuration,
                               httpClient: HttpClientV2
                             )(implicit ec: ExecutionContext) {

  private val service: Service = configuration.get[Service]("microservice.services.cbs")
  private val environment: String = configuration.get[String]("microservice.services.cbs.environment")
  private val bearerToken: String = configuration.get[String]("microservice.services.cbs.auth")

  def submit(data: JsObject, correlationId: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    httpClient
      .post(url"${service.baseUrl}/child-benefit/claim")
      .setHeader(
        "Authorization" -> s"Bearer $bearerToken",
        "Environment"   -> environment,
        "CorrelationId" -> correlationId
      )
      .withBody(data)
      .execute
  }
}