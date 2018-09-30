package org.mikesajak.logviewer.log.parser

import org.mikesajak.logviewer.log._

class SimpleLogEntryParser(parserContext: ParserContext, idGenerator: IdGenerator) extends LogEntryParser {
  private val logLineStartPattern = """(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3})\s+(ERROR|WARNING|WARN|INFO|DEBUG|TRACE)""".r
  private val LogLinePattern = """^(?s)(?<timestamp>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}[\.,]\d{3})\s+(?<level>ERROR|WARNING|WARN|INFO|DEBUG|TRACE)\s+\[(?<thread>.*?)\]\s+\[<?(?:(?<sessionId>.*?),(?<requestId>.*?),by,(?<userId>.*?))?>?\]\s+(?<body>.+)$""".r

  private val timestampParser = new TimestampParser

  override def matchBeginning(line: String): Boolean =
    logLineStartPattern.findPrefixOf(line).isDefined

  override def parse(entryData: Seq[String]): Option[LogEntry] = {
    val entryStr = (entryData foldLeft new StringBuilder)((sb, line) => sb.append(line).append("\n"))
        .dropRight(1)
        .toString

    entryStr match {
      case LogLinePattern(timestampStr, levelStr, thread, sessionId, requestId, userId, body) =>
        val timestamp = timestampParser.parse(timestampStr)
        Some(new SimpleLogEntry(idGenerator.genId(parserContext, timestamp), LogLevel.of(levelStr),
                                thread, sessionId, requestId, userId, entryStr, entryStr.length - body.length))

      case _ => None
    }
  }
}
