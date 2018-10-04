package org.mikesajak.logviewer.log.span

import java.time.Duration

import org.mikesajak.logviewer.log.{LogId, Timestamp}

case class Span(category: String, name: String, logIds: Seq[LogId], begin: Timestamp, end: Timestamp) {
  def id = s"$category:$name"
  def duration: Duration = Duration.between(begin.time, end.time)
  def contains(time: Timestamp): Boolean = contains(begin, end, time)
  def overlaps(startTime: Timestamp, endTime: Timestamp): Boolean = {
    contains(startTime) || contains(endTime) ||
     contains(startTime, endTime, begin) || contains(startTime, endTime, end)
  }

  private def contains(startTime: Timestamp, endTime: Timestamp, time: Timestamp) =
    startTime.isEqOrBefore(time) && end.isEqOrAfter(time)
}
