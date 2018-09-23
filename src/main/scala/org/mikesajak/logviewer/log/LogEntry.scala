package org.mikesajak.logviewer.log

case class LogSource(name: String, file: String) extends Ordered[LogSource] {
  override def compare(that: LogSource): Int =
    name.compareTo(that.name) match {
      case 0 => file.compareTo(that.file)
      case d @ _ => d
    }
}

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
  override def compare(that: LogEntry): Int = id.compare(that.id)

  override def toString = s"LogEntry(id=$id, level=$level, thread=$thread, session=$sessionId, request=$requestId, user=$userId, body=$bodyIdx)"
}
