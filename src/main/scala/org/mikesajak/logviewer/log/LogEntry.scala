package org.mikesajak.logviewer.log

import java.time.LocalDateTime

trait LogEntry extends Ordering[LogEntry] {
  def id: LogId
  def timestamp: LocalDateTime
  def directory: String
  def file: String
  def level: LogLevel
  def thread: String
  def sessionId: String
  def requestId: String
  def userId: String
  def bodyIdx: Int
  def rawMessage: String

  def body: String = rawMessage.substring(bodyIdx)

  // Ordering
  override def compare(x: LogEntry, y: LogEntry): Int = x.timestamp.compareTo(y.timestamp)

  override def toString = s"LogEntry(id=$id, time=$timestamp, dil=$directory, file/$file, level=$level, thread=$thread, session=$sessionId, request=$requestId, user=$userId, body=$bodyIdx)"
}
