package org.mikesajak.logviewer.log.parser

import java.time.LocalDateTime

import org.mikesajak.logviewer.log._

import scala.collection.mutable

trait ParserContext {
  def source: LogSource
}

class SimpleFileParserContext(override val source: LogSource) extends ParserContext {
  def this(directory: String, file: String) = this(LogSource(directory, file))
}

trait IdGenerator {
  def genId(parserContext: ParserContext, timestamp: LocalDateTime): LogId
}

class SimpleLogIdGenerator extends IdGenerator {
  private val timestampMap = mutable.Map[IdKey, Int]()

  case class IdKey(source: LogSource, timestamp: LocalDateTime)

  override def genId(parserContext: ParserContext, timestamp: LocalDateTime): LogId = {
    val key = IdKey(parserContext.source, timestamp)
    val id = LogId(parserContext.source, timestamp, timestampMap.getOrElse(key, 0))

    val count = timestampMap.getOrElse(key, 0) + 1
    timestampMap(key) = count

    id
  }
}