package org.mikesajak.logviewer.log

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import com.google.common.base.Stopwatch
import com.google.common.eventbus.Subscribe
import com.typesafe.scalalogging.Logger
import org.mikesajak.logviewer.log.parser._
import org.mikesajak.logviewer.util.EventBus
import org.mikesajak.logviewer.util.Measure.measure
import org.mikesajak.logviewer.{AppendLogRequest, OpenLogRequest, log}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.matching.Regex

class LogParserMgr(eventBus: EventBus, globalState: GlobalState) {
  private implicit val logger: Logger = Logger[LogParserMgr]

  eventBus.register(this)

  import scala.concurrent.ExecutionContext.Implicits.global

  private val BATCH_SIZE = 100
  private val logFilePattern = "aircrews.*\\.log".r // TODO: allow user to type filter

  @Subscribe
  def handleOpenLog(request: OpenLogRequest): Unit = {
    Future {
      val logStoreBuilder =
        new ImmutableMemoryLogStore.Builder()
//        new log.NitriteLogStore.MappedBuilder("test.db")

      parseLogs(request.files, logStoreBuilder, logStoreBuilder.threadSafe)

      val logStore = measure("Sorting entries and building log store") { () =>
        logStoreBuilder.build()
      }

      logger.info(s"Created new log store with ${logStore.size} entries.")

      globalState.currentLogStore = logStore
      eventBus.publish(SetNewLogEntries(logStore))
    }
  }

  @Subscribe
  def handleAppendLog(request: AppendLogRequest): Unit = {
//    eventBus.publish(SetNewLogEntries(ImmutableMemoryLogStore.empty)) // release memory, before parsing
    Future {
      val logStoreBuilder = new ImmutableMemoryLogStore.Builder()

      for (e <- globalState.currentLogStore.iterator.asScala)
        logStoreBuilder.add(e)

//      logStoreBuilder.add(globalState.currentLogStore.iterator)
//      globalState.currentLogStore = ImmutableMemoryLogStore.empty

      parseLogs(request.files, logStoreBuilder, logStoreBuilder.threadSafe)

      val logStore = measure("Sorting entries and building log store") { () =>
        logStoreBuilder.build()
      }

      globalState.currentLogStore = logStore
      eventBus.publish(SetNewLogEntries(logStore))
    }
  }

  private def parseLogs(inputPaths: Seq[File], logStoreBuilder: LogStoreBuilder, multithreaded: Boolean): Unit = {
    val filesToParse = inputPaths.view
                         .flatMap(f => traverseDir(f, logFilePattern))
                         .toList

    // TODO: smarter log source definition - use list of input directories, and map directly to source dir->file

    logger.debug(s"Found ${filesToParse.length} files to parse: $filesToParse")

    val completedCount = new AtomicInteger()

    measure(s"Parsing ${filesToParse.size} log files from ${inputPaths.map(_.getName)}") { () =>
      val inputData = if (multithreaded) filesToParse.par else filesToParse
      inputData.foreach { inputFile =>
        val stopwatch = Stopwatch.createStarted()

        val parserContext = new SimpleFileParserContext(inputFile.getParentFile.getName, inputFile.getName)
        val idGenerator = new SimpleLogIdGenerator
        val logEntryParser = new SimpleLogEntryParser(parserContext, idGenerator)
        val dataSource = new SimpleFileLogDataSource(inputFile)
        val resultIterator = new LogParserIterator2(dataSource.lines, logEntryParser).flatten
            .grouped(BATCH_SIZE)

        resultIterator.foreach(batch => logStoreBuilder.add(batch))

        val completed = completedCount.incrementAndGet()
        eventBus.publish(ParseProgress(completed.toFloat / filesToParse.length,
          s"Parsing $completed/${filesToParse.length} log from ${inputFile.getName} finished in $stopwatch. Current logStore size is ${logStoreBuilder.size}"))

      }
    }
  }

  private def traverseDir(file: File, namePattern: Regex) : Seq[File] = file match {
    case f if f.isFile => if (namePattern.findFirstMatchIn(f.getName).isDefined) List(f) else List.empty
    case d =>
      val (subFiles, subDirs) = d.listFiles().partition(f => f.isFile)
      val resultFiles = subFiles.filter(f => namePattern.findFirstMatchIn(f.getName).isDefined)
      (subDirs foldLeft resultFiles)( (acc, dir) => acc ++ traverseDir(dir, namePattern))
  }

}

case class SetNewLogEntries(logStore: LogStore)
case class AppendLogEntries(logStoreBuilder: ImmutableMemoryLogStore.Builder) // TODO: do something smarter than that

case class ParseProgress(progress: Float, message: String)