package org.mikesajak.logviewer.ui.controller

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Predicate

import com.google.common.eventbus.Subscribe
import com.typesafe.scalalogging.Logger
import groovy.lang.GroovyShell
import javafx.collections.ObservableList
import org.mikesajak.logviewer.log._
import org.mikesajak.logviewer.ui.{FilteredObservableList, MappedObservableList}
import org.mikesajak.logviewer.util.Measure.measure
import org.mikesajak.logviewer.util.{EventBus, ResourceManager}
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.beans.property.{ObjectProperty, StringProperty}
import scalafx.collections.ObservableBuffer
import scalafx.event.ActionEvent
import scalafx.scene.CacheHint
import scalafx.scene.control._
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.input.{MouseButton, MouseEvent}
import scalafx.scene.layout.Priority
import scalafxml.core.macros.sfxml

import scala.util.matching.Regex

object LogRow {
  val whiteSpacePattern: Regex = """\r\n|\n|\r""".r

  val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
}

case class LogRow(index: Int, logEntry: LogEntry, resourceMgr: ResourceManager) {
  import LogRow._

  val idx = new StringProperty(index.toString)
  val timestamp = new StringProperty(dateTimeFormatter.format(logEntry.timestamp))
  val directory = new StringProperty(logEntry.directory)
  val file = new StringProperty(logEntry.file)
  val level = new StringProperty(logEntry.level.toString)
  val thread = new StringProperty(logEntry.thread)
  val session = new StringProperty(logEntry.sessionId)
  val requestId = new StringProperty(logEntry.requestId)
  val userId = new StringProperty(logEntry.userId)
  val body = new StringProperty(whiteSpacePattern.replaceAllIn(logEntry.body, "\\\\n"))

//  def canEqual(other: Any): Boolean = other.isInstanceOf[LogRow]
//
//  override def equals(other: Any): Boolean = other match {
//    case that: LogRow =>
//      (that canEqual this) &&
//        logEntry.id == that.logEntry.id
//    case _ => false
//  }
//
//  override def hashCode(): Int = {
//    val state = Seq(logEntry.id)
//    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
//  }
}

@sfxml
class LogTableController(logTableView: TableView[LogRow],
                         idColumn: TableColumn[LogRow, String],
                         dirColumn: TableColumn[LogRow, String],
                         fileColumn: TableColumn[LogRow, String],
                         timestampColumn: TableColumn[LogRow, String],
                         levelColumn: TableColumn[LogRow, LogLevel],
                         threadColumn: TableColumn[LogRow, String],
                         sessionColumn: TableColumn[LogRow, String],
                         requestColumn: TableColumn[LogRow, String],
                         userColumn: TableColumn[LogRow, String],
                         bodyColumn: TableColumn[LogRow, String],
                         selEntryTextArea: TextArea,

                         searchCombo: ComboBox[String], clearSearchButton: Button,
                         filterCombo: ComboBox[String], clearFilterButton: Button,

                         errorLevelToggle: ToggleButton, warnLevelToggle: ToggleButton, infoLevelToggle: ToggleButton,
                         debugLevelToggle: ToggleButton, traceLevelToggle: ToggleButton, otherLevelToggle: ToggleButton,

                         statusLabel: Label,
                         splitPane: SplitPane,

                         resourceMgr: ResourceManager,
                         eventBus: EventBus) {
  private implicit val logger: Logger = Logger[LogTableController]

  type FilterPredicate = LogRow => Boolean

  private var tableRows: ObservableList[LogRow] = ObservableBuffer[LogRow]()
  private var currentPredicate: Option[FilterPredicate] = None
  private var filterStack = IndexedSeq[FilterPredicate]()

  private var logStore: LogStore = ImmutableMemoryLogStore.empty

  private val logLevelToggles = Seq(errorLevelToggle, warnLevelToggle, infoLevelToggle, debugLevelToggle, traceLevelToggle)
  private val logLevelToggles2 = Seq((errorLevelToggle, LogLevel.Error),
                                     (warnLevelToggle, LogLevel.Warning),
                                     (infoLevelToggle, LogLevel.Info),
                                     (debugLevelToggle, LogLevel.Debug),
                                     (traceLevelToggle, LogLevel.Trace))
  private val logLevelStyleClassMap = Map[LogLevel, String](
    LogLevel.Error   -> "error",
    LogLevel.Warning -> "warn",
    LogLevel.Info    -> "info",
    LogLevel.Debug   -> "debug",
    LogLevel.Trace   -> "trace"
  )

  private var filterValidationError: Option[String] = None

  init()


  def init() {
    logTableView.selectionModel.value.selectionMode = SelectionMode.Multiple

    idColumn.cellValueFactory = {
      _.value.idx
    }
    dirColumn.cellValueFactory = {
      _.value.directory
    }
    fileColumn.cellValueFactory = {
      _.value.file
    }
    timestampColumn.cellValueFactory = {
      _.value.timestamp
    }
    levelColumn.cellValueFactory = { t => ObjectProperty(t.value.logEntry.level) }
    levelColumn.cellFactory = { tc: TableColumn[LogRow, LogLevel] =>
      new TableCell[LogRow, LogLevel]() {
        item.onChange { (_, _, newLogLevel) =>
          text = if (newLogLevel != null) newLogLevel.toString else null
          graphic = if (newLogLevel != null) findIconFor(newLogLevel).orNull else null

          if (!tableRow.value.isEmpty) {
            val value = tableRow.value.item.value
            if (value != null) {
              val logEntry = value.asInstanceOf[LogRow].logEntry

              tableRow.value.styleClass --= logLevelStyleClassMap.values
              tableRow.value.styleClass -= "other"
              tableRow.value.styleClass += logLevelStyleClassMap.getOrElse(logEntry.level, "other")
            }
          }
        }
      }
    }
    threadColumn.cellValueFactory = {
      _.value.thread
    }
    sessionColumn.cellValueFactory = {
      _.value.session
    }
    requestColumn.cellValueFactory = {
      _.value.requestId
    }
    userColumn.cellValueFactory = {
      _.value.userId
    }
    bodyColumn.cellValueFactory = {
      _.value.body
    }

    logTableView.rowFactory = { tableView =>
      val row = new TableRow[LogRow]()

      row.handleEvent(MouseEvent.MouseClicked) { event: MouseEvent =>
        if (!row.isEmpty) {
          event.button match {
            case MouseButton.Primary => selEntryTextArea.text = row.item.value.logEntry.rawMessage
            case MouseButton.Secondary =>
            case MouseButton.Middle =>
            case _ =>
          }
        }
      }
      row
    }

    splitPane.vgrow = Priority.Always

    initLogRows(ImmutableMemoryLogStore.empty, None)

    searchCombo.onAction = (ae: ActionEvent) => logger.info(s"Search combo ENTER, text=${searchCombo.editor.text.get()}")

    val knownWords = Seq("id", "directory", "file", "level", "thread", "sessionId", "requestId", "userId", "body", "rawMessage")

//    filterCombo.editor.value.handleEvent(KeyEvent.KeyTyped) { event: KeyEvent =>
//      val text = filterCombo.editor.text.get()
//      val cursorIdx = filterCombo.editor.value.getCaretPosition
//      println(s"Key typed: $text, cursor=$cursorIdx")
//      val prevDotIdx = text.lastIndexOf('.', cursorIdx)
//      if (prevDotIdx < cursorIdx) {
//        val lastSegment = text.substring(prevDotIdx, cursorIdx)
//        if (lastSegment.length > 1) {
//          knownWords.find(str => str.startsWith(lastSegment))
//            .foreach(matchingWord => println(s"TODO: Show hint: ($lastSegment)${matchingWord.substring(lastSegment.length)}"))
//        }
//      }
//    }

    filterCombo.onAction = (ae: ActionEvent) => {
      val filterText = filterCombo.editor.text.get()
      logger.debug(s"Filter combo action, text=$filterText")
      setFilterPredicate(buildPredicate())
    }

    val filterValidationTooltip = new Tooltip()
    filterCombo.editor.value.onMouseMoved = (me: MouseEvent) => {
      filterValidationError.foreach { errorMsg =>
        filterValidationTooltip.text = errorMsg
        filterValidationTooltip.show(filterCombo, me.screenX, me.screenY + 15)
      }
    }
    filterCombo.editor.value.onMouseExited = (me: MouseEvent) => {
      filterValidationTooltip.hide()
    }


    logLevelToggles.foreach { button =>
      button.onAction = (ae: ActionEvent) => setFilterPredicate(buildPredicate())
    }

    eventBus.register(this)
  }

  private def buildPredicate(): Option[FilterPredicate] = {
    val toggleButtonPredicate =
      if (logLevelToggles.forall(!_.isSelected)) None
      else logLevelPred(logLevelToggles2)

    val filterExpression = filterCombo.editor.text.get
    val expressionPredicate = buildFilterExprPredicate(filterExpression)

    val predicate = Seq(toggleButtonPredicate, expressionPredicate)
                      .flatten
                      .reduceLeftOption((resultPred, curPred) => (logRow: LogRow) => resultPred(logRow) && curPred(logRow))
    predicate
  }

  private def buildFilterExprPredicate(filterExpression: String): Option[FilterPredicate] = {
    if (filterExpression.nonEmpty) {
      val expressionPredicateTest = parseFilter(filterExpression, suppressExceptions = false)
      // test predicate on some basic data
      testPredicate(expressionPredicateTest) match {
        case Some(exception) =>
          logger.warn(s"Filter predicate is not valid.", exception)
          filterCombo.editor.value.styleClass += "error"
          filterValidationError = Some(exceptionMessage(exception))
          None
        case None =>
          filterCombo.editor.value.styleClass -= "error"
          filterValidationError = None
          Some(parseFilter(filterExpression, suppressExceptions = true))
      }
    } else None
  }

  private def exceptionMessage(ex: Throwable): String = {
    if (ex.getCause == null || ex.getCause == ex) ex.getLocalizedMessage
    else ex.getLocalizedMessage + "\n" + exceptionMessage(ex.getCause)
  }

  private def testPredicate(pred: FilterPredicate) = {
    val time = LocalDateTime.now()
    val logEntry = new SimpleLogEntry(LogId("testDir", "testFile", time),
      time, LogLevel.Info, "thread-1", "session-1234","reqest-1234", "testuser",
      "Message 12341234123412341234123412341342", 0)
    val logRow = new LogRow(0, logEntry, resourceMgr)

    try {
      pred(logRow)
      None
    } catch {
      case e: Exception => Some(e)
    }
  }

  private def logLevelPred(logToggles: Seq[(ToggleButton, LogLevel)]): Option[FilterPredicate] = {
    logToggles.flatMap { case (toggle, level) => logLevelPred(toggle, level) }
      .reduceLeftOption((p1, p2) => (logRow: LogRow) => p1(logRow) || p2(logRow))
  }

  private def logLevelPred(toggle: ToggleButton, level: LogLevel): Option[FilterPredicate] =
    if (toggle.isSelected) Some(logRow => logRow.logEntry.level == level)
    else None //logRow => toggle.isSelected && logRow.logEntry.level == level

  class InvalidFilterExpression(message: String) extends Exception(message)
  class FilterExpressionError(message: String, cause: Exception) extends Exception(message, cause)

  private def parseFilter(text: String, suppressExceptions: Boolean): FilterPredicate = {
    val shell = new GroovyShell()
    val script = shell.parse(text)
    logRow: LogRow => {
      shell.setVariable("entry", logRow.logEntry)
      try {
        val result: AnyRef = script.run()
        //noinspection ComparingUnrelatedTypes
        if (!result.isInstanceOf[Boolean])
          handleException(text, suppressExceptions, new InvalidFilterExpression(s"Filter expression must return Boolean value.\nExpression:\n$text"))
        result.asInstanceOf[Boolean]
      } catch {
        case e: Exception =>
          handleException(text, suppressExceptions, new FilterExpressionError(s"Exception occurred during evaluation of filter expression.\nExpression:\n$text", e))
      }
    }

  }

  private def handleException(expr: String, suppress: Boolean, ex: Exception): Boolean = {
    if (suppress) {
      logger.warn(s"An error occurred during preparing filter expression:\n$expr", ex)
      true
    } else throw ex
  }

  private def findIconFor(level: LogLevel) = {
    val icon = level match {
      case LogLevel.Error => Some(resourceMgr.getIcon("icons8-error4-16.png"))
      case LogLevel.Warning => Some(resourceMgr.getIcon("icons8-warning-16.png"))
      case LogLevel.Info => Some(resourceMgr.getIcon("icons8-dymek-info2-16.png"))
      case LogLevel.Debug => Some(resourceMgr.getIcon("icons8-debug4-16.png"))
      case LogLevel.Trace => Some(resourceMgr.getIcon("icons8-debug3-16.png"))
      case _ => None
    }
    icon.map(newCachedImageView)
  }

  private def newCachedImageView(img: Image): ImageView = {
    val imageView = new ImageView(img)
    imageView.preserveRatio = true
    imageView.cache = true
    imageView.cacheHint = CacheHint.Speed
    imageView
  }

  @Subscribe
  def onLogOpened(request: SetNewLogEntries): Unit = {
    logger.debug(s"New log requset received, ${request.logStore.size} entries")

    measure("Setting table rows") { () =>
      initLogRows(request.logStore, currentPredicate)
    }
  }

  private def initLogRows(logStore: LogStore, predicate: Option[FilterPredicate]): Unit = {
    this.logStore = logStore
    tableRows = new MappedObservableList[LogRow, LogEntry](logStore, entry => new LogRow(0, entry, resourceMgr))
    setFilterPredicate(predicate)
  }

  private def setFilterPredicate(predicateOption: Option[FilterPredicate]): Unit = {

    val visibleItemsList =
      predicateOption.map { predicate =>
        measure("Preparing filtered view") { () =>
          new FilteredObservableList[LogRow](tableRows, predicate)
        }
      }.getOrElse(tableRows)

    logTableView.items = visibleItemsList

    val statusMessage =
      if (logStore.isEmpty) s"${logStore.size} log entries."
      else {
        val firstTimestamp = logStore.first.timestamp
        val lastTimestamp = logStore.last.timestamp
        s"${logStore.size} log total entries, ${logTableView.items.value.size} filtered log entries, time range: ${LogRow.dateTimeFormatter.format(firstTimestamp)} - ${LogRow.dateTimeFormatter.format(lastTimestamp)}"
      }

    Platform.runLater {
      statusLabel.text = statusMessage
    }
  }

}
