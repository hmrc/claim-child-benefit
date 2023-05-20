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

import connectors.IndividualTraceConnector
import logging.Logging
import models.{IndividualTraceCacheItem, IndividualTraceRequest}
import play.api.http.Status.{BAD_GATEWAY, GATEWAY_TIMEOUT, SERVICE_UNAVAILABLE}
import repositories.IndividualTraceCacheRepository
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import javax.inject.Inject
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class IndividualTraceService @Inject()(
                                        connector: IndividualTraceConnector,
                                        cache: IndividualTraceCacheRepository,
                                        retryService: RetryService
                                      )(implicit ec: ExecutionContext) extends Logging {

  def trace(traceRequest: IndividualTraceRequest)
           (implicit hc: HeaderCarrier): Future[Boolean] =
    cache.get(traceRequest).flatMap {
      _.map(x => Future.successful(x.exists))
        .getOrElse {
          doTrace(traceRequest).flatMap {
            result =>
              cache.set(traceRequest, result)
                .map(_ => result)
                .recover {
                  case e: Exception =>
                    logger.warn("Error caching individual trace result", e.getMessage)
                    result
                }
          }
        }
    }

  private def doTrace(traceRequest: IndividualTraceRequest)
                     (implicit hc: HeaderCarrier): Future[Boolean] =
    retryService.retry(
      connector.trace(traceRequest),
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
