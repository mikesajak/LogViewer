package org.mikesajak.logviewer.log

import javafx.collections.ObservableList

trait LogStore extends ObservableList[LogEntry] {
//  def entry(id: LogId): Option[LogEntry]
//  def entry(idx: Int): LogEntry
//  def entries: IndexedSeq[LogEntry]
//  def size: Int
//  def entries(startIdx: Long, endIdx: Long): Seq[LogEntry]

  def entries: Seq[LogEntry]
}


