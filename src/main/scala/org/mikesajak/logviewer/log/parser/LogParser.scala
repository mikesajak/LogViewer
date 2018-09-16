package org.mikesajak.logviewer.log.parser

import org.mikesajak.logviewer.log.LogEntry

class LogParser {
  def parse(dataSource: LogDataSource, logEntryParser: LogEntryParser): Iterator[Option[LogEntry]] = {
    new LogParserIterator(dataSource.lines, logEntryParser)
//      .flatten
  }
}
