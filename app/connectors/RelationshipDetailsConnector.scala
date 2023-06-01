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
import models.RelationshipDetails
import play.api.Configuration
import play.api.http.HeaderNames
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits.readFromJson

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RelationshipDetailsConnector @Inject()(
                                              configuration: Configuration,
                                              httpClient: HttpClientV2
                                            )(implicit ec: ExecutionContext) {

  private val service: Service = configuration.get[Service]("microservice.services.relationship-details")
  private val apiKey: String = configuration.get[String]("microservice.services.relationship-details.auth")
  private val originatorId: String = configuration.get[String]("microservice.services.relationship-details.originator-id")
  private val environment: String = configuration.get[String]("microservice.services.relationship-details.environment")

  def getRelationships(nino: String, correlationId: String = UUID.randomUUID().toString)
                      (implicit hc: HeaderCarrier): Future[RelationshipDetails] = {

    val trimmedNino = nino.take(8)

    httpClient.get(url"${service.baseUrl}/individuals/relationship/$trimmedNino")
      .setHeader(HeaderNames.AUTHORIZATION -> s"Bearer $apiKey")
      .setHeader("OriginatorId" -> originatorId)
      .setHeader("Environment" -> environment)
      .setHeader("CorrelationId" -> correlationId)
      .execute[RelationshipDetails]
  }
}
