package org.mikesajak.logviewer.log

import java.time.LocalDateTime

trait LogEntry {
  def id: LogId
  def timestamp: LocalDateTime
  def directory: String
  def file: String
  def level: LogLevel
  def thread: String
  def sessionId: String
  def requestId: String
  def userId: String
  def body: String

  def rawMessage: String

  override def toString = s"LogEntry(id=$id, time=$timestamp, dil=$directory, file/$file, level=$level, thread=$thread, session=$sessionId, request=$requestId, user=$userId, body=$body)"
}
