package org.mikesajak.logviewer.log

import com.google.common.eventbus.Subscribe
import com.typesafe.scalalogging.Logger
import org.mikesajak.logviewer.OpenLogRequest
import org.mikesajak.logviewer.log.parser._
import org.mikesajak.logviewer.util.{EventBus, Measure}

import scala.concurrent.Future

class LogParserMgr(eventBus: EventBus) {
  private implicit val logger = Logger[LogParserMgr]

  eventBus.register(this)

  import scala.concurrent.ExecutionContext.Implicits.global

  @Subscribe
  def handleOpenLog(request: OpenLogRequest): Unit = {
    Future {
      val idGenerator = new SimpleLogIdGenerator(request.file.getParentFile.getName, request.file.getName,0)
      val dataSource = new SimpleFileLogDataSource(request.file)
      val logEntryParser = new SimpleLogEntryParser(idGenerator)

      logger.debug(s"Start parsing log from ${request.file.getName}")

      val resultIterator = new LogParserIterator2(dataSource.lines, logEntryParser).flatten

      val entries = Measure.measure("Parsing log") { () =>
        resultIterator.toList
      }

      val logStore = Measure.measure("Creating log store") { () =>
        SimpleMemoryLogStore(entries)
      }

      eventBus.publish(NewLogOpened(logStore))
    }
  }
}

case class NewLogOpened(logStore: LogStore)
