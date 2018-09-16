package org.mikesajak.logviewer.log.parser

import org.mikesajak.logviewer.log.LogEntry

trait LogEntryParser {
  def matchBeginning(line: String): Boolean
  def parse(entryData: Seq[String]) : Option[LogEntry]
}

