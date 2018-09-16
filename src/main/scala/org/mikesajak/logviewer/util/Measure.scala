package org.mikesajak.logviewer.util

import com.google.common.base.Stopwatch
import com.typesafe.scalalogging.Logger

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

  def measure[A](name: String)(f: () => A)(implicit logger: Logger): A = {
    val stopwatch = Stopwatch.createStarted()
    val result = f()
    stopwatch.stop()
    logger.debug(s"$name finished in $stopwatch")
    result
  }
}
