package org.mikesajak.logviewer.ui.controller

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Predicate

import com.google.common.eventbus.Subscribe
import com.typesafe.scalalogging.Logger
import groovy.lang.GroovyShell
import javafx.collections.ObservableList
import org.mikesajak.logviewer.log._
import org.mikesajak.logviewer.ui.MappedObservableList
import org.mikesajak.logviewer.util.Measure.measure
import org.mikesajak.logviewer.util.{EventBus, ResourceManager}
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.beans.property.{ObjectProperty, StringProperty}
import scalafx.collections.ObservableBuffer
import scalafx.collections.transformation.FilteredBuffer
import scalafx.css.PseudoClass
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

class LogRow(index: Int, val logEntry: LogEntry, resourceMgr: ResourceManager) {
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

  def canEqual(other: Any): Boolean = other.isInstanceOf[LogRow]

  override def equals(other: Any): Boolean = other match {
    case that: LogRow =>
      (that canEqual this) &&
        logEntry.id == that.logEntry.id
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(logEntry.id)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
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

                         searchCombo: ComboBox[String],
                         filterCombo: ComboBox[String],

                         errorLevelToggle: ToggleButton, warnLevelToggle: ToggleButton, infoLevelToggle: ToggleButton,
                         debugLevelToggle: ToggleButton, traceLevelToggle: ToggleButton, otherLevelToggle: ToggleButton,

                         statusLabel: Label,
                         splitPane: SplitPane,

                         resourceMgr: ResourceManager,
                         eventBus: EventBus) {
  private implicit val logger: Logger = Logger[LogTableController]

  private var tableRows = ObservableBuffer[LogRow]()
  private var filteredRows = new FilteredBuffer(tableRows)
//  private var sortedRows = new SortedBuffer(filteredRows)

  private val logLevelToggles = Seq(errorLevelToggle, warnLevelToggle, infoLevelToggle, debugLevelToggle, traceLevelToggle)
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
              println("aaa")
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

    initLogRows(new ObservableBuffer[LogEntry]())

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
      logger.info(s"Filter combo action, text=$filterText")
      measure("Setting filter predicate") { () =>
        filteredRows.predicate = buildPredicate()
      }
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
      button.onAction = (ae: ActionEvent) => filteredRows.predicate = buildPredicate()
    }

    eventBus.register(this)
  }

  private def buildPredicate() = {
    val toggleButtonPredicate =
      if (logLevelToggles.forall(!_.isSelected)) null
      else {
        togglePred(errorLevelToggle).and(logLevelPred(LogLevel.Error)) or
          togglePred(warnLevelToggle).and(logLevelPred(LogLevel.Warning)) or
          togglePred(infoLevelToggle).and(logLevelPred(LogLevel.Info)) or
          togglePred(debugLevelToggle).and(logLevelPred(LogLevel.Debug)) or
          togglePred(traceLevelToggle).and(logLevelPred(LogLevel.Trace))
      }

    val filterExpression = filterCombo.editor.text.get
    val expressionPredicate = buildFilterExprPredicate(filterExpression)

    expressionPredicate.map(p => toggleButtonPredicate.and(p))
      .getOrElse(toggleButtonPredicate)
  }

  private def buildFilterExprPredicate(filterExpression: String) = {
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

  private def testPredicate(pred: Predicate[LogRow]) = {
    val time = LocalDateTime.now()
    val logEntry = new SimpleLogEntry(LogId("testDir", "testFile", time),
      time, LogLevel.Info, "thread-1", "session-1234","reqest-1234", "testuser",
      "Message 12341234123412341234123412341342", 0)
    val logRow = new LogRow(0, logEntry, resourceMgr)

    try {
      pred.test(logRow)
      None
    } catch {
      case e: Exception => Some(e)
    }
  }

  private def logLevelPred(level: LogLevel): Predicate[LogRow] = logRow => logRow.logEntry.level == level
  private def togglePred(toggle: ToggleButton): Predicate[LogRow] = logRow => toggle.isSelected

  class InvalidFilterExpression(message: String) extends Exception(message)
  class FilterExpressionError(message: String, cause: Exception) extends Exception(message, cause)

  private def parseFilter(text: String, suppressExceptions: Boolean) = {
    val shell = new GroovyShell()
    val script = shell.parse(text)

    val predicate: Predicate[LogRow] = { logRow =>
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

    predicate
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

//    val rows = toRows(request.logStore.entries)

    measure("Setting table rows") { () =>
//      tableRows.setAll(rows.asJava)
      initLogRows(request.logStore)
    }
  }

  private def initLogRows(logEntries: ObservableList[LogEntry]): Unit = {
    tableRows = new MappedObservableList[LogRow, LogEntry](logEntries, entry => new LogRow(0, entry, resourceMgr))
    filteredRows = new FilteredBuffer(tableRows)
//    sortedRows = new SortedBuffer(filteredRows)

    logTableView.items = filteredRows

    Platform.runLater {
      statusLabel.text = s"${logEntries.size} log entries"
    }
  }

  @Subscribe
  def onLogFinished(request: FinishLogEntries): Unit = {
    logger.debug("Finish adding log enties")
  }

//  private def toRows(entries: Seq[LogEntry]) =
//    measure("Converting log entries into rows") { () =>
//      entries.view.zipWithIndex
//        .map { case (entry,idx) => new LogRow(idx, entry, resourceMgr) }
//  }

}
