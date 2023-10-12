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

import connectors.IndividualDetailsConnector
import logging.Logging
import models.{Address, DesignatoryDetails, Name}
import play.api.http.Status.{BAD_GATEWAY, GATEWAY_TIMEOUT, SERVICE_UNAVAILABLE}
import repositories.DesignatoryDetailsCacheRepository
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DesignatoryDetailsService @Inject() (
                                            connector: IndividualDetailsConnector,
                                            cache: DesignatoryDetailsCacheRepository,
                                            retryService: RetryService
                                          )(implicit ec: ExecutionContext) extends Logging {

  def getDesignatoryDetails(nino: String)(implicit hc: HeaderCarrier): Future[DesignatoryDetails] =
    cache.get(nino).flatMap {
      _.map(Future.successful)
        .getOrElse {
          getDetails(nino).flatMap { result =>

            val realName = result.names
              .filter(n => n.nameType == 1 && n.nameEndDate.isEmpty)
              .maxByOption(_.nameSequenceNumber)
              .map(Name(_))

            val knownAs = result.names
              .filter(n => n.nameType == 2 && n.nameEndDate.isEmpty)
              .maxByOption(_.nameSequenceNumber)
              .map(Name(_))

            val residentialAddress = result.addresses
              .filter(a => a.addressType == 1 && a.addressEndDate.isEmpty)
              .maxByOption(_.addressSequenceNumber)
              .map(Address(_))

            val correspondenceAddress = result.addresses
              .filter(a => a.addressType == 2 && a.addressEndDate.isEmpty)
              .maxByOption(_.addressSequenceNumber)
              .map(Address(_))

            val details = DesignatoryDetails(
              dateOfBirth = result.dateOfBirth,
              realName = realName,
              knownAsName = knownAs,
              residentialAddress = residentialAddress,
              correspondenceAddress = correspondenceAddress
            )

            cache.set(nino, details)
              .map(_ => details)
              .recover {
                case e: Exception =>
                  logger.debug("Error caching designatory details", e.getMessage)
                  logger.warn("Error caching designatory details")
                  details
              }
          }
        }
    }

  private def getDetails(nino: String)(implicit hc: HeaderCarrier): Future[models.integration.DesignatoryDetails] =
    retryService.retry(
      connector.getDesignatoryDetails(nino),
      delay = 1.second,
      backoff = RetryService.BackoffStrategy.exponential,
      maxAttempts = 3,
      retriable = shouldRetry
    )

  private val retriableStatusCodes: Set[Int] = Set(BAD_GATEWAY, SERVICE_UNAVAILABLE , GATEWAY_TIMEOUT)

  private def shouldRetry(e: Throwable): Boolean =
    e match {
      case e: UpstreamErrorResponse => retriableStatusCodes.contains(e.statusCode)
      case _                        => false
    }
}
