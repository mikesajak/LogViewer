package org.mikesajak.logviewer.util

import com.typesafe.scalalogging.Logger
import enumeratum.{Enum, EnumEntry}

import scala.collection.immutable

sealed trait LoggingLevel extends EnumEntry

object LoggingLevel extends Enum[LoggingLevel] {
  val values: immutable.IndexedSeq[LoggingLevel] = findValues

  case object Error extends LoggingLevel
  case object Warning extends LoggingLevel
  case object Info extends LoggingLevel
  case object Debug extends LoggingLevel
  case object Trace extends LoggingLevel
}

object Logging {
  def log(message: => String, level: LoggingLevel)(implicit logger: Logger): Unit = level match {
    case LoggingLevel.Error => logger.error(message)
    case LoggingLevel.Warning => logger.warn(message)
    case LoggingLevel.Info => logger.info(message)
    case LoggingLevel.Debug => logger.debug(message)
    case LoggingLevel.Trace => logger.trace(message)
  }

  def log(message: => String, exception: Throwable, level: LoggingLevel)(implicit logger: Logger): Unit = level match {
    case LoggingLevel.Error => logger.error(message, exception)
    case LoggingLevel.Warning => logger.warn(message, exception)
    case LoggingLevel.Info => logger.info(message, exception)
    case LoggingLevel.Debug => logger.debug(message, exception)
    case LoggingLevel.Trace => logger.trace(message, exception)
  }
}