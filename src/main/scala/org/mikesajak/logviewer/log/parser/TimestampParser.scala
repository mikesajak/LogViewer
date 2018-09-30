package org.mikesajak.logviewer.log.parser

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.mikesajak.logviewer.log.Timestamp

class TimestampParser {
  private val dateTimeFormatters = Seq(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"),
                                        DateTimeFormatter.ofPattern("dd-MM-yyy HH:mm:ss"),
                                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")).view

  def parse(timestampStr: String): Timestamp = {
    dateTimeFormatters.map(df => tryParseDate(timestampStr, df))
      .collectFirst { case Some(ts) => ts }
      .get // TODO what if date does not match - add error handler
  }

  private def tryParseDate(text: String, dtf: DateTimeFormatter)=
    try {
      Some(Timestamp(LocalDateTime.parse(text, dtf)))
    } catch {
      case e: Exception => None
    }
}
