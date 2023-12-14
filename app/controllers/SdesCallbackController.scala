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

package controllers

import com.codahale.metrics.{MetricRegistry, Timer}
import com.kenshoo.play.metrics.Metrics
import controllers.SdesCallbackController.SubmissionLockedException
import models.dmsa.{SubmissionItem, SubmissionItemStatus}
import models.sdes.{NotificationCallback, NotificationType}
import play.api.mvc.ControllerComponents
import play.api.{Configuration, Logging}
import repositories.SubmissionItemRepository
import services.{AuditService, RetryService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import java.time.{Clock, Duration}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success
import scala.util.control.NoStackTrace

@Singleton
class SdesCallbackController @Inject() (
                                         override val controllerComponents: ControllerComponents,
                                         submissionItemRepository: SubmissionItemRepository,
                                         clock: Clock,
                                         configuration: Configuration,
                                         retryService: RetryService,
                                         auditService: AuditService,
                                         metrics: Metrics
                                       )(implicit ec: ExecutionContext) extends BackendBaseController with Logging {

  private val lockTtl: Duration = Duration.ofSeconds(configuration.get[Int]("lock-ttl"))

  private val metricRegistry: MetricRegistry = metrics.defaultRegistry
  private val timer: Timer = metricRegistry.timer("supplementary-data.timer")

  def callback = Action.async(parse.json[NotificationCallback]) { implicit request =>
    logger.info(s"SDES Callback received for correlationId: ${request.body.correlationID}, with status: ${request.body.notification}, and failureReason: ${request.body.failureReason.getOrElse("")}")
    retryService.retry(
      submissionItemRepository.getByCorrelationId(request.body.correlationID).flatMap {
        _.map { item =>
          if (isLocked(item)) {
            logger.warn(s"correlationId: ${request.body.correlationID} was locked!")
            Future.failed(SubmissionLockedException(item.sdesCorrelationId))
          } else {
            getNewItemStatus(request.body.notification).map { newStatus =>
              withTimerForItem(item) {
                submissionItemRepository.update(item.id, newStatus, request.body.failureReason)
                  .map(_ => Ok)
              }
            }.getOrElse {
              Future.successful(Ok)
            }.map { result =>
              auditService.auditSupplementaryDataResult(request.body, item)
              result
            }
          }
        }.getOrElse {
          logger.warn(s"SDES Callback received for correlationId: ${request.body.correlationID}, with status: ${request.body.notification} but no matching submission was found")
          Future.successful(NotFound)
        }
      }, retriable = isSubmissionLockedException
    )
  }

  private def getNewItemStatus(notificationType: NotificationType): Option[SubmissionItemStatus] =
    notificationType match {
      case NotificationType.FileProcessed         => Some(SubmissionItemStatus.Completed)
      case NotificationType.FileProcessingFailure => Some(SubmissionItemStatus.Failed)
      case _                                      => None
    }

  private def isLocked(item: SubmissionItem): Boolean =
    item.lockedAt.exists(_.isAfter(clock.instant().minus(lockTtl)))

  private def isSubmissionLockedException(e: Throwable): Boolean =
    e.isInstanceOf[SubmissionLockedException]

  private def withTimerForItem[A](item: SubmissionItem)(future: Future[A]): Future[A] = {
    future.onComplete {
      case Success(_) =>
        timer.update(Duration.between(item.created, clock.instant()))
      case _ => ()
    }
    future
  }
}

object SdesCallbackController {

  final case class SubmissionLockedException(sdesCorrelationId: String) extends Throwable with NoStackTrace {
    override def getMessage: String = s"Item with sdesCorrelationId $sdesCorrelationId was locked"
  }
}
