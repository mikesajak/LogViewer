package org.mikesajak.logviewer.log

import java.time.LocalDateTime

import javafx.collections.ObservableList

trait LogStore extends ObservableList[LogEntry] {
  def entries: IndexedSeq[LogEntry]
  def isEmpty: Boolean = entries.isEmpty
  def nonEmpty: Boolean = entries.isEmpty
  def range(start: LocalDateTime, end: LocalDateTime): LogStore
}


