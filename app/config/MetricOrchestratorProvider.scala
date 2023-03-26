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

package config

import com.kenshoo.play.metrics.Metrics
import models.dmsa.SubmissionItemStatus
import play.api.Configuration
import repositories.SubmissionItemRepository
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}
import uk.gov.hmrc.mongo.metrix.{MetricOrchestrator, MetricRepository, MetricSource}

import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MetricOrchestratorProvider @Inject() (
                                             lockRepository: MongoLockRepository,
                                             metricRepository: MetricRepository,
                                             metrics: Metrics,
                                             submissionItemRepository: SubmissionItemRepository,
                                             configuration: Configuration
                                           ) extends Provider[MetricOrchestrator] {

  private val lockTtl: Duration = configuration.get[Duration]("workers.metric-orchestrator-worker.lock-ttl")
  private val lockService: LockService = LockService(lockRepository, lockId = "metrix-orchestrator", ttl = lockTtl)

  private val metricRegistry = metrics.defaultRegistry

  private val source = new MetricSource {
    override def metrics(implicit ec: ExecutionContext): Future[Map[String, Int]] =
      for {
        countOfSubmitted <- submissionItemRepository.countByStatus(SubmissionItemStatus.Submitted)
        countOfForwarded <- submissionItemRepository.countByStatus(SubmissionItemStatus.Forwarded)
        countOfFailed    <- submissionItemRepository.countByStatus(SubmissionItemStatus.Failed)
        countOfCompleted <- submissionItemRepository.countByStatus(SubmissionItemStatus.Completed)
      } yield Map(
        "submission-item.submitted.count" -> countOfSubmitted.toInt,
        "submission-item.forwarded.count" -> countOfForwarded.toInt,
        "submission-item.failed.count"    -> countOfFailed.toInt,
        "submission-item.completed.count" -> countOfCompleted.toInt,
      )
  }

  override def get(): MetricOrchestrator = new MetricOrchestrator(
    metricSources    = List(source),
    lockService      = lockService,
    metricRepository = metricRepository,
    metricRegistry   = metricRegistry
  )
}