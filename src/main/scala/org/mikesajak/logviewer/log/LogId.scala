package org.mikesajak.logviewer.log

import java.time.LocalDateTime

case class LogId(source: LogSource, timestamp: LocalDateTime, ordinal: Int)
