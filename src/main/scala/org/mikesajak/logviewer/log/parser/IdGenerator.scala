package org.mikesajak.logviewer.log.parser

import org.mikesajak.logviewer.log._

import scala.collection.mutable

trait ParserContext {
  def source: LogSource
}

class SimpleFileParserContext(override val source: LogSource) extends ParserContext {
  def this(directory: String, file: String) = this(LogSource(directory, file))
}

trait IdGenerator {
  def genId(parserContext: ParserContext, timestamp: Timestamp): LogId
}

class SimpleLogIdGenerator extends IdGenerator {
  private val timestampMap = mutable.Map[IdKey, Int]()

  case class IdKey(source: LogSource, timestamp: Timestamp)

  override def genId(parserContext: ParserContext, timestamp: Timestamp): LogId = {
    val key = IdKey(parserContext.source, timestamp)
    val id = LogId(parserContext.source, timestamp, timestampMap.getOrElse(key, 0))

    val count = timestampMap.getOrElse(key, 0) + 1
    timestampMap(key) = count

    id
  }
}