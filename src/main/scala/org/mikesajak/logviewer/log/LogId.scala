package org.mikesajak.logviewer.log

import java.time.LocalDateTime

case class LogId(source: LogSource, timestamp: LocalDateTime, ordinal: Int) extends Ordered[LogId] {
  override def compare(o: LogId): Int =
    source.compare(o.source) match {
      case 0 => timestamp.compareTo(o.timestamp) match {
          case 0 => ordinal.compare(o.ordinal)
          case d @ _ => d
        }
      case d @ _ => d
    }
}
