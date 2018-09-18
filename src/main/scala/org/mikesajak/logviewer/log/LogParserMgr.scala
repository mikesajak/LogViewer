package org.mikesajak.logviewer.log

import java.io.File

import com.google.common.base.Stopwatch
import com.google.common.eventbus.Subscribe
import com.typesafe.scalalogging.Logger
import org.mikesajak.logviewer.OpenLogRequest
import org.mikesajak.logviewer.log.parser._
import org.mikesajak.logviewer.util.EventBus
import org.mikesajak.logviewer.util.Measure.measure

import scala.concurrent.Future
import scala.util.matching.Regex

class LogParserMgr(eventBus: EventBus) {
  private implicit val logger: Logger = Logger[LogParserMgr]

  eventBus.register(this)

  import scala.concurrent.ExecutionContext.Implicits.global

  private val BATCH_SIZE = 100000

  @Subscribe
  def handleOpenLog(request: OpenLogRequest): Unit = {
    val logFilePattern = "aircrews.*\\.log".r // TODO: allow user to type filter

    Future {
      val logStoreBuilder = new ImmutableMemoryLogStore.Builder()

      val filesToParse = request.files.view
          .flatMap(f => traverseDir(f, logFilePattern))

      logger.debug(s"Found ${filesToParse.length} files to parse: $filesToParse")

      val logStore = measure(s"Parsing ${filesToParse.size} log files from ${request.files.map(_.getName)}") { () =>
        for ((inputFile, idx) <- filesToParse.zipWithIndex) {
          val stopwatch = Stopwatch.createStarted()

          val idGenerator = new SimpleLogIdGenerator(inputFile.getParentFile.getName, inputFile.getName)
          val logEntryParser = new SimpleLogEntryParser(idGenerator)
          val dataSource = new SimpleFileLogDataSource(inputFile)
          val resultIterator = new LogParserIterator2(dataSource.lines, logEntryParser).flatten

          logStoreBuilder.add(resultIterator.toSeq)

          eventBus.publish(ParseProgress(idx.toFloat / filesToParse.length,
            s"Parsing $idx/${filesToParse.length} log from ${inputFile.getName} finished in $stopwatch. Current logStore size is ${logStoreBuilder.size}"))
        }

        measure("Sorting entries and building log store") { () =>
          logStoreBuilder.build()
        }
      }

      handleFirstBatch(logStore)
    }
  }

  private def handleFirstBatch(logStore: LogStore) = {
    eventBus.publish(SetNewLogEntries(logStore))
  }

  private def traverseDir(file: File, namePattern: Regex) : Seq[File] = file match {
    case f if f.isFile => if (namePattern.findFirstMatchIn(f.getName).isDefined) List(f) else List.empty
    case d =>
      val (subFiles, subDirs) = d.listFiles().partition(f => f.isFile)
      val resultFiles = subFiles.filter(f => namePattern.findFirstMatchIn(f.getName).isDefined)
      (subDirs foldLeft resultFiles)( (acc, dir) => acc ++ traverseDir(dir, namePattern))
  }

}

case class SetNewLogEntries(logStore: LogStore)//entries: Seq[LogEntry])
//case class AppendLogEntries(entries: Seq[LogEntry])
case class FinishLogEntries()

case class ParseProgress(progress: Float, message: String)