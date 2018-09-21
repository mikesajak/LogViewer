package org.mikesajak.logviewer.log

import java.time.LocalDateTime

import javafx.collections.ObservableList

trait LogStore extends ObservableList[LogEntry] {
  def isEmpty: Boolean
  def nonEmpty: Boolean
  def first: LogEntry
  def last: LogEntry
  def range(start: LocalDateTime, end: LocalDateTime): IndexedSeq[LogEntry]
  def logStoreForRange(start: LocalDateTime, end: LocalDateTime): LogStore
}


