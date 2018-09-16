package org.mikesajak.logviewer.log.parser

import org.mikesajak.logviewer.log.LogId

trait IdGenerator {
  def nextId(): LogId
}

class SimpleLogIdGenerator(directory: String, file: String, var index: Long = 0) extends IdGenerator {
  override def nextId(): LogId = {
    val id = LogId(directory, file, index)
    index += 1
    id
  }
}