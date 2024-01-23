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

import better.files.File
import com.codahale.metrics.{Gauge, MetricRegistry}
import logging.Logging
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.{Inject, Singleton}

@Singleton
class FileSystemMetricsService @Inject() (
                                           configuration: Configuration,
                                           metrics: Metrics
                                         ) extends Logging {

  private val tmpDir: File = File(configuration.get[String]("play.temporaryFile.dir"))
    .createDirectories()

  private val metricRegistry: MetricRegistry = metrics.defaultRegistry

  logger.info("Starting file system metrics collection")
  metricRegistry.register("temporary-directory.size", new Gauge[Long] {
    override def getValue: Long = tmpDir.size()
  })
}
