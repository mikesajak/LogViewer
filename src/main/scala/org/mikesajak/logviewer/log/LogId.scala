package org.mikesajak.logviewer.log

import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDateTime}

object Timestamp {
  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

  def apply(logTime: LocalDateTime): Timestamp = Timestamp(logTime, 0)
}

case class Timestamp(logTime: LocalDateTime, var offset: Long) extends Ordered[Timestamp] {
  def time: LocalDateTime = logTime.plus(Duration.ofMillis(offset))
  override def compare(that: Timestamp): Int =  time compareTo that.time
  override def toString: String = Timestamp.formatter.format(time)

  def plus(duration: Duration) = Timestamp(logTime.plus(duration), offset)
  def minus(duration: Duration) = Timestamp(logTime.minus(duration), offset)

  def isBefore(timestamp: Timestamp): Boolean = time.isBefore(timestamp.time)
  def isEqOrBefore(timestamp: Timestamp): Boolean = {
    val t1 = time
    val t2 = timestamp.time
    t1.isEqual(t2) || t1.isBefore(t2)
  }
  def isAfter(timestamp: Timestamp): Boolean = time.isAfter(timestamp.time)
  def isEqOrAfter(timestamp: Timestamp): Boolean = {
    val t1 = time
    val t2 = timestamp.time
    t1.isEqual(t2) || t1.isAfter(t2)
  }
  def isEqual(timestamp: Timestamp): Boolean = time.isEqual(timestamp.time)
}

case class LogId(source: LogSource, timestamp: Timestamp, ordinal: Int) extends Ordered[LogId] {
  override def compare(o: LogId): Int =
    timestamp.compareTo(o.timestamp) match {
      case 0 => ordinal.compare(o.ordinal) match {
          case 0 => source.compare(o.source)
          case d @ _ => d
        }
      case d @ _ => d
    }
}
