package org.mikesajak.logviewer.log

import java.time.LocalDateTime

class SimpleLogEntry(override val id: LogId,
                     override val timestamp: LocalDateTime,
                     override val level: LogLevel,
                     override val thread: String,
                     override val sessionId: String,
                     override val requestId: String,
                     override val userId: String,
                     override val rawMessage: String,
                     bodyIdx: Int)
    extends LogEntry {

  override def body: String = rawMessage.substring(bodyIdx)
  override def directory: String = id.dir
  override def file: String = id.file
}
