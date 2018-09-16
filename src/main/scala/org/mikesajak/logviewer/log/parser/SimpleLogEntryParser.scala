package org.mikesajak.logviewer.log.parser

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.mikesajak.logviewer.log._

class SimpleLogEntryParser(idGenerator: IdGenerator) extends LogEntryParser {
  private val logLineStartPattern = """(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3})\s+(ERROR|WARNING|WARN|INFO|DEBUG|TRACE)""".r
  private val LogLinePattern = """^(?s)(?<timestamp>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}[\.,]\d{3})\s+(?<level>ERROR|WARNING|WARN|INFO|DEBUG|TRACE)\s+\[(?<thread>.*?)\]\s+\[(?:(?<sessionId>.*?),(?<requestId>.*?),by,(?<userId>.*?))?\]\s+(?<body>.+)$""".r
  private val dateTimeFormatters = Seq(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"),
                                       DateTimeFormatter.ofPattern("dd-MM-yyy HH:mm:ss"),
                                       DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")).view

  override def matchBeginning(line: String): Boolean =
    logLineStartPattern.findPrefixOf(line).isDefined

  override def parse(entryData: Seq[String]): Option[LogEntry] = {
    val entryStr = (entryData foldLeft new StringBuilder)((sb, line) => sb.append(line).append("\n"))
        .dropRight(1)
        .toString

    entryStr match {
      case LogLinePattern(timestampStr, levelStr, thread, sessionId, requestId, userId, body) =>
        val timestamp = dateTimeFormatters.map(df => tryParseDate(timestampStr, df))
                                          .collectFirst { case Some(ts) => ts }
                                          .get // TODO what if date does not match - add error handler

        Some(new SimpleLogEntry(idGenerator.nextId(), timestamp, LogLevel.of(levelStr),
                                thread, sessionId, requestId, userId, entryStr, entryStr.length - body.length))

      case _ => None
    }
  }

  private def tryParseDate(text: String, dtf: DateTimeFormatter)=
    try {
      Some(LocalDateTime.parse(text, dtf))
    } catch {
      case e: Exception => None
    }
}
