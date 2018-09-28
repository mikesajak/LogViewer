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
