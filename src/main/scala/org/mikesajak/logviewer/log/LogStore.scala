package org.mikesajak.logviewer.log

import javafx.collections.ObservableList

trait LogStoreBuilder {
  def size: Long
  def add(logEntry: LogEntry): Unit
  def add(logEntries: Seq[LogEntry]): Unit

  def threadSafe: Boolean

  def build(): LogStore
}

trait Indexes {
  def sources: Set[String]
  def threads: Set[String]
  def sessions: Set[String]
  def users: Set[String]
//  def spansForEntry(logId: LogId): Seq[Span]
  def spansForTime(time: Timestamp)
}

trait LogStore extends ObservableList[LogEntry] {
  def isEmpty: Boolean
  def nonEmpty: Boolean = !isEmpty
  def first: LogEntry
  def last: LogEntry
//  def range(start: LocalDateTime, end: LocalDateTime): IndexedSeq[LogEntry]
//  def logStoreForRange(start: LocalDateTime, end: LocalDateTime): LogStore

  def entriesIterator: Iterator[LogEntry]

  def indexes: Indexes
}


