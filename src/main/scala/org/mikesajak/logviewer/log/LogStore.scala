package org.mikesajak.logviewer.log

trait LogStore {
//  def entry(id: LogId): Option[LogEntry]
  def entry(idx: Long): LogEntry
  def entries: IndexedSeq[LogEntry]
//  def entries(startIdx: Long, endIdx: Long): Seq[LogEntry]
}


