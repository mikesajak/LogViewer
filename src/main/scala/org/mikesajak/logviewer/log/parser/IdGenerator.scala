package org.mikesajak.logviewer.log.parser

import java.time.LocalDateTime

import org.mikesajak.logviewer.log.LogId

trait IdGenerator {
  def genId(timestamp: LocalDateTime): LogId
}

class SimpleLogIdGenerator(directory: String, file: String) extends IdGenerator {
  override def genId(timestamp: LocalDateTime): LogId = {
    LogId(directory, file, timestamp)
  }
}