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

package services

import connectors.RelationshipDetailsConnector
import logging.Logging
import models.RelationshipDetails
import play.api.http.Status.{BAD_GATEWAY, GATEWAY_TIMEOUT, SERVICE_UNAVAILABLE}
import repositories.RelationshipDetailsCacheRepository
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import javax.inject.Inject
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class RelationshipDetailsService @Inject()(
                                            connector: RelationshipDetailsConnector,
                                            cache: RelationshipDetailsCacheRepository,
                                            retryService: RetryService
                                          )(implicit ec: ExecutionContext) extends Logging {

  def getRelationshipDetails(nino: String)(implicit hc: HeaderCarrier): Future[RelationshipDetails] =
    cache.get(nino).flatMap {
      _.map(Future.successful)
        .getOrElse {
          getDetails(nino).flatMap {
            result =>
              cache.set(nino, result)
                .map(_ => result)
                .recover {
                  case e: Exception =>
                    logger.warn("Error caching relationship details", e.getMessage)
                    result
                }
          }
        }
    }

  private def getDetails(nino: String)(implicit hc: HeaderCarrier): Future[RelationshipDetails] =
    retryService.retry(
      connector.getRelationships(nino),
      delay = 1.second,
      backoff = RetryService.BackoffStrategy.exponential,
      maxAttempts = 3,
      retriable = shouldRetry
    )

  private val retriableStatusCodes: Set[Int] = Set(BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT)

  private def shouldRetry(e: Throwable): Boolean =
    e match {
      case e: UpstreamErrorResponse => retriableStatusCodes.contains(e.statusCode)
      case _ => false
    }
}
