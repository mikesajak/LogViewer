package org.mikesajak.logviewer.ui.controller

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.google.common.eventbus.Subscribe
import com.typesafe.scalalogging.Logger
import groovy.lang.GroovyShell
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.collections.ObservableList
import javafx.geometry.Insets
import org.controlsfx.control.{BreadCrumbBar, PopOver, SegmentedButton}
import org.mikesajak.logviewer.log._
import org.mikesajak.logviewer.ui.{CachedObservableList, ColorGen, FilteredObservableList, MappedIndexedObservableList}
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
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout.{Priority, VBox}
import scalafxml.core.macros.sfxml

import scala.collection.JavaConverters._
import scala.util.matching.Regex

object LogRow {
  val whiteSpacePattern: Regex = """\r\n|\n|\r""".r

  val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
}

case class LogRow(index: Int, logEntry: LogEntry, resourceMgr: ResourceManager) {
  import LogRow._

  val idx = new StringProperty(index.toString)
  val timestamp = new StringProperty(dateTimeFormatter.format(logEntry.id.timestamp))
  val source = new StringProperty(logEntry.id.source.name)
  val file = new StringProperty(logEntry.id.source.file)
  val level = new StringProperty(logEntry.level.toString)
  val thread = new StringProperty(logEntry.thread)
  val session = new StringProperty(logEntry.sessionId)
  val requestId = new StringProperty(logEntry.requestId)
  val userId = new StringProperty(logEntry.userId)
  val body = new StringProperty(whiteSpacePattern.replaceAllIn(logEntry.body, "\\\\n"))
}

@sfxml
class LogTableController(logTableView: TableView[LogRow],
                         idColumn: TableColumn[LogRow, String],
                         sourceColumn: TableColumn[LogRow, String],
                         fileColumn: TableColumn[LogRow, String],
                         timestampColumn: TableColumn[LogRow, String],
                         levelColumn: TableColumn[LogRow, LogLevel],
                         threadColumn: TableColumn[LogRow, String],
                         sessionColumn: TableColumn[LogRow, String],
                         requestColumn: TableColumn[LogRow, String],
                         userColumn: TableColumn[LogRow, String],
                         bodyColumn: TableColumn[LogRow, String],

                         selEntryVBox: VBox,
                         selEntryTextArea: TextArea,

                         searchCombo: ComboBox[String], clearSearchButton: Button,
                         filterCombo: ComboBox[String], clearFilterButton: Button,
                         filtersTreeView: TreeView[String],
                         addFilterButton: Button,
                         replaceFilterButton: Button,
                         addLogLevelFilterButton: Button,

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

  private var sourceColors = Map[String, String]()
  private var threadColors = Map[String, String]()
  private var sessionColors = Map[String, String]()
  private var userColors = Map[String, String]()

  def init() {
    logTableView.selectionModel.value.selectionMode = SelectionMode.Multiple

    idColumn.cellValueFactory = {
      _.value.idx
    }
    sourceColumn.cellValueFactory = {
      _.value.source
    }
    sourceColumn.cellFactory = { tc: TableColumn[LogRow, String] =>
      new TableCell[LogRow, String]() {
        item.onChange { (_,_, newValue) =>
          text = newValue
          style = s"-fx-background-color: #${sourceColors.getOrElse(newValue, "ffffff")};"
        }
      }
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
    threadColumn.cellFactory = { tc: TableColumn[LogRow, String] =>
      new TableCell[LogRow, String]() {
        item.onChange { (_, _, newValue) =>
          text = newValue
          style = s"-fx-background-color: #${threadColors.getOrElse(newValue, "ffffff")};"
        }
      }
    }
    sessionColumn.cellValueFactory = {
      _.value.session
    }
    sessionColumn.cellFactory = { tc: TableColumn[LogRow, String] =>
      new TableCell[LogRow, String]() {
        item.onChange { (_, _, newValue) =>
          text = newValue
          style = s"-fx-background-color: #${sessionColors.getOrElse(newValue, "ffffff")};"
        }
      }
    }
    requestColumn.cellValueFactory = {
      _.value.requestId
    }
    userColumn.cellValueFactory = {
      _.value.userId
    }
    userColumn.cellFactory = { tc: TableColumn[LogRow, String] =>
      new TableCell[LogRow, String]() {
        item.onChange { (_, _, newValue) =>
          text = newValue
          style = s"-fx-background-color: #${userColors.getOrElse(newValue, "ffffff")};"
        }
      }
    }
    bodyColumn.cellValueFactory = {
      _.value.body
    }

//    logTableView.rowFactory = { tableView =>
//      val row = new TableRow[LogRow]()

//      row.handleEvent(MouseEvent.MouseClicked) { event: MouseEvent =>
//        if (!row.isEmpty) {
//          event.button match {
//            case MouseButton.Primary =>
////              val entry = row.item.value.logEntry
////              selEntryTextArea.text = s"<id=${entry.id}> <level=${entry.level}> <thread=${entry.thread}> " +
////                s"<sessionId=${entry.sessionId}> <requestId=${entry.requestId}> <userId=${entry.userId}>" +
////                s"\n\n${entry.rawMessage}"
//            case MouseButton.Secondary =>
//            case MouseButton.Middle =>
//            case _ =>
//          }
//        }
//      }
//      row
//    }

    logTableView.selectionModel.value.selectedItemProperty().addListener((obs, oldSelRow, newSelRow) => {
      val entry = newSelRow.logEntry
      val breadCrumbBar = new BreadCrumbBar[String]()
      val model = BreadCrumbBar.buildTreeModel(newSelRow.index.toString,
                                               entry.id.source.name, entry.id.source.file,
                                               entry.id.timestamp.toString, entry.level.toString, entry.thread,
                                               entry.sessionId, entry.requestId, entry.userId, "")
      breadCrumbBar.setSelectedCrumb(model.getParent)
      breadCrumbBar.setAutoNavigationEnabled(false)
      selEntryVBox.children.setAll(Seq(breadCrumbBar, selEntryTextArea.delegate).asJava)

      selEntryTextArea.text = s"<id=${entry.id}> <level=${entry.level}> <thread=${entry.thread}> " +
        s"<sessionId=${entry.sessionId}> <requestId=${entry.requestId}> <userId=${entry.userId}>" +
        s"\n\n${entry.rawMessage}"
    })

    splitPane.vgrow = Priority.Always

    initLogRows(ImmutableMemoryLogStore.empty, None)

    filtersTreeView.root = new TreeItem("")
    addLogLevelFilterButton.onAction = { ae =>
      val popOver = new PopOver()
      val levelToggles = LogLevel.values.map(l => new javafx.scene.control.ToggleButton(l.toString))
      val levelsSegButton = new SegmentedButton(levelToggles: _*)
      levelsSegButton.setToggleGroup(null) // allow multi selection

      val setButton = new Button {
        text = "Set"
        onAction = {
          println("TODO: Set log level filter")
          ae => popOver.hide()
        }
      }


      val hbox = new javafx.scene.layout.HBox(5, levelsSegButton, setButton)
      hbox.margin = new Insets(10)

      popOver.setContentNode(hbox)
      popOver.setArrowLocation(PopOver.ArrowLocation.TOP_RIGHT)
      popOver.setDetachable(false)
      popOver.show(addLogLevelFilterButton.delegate)
    }

    searchCombo.onAction = (ae: ActionEvent) => {
      val queryText = searchCombo.value.value
      logger.info(s"Search combo ENTER, text=${queryText}")
      filtersTreeView.root.value.children.add(new TreeItem(queryText))
    }

    val knownWords = Seq("id", "directory", "file", "level", "thread", "sessionId", "requestId", "userId", "body", "rawMessage")

    filterCombo.editor.value.textProperty().addListener(new ChangeListener[String]() {
      override def changed(observable: ObservableValue[_ <: String], oldValue: String, newValue: String): Unit = {
//        val text = filterCombo.editor.text.get()
        val text = newValue
        val cursorIdx = filterCombo.editor.value.getCaretPosition
        println(s"Key typed: $text, cursor=$cursorIdx")
        val prevDotIdx = text.lastIndexOf('.', cursorIdx)
        if (prevDotIdx < cursorIdx) {
          val lastSegment = text.substring(prevDotIdx, cursorIdx)
          if (lastSegment.length > 1) {
            knownWords.find(str => str.startsWith(lastSegment))
            .foreach(matchingWord => println(s"TODO: Show hint: ($lastSegment)${matchingWord.substring(lastSegment.length)}"))
          }
        }
      }
    })

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
    val logEntry = new SimpleLogEntry(LogId(LogSource("testDir", "testFile"), time, 0),
      LogLevel.Info, "thread-1", "session-1234","reqest-1234", "testuser",
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

    initColorMaps()

    val logRowList = new MappedIndexedObservableList[LogRow, LogEntry](logStore,
      (index, entry) => new LogRow(index, entry, resourceMgr))
    tableRows = new CachedObservableList(logRowList)
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
        val firstTimestamp = logStore.first.id.timestamp
        val lastTimestamp = logStore.last.id.timestamp
        s"${logStore.size} log total entries, ${logTableView.items.value.size} filtered log entries, time range: ${LogRow.dateTimeFormatter.format(firstTimestamp)} - ${LogRow.dateTimeFormatter.format(lastTimestamp)}"
      }

    Platform.runLater {
      statusLabel.text = statusMessage
    }
  }

  private def initColorMaps(): Unit = {
    val colorGen = new ColorGen

    def initMap(values: Set[String], reservePool: Int) = {
      val result = values.map(s => s -> colorGen.nextColor() ).toMap
      for (i <- values.size until reservePool)
        colorGen.nextColor()
      result
    }

    sourceColors = initMap(logStore.indexes.sources, 100)
    threadColors = initMap(logStore.indexes.threads, 200)
    sessionColors = initMap(logStore.indexes.sessions, 200)
    userColors = initMap(logStore.indexes.users, 200)
  }

}
