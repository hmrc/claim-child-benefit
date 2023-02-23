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
import models.integration.DesignatoryDetails
import play.api.Configuration
import play.api.http.HeaderNames
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IfConnector @Inject() (
                              configuration: Configuration,
                              httpClient: HttpClientV2
                            )(implicit ec: ExecutionContext) {

  private val service: Service = configuration.get[Service]("microservice.services.integration-framework")
  private val apiKey: String = configuration.get[String]("microservice.services.integration-framework.auth")
  private val originatorId: String = configuration.get[String]("microservice.services.integration-framework.originator-id")
  private val environment: String = configuration.get[String]("microservice.services.integration-framework.environment")

  def getDesignatoryDetails(nino: String, correlationId: String = UUID.randomUUID().toString)(implicit hc: HeaderCarrier): Future[DesignatoryDetails] =
    httpClient.get(url"${service.baseUrl}/individuals/details/NINO/$nino")
      .setHeader(HeaderNames.AUTHORIZATION -> apiKey)
      .setHeader("OriginatorId" -> originatorId)
      .setHeader("Environment" -> environment)
      .setHeader("CorrelationId" -> correlationId)
      .execute[DesignatoryDetails]
}
