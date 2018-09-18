package org.mikesajak.logviewer.log

import java.time.LocalDateTime

case class LogId(dir: String, file: String, timestamp: LocalDateTime)
