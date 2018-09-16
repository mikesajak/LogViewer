package org.mikesajak.logviewer.log

import enumeratum._

import scala.collection.immutable

sealed trait LogLevel extends EnumEntry

object LogLevel extends Enum[LogLevel] {
  val values: immutable.IndexedSeq[LogLevel] = findValues

  case object Error extends LogLevel
  case object Warning extends LogLevel
  case object Info extends LogLevel
  case object Debug extends LogLevel
  case object Trace extends LogLevel

  def of(name: String): LogLevel = name.toUpperCase match {
    case "ERROR" => Error
    case "WARNING" | "WARN" => Warning
    case "INFO" => Info
    case "DEBUG" => Debug
    case "TRACE" => Trace
  }
}
