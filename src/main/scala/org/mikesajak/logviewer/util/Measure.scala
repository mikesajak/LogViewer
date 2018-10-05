package org.mikesajak.logviewer.util

import com.google.common.base.Stopwatch
import com.typesafe.scalalogging.Logger
import org.mikesajak.logviewer.util.LoggingLevel.Debug

object Measure {
  def measure[A](f: () => A)(logF: Stopwatch => Unit): A = {
    val stopwatch = Stopwatch.createStarted()
    val result = f()
    stopwatch.stop()
    logF(stopwatch)
    result
  }

  def measure[A](logger: Logger, name: String)(f: () => A): A = {
    val stopwatch = Stopwatch.createStarted()
    val result = f()
    stopwatch.stop()
    logger.debug(s"$name finished in $stopwatch")
    result
  }

  def measure[A](name: String, logLevel: LoggingLevel = Debug)(f: () => A)(implicit logger: Logger): A = {
    val stopwatch = Stopwatch.createStarted()
    try {
      Logging.log(s"$name - started...", logLevel)
      val result = f()
      stopwatch.stop()
      Logging.log(s"$name - finished in $stopwatch", logLevel)
      result
    } catch {
      case e: Exception =>
        Logging.log(s"$name - finished with exception in $stopwatch", e, LoggingLevel.Warning)
        throw e
    }
  }
}
