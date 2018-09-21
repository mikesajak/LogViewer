package org.mikesajak.logviewer.log

trait LogSource

trait LogEntry extends Ordered[LogEntry] {
  def id: LogId
  def level: LogLevel
  def thread: String
  def sessionId: String
  def requestId: String
  def userId: String
  def bodyIdx: Int
  def rawMessage: String

  def body: String = rawMessage.substring(bodyIdx)

  // Ordering
  override def compare(that: LogEntry): Int = id.timestamp.compareTo(that.id.timestamp) // TODO: take into account source and ordinal number

  override def toString = s"LogEntry(id=$id, level=$level, thread=$thread, session=$sessionId, request=$requestId, user=$userId, body=$bodyIdx)"
}
