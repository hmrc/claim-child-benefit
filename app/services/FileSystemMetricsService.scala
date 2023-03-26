package services

import better.files.File
import com.codahale.metrics.{Gauge, MetricRegistry}
import com.kenshoo.play.metrics.Metrics
import logging.Logging
import play.api.Configuration

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
