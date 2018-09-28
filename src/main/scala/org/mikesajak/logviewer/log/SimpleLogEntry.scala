package org.mikesajak.logviewer.log

class SimpleLogEntry(override val id: LogId,
                     override val level: LogLevel,
                     override val thread: String,
                     override val sessionId: String,
                     override val requestId: String,
                     override val userId: String,
                     override val rawMessage: String,
                     override val bodyIdx: Int)
    extends LogEntry {
}
