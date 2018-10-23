package org.mikesajak.logviewer.log

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import com.google.common.base.Stopwatch
import com.google.common.eventbus.Subscribe
import com.typesafe.scalalogging.Logger
import org.mikesajak.logviewer.log.parser._
import org.mikesajak.logviewer.log.span.{RequestIdMarkerMatcher, SpanParser, SpanStore}
import org.mikesajak.logviewer.util.EventBus
import org.mikesajak.logviewer.util.Measure.measure
import org.mikesajak.logviewer.{AppendLogRequest, OpenLogRequest}

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
      try {
        val logStoreBuilder =
          new ImmutableMemoryLogStore.Builder()
        //        new log.NitriteLogStore.MappedBuilder("test.db")

        parseLogs(request.files, logStoreBuilder, logStoreBuilder.threadSafe)

        val logStore = measure("Sorting entries and building log store") { () =>
          logStoreBuilder.build()
        }

        val spanStore = measure("Parsing spans and building span store") { () =>
          val spanParser = new SpanParser(new RequestIdMarkerMatcher) // TODO: configurable/pluggable matchers etc.
          spanParser.buildSpanStore(logStore)
        }

        logger.info(s"Created new log store with ${logStore.size} entries, and span store with ${spanStore.size} spans.")

        globalState.currentLogStore = logStore
        eventBus.publish(SetNewLogEntries(logStore, spanStore))
      } catch {
        case e: Exception => logger.error("Exception ocurred during parsing", e)
      }
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

      val spanStore = measure("Parsing spans and building span store") { () =>
        val spanParser = new SpanParser(new RequestIdMarkerMatcher) // TODO: configurable/pluggable matchers etc.
        spanParser.buildSpanStore(logStore)
      }

      globalState.currentLogStore = logStore
      eventBus.publish(SetNewLogEntries(logStore, spanStore))
    }
  }

  private def parseLogs(inputPaths: Seq[File], logStoreBuilder: LogStoreBuilder, multithreaded: Boolean): Unit = {
    val logSources2Parse = inputPaths.view
                           .flatMap(f => traverseDir20(f, logFilePattern))
                           .toList

    // TODO: smarter log source definition - use list of input directories, and map directly to source dir->file

    logger.debug(s"Found ${logSources2Parse.length} files to parse: $logSources2Parse")

    val completedCount = new AtomicInteger()

    measure(s"Parsing ${logSources2Parse.size} log files from ${inputPaths.map(_.getName)}") { () =>
      val inputData = if (multithreaded) logSources2Parse.par else logSources2Parse
      inputData.foreach { logSource =>
        val stopwatch = Stopwatch.createStarted()

        val parserContext = new SimpleFileParserContext(logSource)
        val idGenerator = new SimpleLogIdGenerator
        val logEntryParser = new SimpleLogEntryParser(parserContext, idGenerator)
        val dataSource = new SimpleFileLogDataSource(logSource.file)
        val resultIterator = new LogParserIterator2(dataSource.lines, logEntryParser).flatten
            .grouped(BATCH_SIZE)

        resultIterator.foreach(batch => logStoreBuilder.add(batch))

        val completed = completedCount.incrementAndGet()
        eventBus.publish(ParseProgress(completed.toFloat / logSources2Parse.length,
          s"Parsing $completed/${logSources2Parse.length} log from ${new File(logSource.file).getName} finished in $stopwatch. Current logStore size is ${logStoreBuilder.size}"))

      }
    }
  }

  private def logSourceFromFile(logFile: File) = LogSource(logFile.getParentFile.getName, logFile.getAbsolutePath)
  private def logSourceFromFile(dir: File, logFile: File) = LogSource(dir.getName, logFile.getAbsolutePath)

  private def quickScanLogs(inputPaths: Seq[File]) = {
    val filesToParse = inputPaths.view
                       .map(f => f -> (if(f.isDirectory) traverseDir2(f, logFilePattern, Some(f))
                                       else traverseDir2(f, logFilePattern, None)))
                       .toList

    for ((path, logSource) <- filesToParse) {

    }

    print(s"$filesToParse")
  }

  case class LogFileStats()

  private def traverseDir(file: File, namePattern: Regex) : Seq[File] = file match {
    case f if f.isFile => if (nameMatches(f, namePattern)) List(f) else List.empty
    case d =>
      val (subFiles, subDirs) = d.listFiles().partition(f => f.isFile)
      val resultFiles = subFiles.filter(f => nameMatches(f, namePattern))
      (subDirs foldLeft resultFiles)( (acc, dir) => acc ++ traverseDir(dir, namePattern))
  }

  def nameMatches(f: File, pattern: Regex) = pattern.findFirstMatchIn(f.getName).isDefined

  private def traverseDir20(file: File, namePattern: Regex): Seq[LogSource] = file match {
    case f if f.isFile => if (nameMatches(f, namePattern)) List(logSourceFromFile(f)) else List.empty
    case d if d.isDirectory =>
      val (subFiles, subDirs) = d.listFiles.partition(_.isFile)
      val subFileSources = subFiles.filter(f => nameMatches(f, namePattern)).map(logSourceFromFile)
      subDirs.foldLeft(subFileSources)((acc, dir) => acc ++ traverseDir2(dir, namePattern, Some(dir)))
  }

  private def traverseDir2(file: File, namePattern: Regex, parent: Option[File]) : Seq[LogSource] = file match {
    case f if f.isFile => if (namePattern.findFirstMatchIn(f.getName).isDefined)
      List(logSourceFromFile(f)) else List.empty
    case d =>
      val (subFiles, subDirs) = d.listFiles().partition(f => f.isFile)
      val resultFiles = subFiles.filter(f => namePattern.findFirstMatchIn(f.getName).isDefined)
        .map(f => parent.map(p => logSourceFromFile(p, f)).getOrElse(logSourceFromFile(f)))
      (subDirs foldLeft resultFiles)( (acc, dir) => acc ++ traverseDir2(dir, namePattern, parent))
  }

//  private def traverseDir3(file: File, namePattern: Regex, root: Boolean) : Seq[LogSource] = file match {
//    case f if f.isFile => if (namePattern.findFirstMatchIn(f.getName).isDefined)
//      List(logSourceFromFile(f)) else List.empty
//    case d =>
//      val (subFiles, subDirs) = d.listFiles().partition(f => f.isFile)
//      val resultFiles = subFiles.filter(f => namePattern.findFirstMatchIn(f.getName).isDefined)
//                        .map(f => if (root))
//      (subDirs foldLeft resultFiles)( (acc, dir) => acc ++ traverseDir3(dir, namePattern, root))
//  }
}

case class SetNewLogEntries(logStore: LogStore, spanStore: SpanStore)
case class AppendLogEntries(logStoreBuilder: ImmutableMemoryLogStore.Builder, spanStore: SpanStore) // TODO: do something smarter than that

case class ParseProgress(progress: Float, message: String)