package org.mikesajak.logviewer.ui.controller

import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDateTime}

import com.google.common.eventbus.Subscribe
import com.typesafe.scalalogging.Logger
import groovy.lang.GroovyShell
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.scene.{control => jfxctrl}
import org.controlsfx.control.textfield.{AutoCompletionBinding, CustomTextField, TextFields}
import org.controlsfx.control.{BreadCrumbBar, PopOver, SegmentedButton}
import org.mikesajak.logviewer.AppController
import org.mikesajak.logviewer.log._
import org.mikesajak.logviewer.ui._
import org.mikesajak.logviewer.util.Measure.measure
import org.mikesajak.logviewer.util.{EventBus, ResourceManager}
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.beans.property.{ObjectProperty, StringProperty}
import scalafx.collections.ObservableBuffer
import scalafx.scene.CacheHint
import scalafx.scene.control._
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.layout.{HBox, Priority, VBox}
import scalafxml.core.macros.sfxml

import scala.collection.JavaConverters._
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

object LogRow {
  val whiteSpacePattern: Regex = """\r\n|\n|\r""".r

  val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
}

case class LogRow(index: Int, logEntry: LogEntry, resourceMgr: ResourceManager) {
  import LogRow._

  val idx = new StringProperty(index.toString)
  val timestamp = new StringProperty(logEntry.id.timestamp.toString)
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
                         selectedEntryTextArea: TextArea,
                         selectedEntryBreadCrumbBar: BreadCrumbBar[(String, String)],

                         searchTextFieldPanel: HBox, // workaround for scalafxml problem with custom controls (e.g. controlsfx)
                         searchHistoryButton: Button,

                         filtersPanel: HBox,
                         filterTextFieldPanel: HBox, // workaround for scalafxml problem with custom controls (e.g. controlsfx)
                         filterHistoryButton: Button,
                         logLevelFilterButton: Button,
                         advancedFiltersButton: Button,

                         timeShiftButton: Button,

                         statusLeftLabel: Label,
                         statusRightLabel: Label,
                         splitPane: SplitPane,

                         appController: AppController,
                         resourceMgr: ResourceManager,
                         eventBus: EventBus) {
  private implicit val logger: Logger = Logger[LogTableController]

  type FilterPredicate = LogRow => Boolean

  private var tableRows: ObservableList[LogRow] = ObservableBuffer[LogRow]()
  private var currentPredicate: Option[FilterPredicate] = None
  private var filterStack = IndexedSeq[FilterPredicate]()

  private var logStore: LogStore = ImmutableMemoryLogStore.empty

  private val logLevelStyleClassMap = Map[LogLevel, String](
    LogLevel.Error   -> "error",
    LogLevel.Warning -> "warn",
    LogLevel.Info    -> "info",
    LogLevel.Debug   -> "debug",
    LogLevel.Trace   -> "trace"
  )

  private var logLevelFilterSelection = LogLevel.values.map(_ -> true).toMap

  private var sourceColors = Map[String, String]()
  private var threadColors = Map[String, String]()
  private var sessionColors = Map[String, String]()
  private var userColors = Map[String, String]()

  init()

  def init() {
    splitPane.vgrow = Priority.Always

    setupTableView()

    setupMessageDetailsPanel()

    setupToolsButtons()

    setupSearchControls()

    setupFilterControls()


    initLogRows(ImmutableMemoryLogStore.empty, None)

    eventBus.register(this)
  }

  private def setupTableView(): Unit = {
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
  }

  private def setupMessageDetailsPanel(): Unit = {
    // re-initialize panel - because of bug in scalafxml that doesn't support custom controls (e.g. ControlsFX)
    selEntryVBox.children.setAll(Seq(selectedEntryBreadCrumbBar, selectedEntryTextArea.delegate).asJava)

    val origCrumbFactory = selectedEntryBreadCrumbBar.getCrumbFactory
    selectedEntryBreadCrumbBar.setCrumbFactory { item =>
      val crumb = origCrumbFactory.call(item)
      val (name, value) = item.getValue
      crumb.setText(value)
      crumb.setTooltip(new Tooltip(s"$name: $value"))
      crumb
    }

    logTableView.selectionModel.value.selectionMode = SelectionMode.Multiple
    logTableView.selectionModel.value.selectedItems.onChange {
      val selectionModel = logTableView.selectionModel.value
      val items = selectionModel.selectedItems

      val selRow = selectionModel.getSelectedItem
      if (items.nonEmpty && selRow != null) {
        val entry = selRow.logEntry

        val entrySegments = Seq("Position" -> selRow.index.toString,
                                "Source" -> entry.id.source.name,
                                "File" -> entry.id.source.file,
                                "Timestamp" -> entry.id.timestamp.toString,
                                "Log level" -> entry.level.toString,
                                "Thread" -> entry.thread,
                                "Session" -> entry.sessionId,
                                "Request" -> entry.requestId,
                                "User" -> entry.userId)
                            .filter(value => value._2 != null)

        val model = BreadCrumbBar.buildTreeModel(entrySegments: _*)
        selectedEntryBreadCrumbBar.setSelectedCrumb(model)
        selectedEntryBreadCrumbBar.setAutoNavigationEnabled(false)

        selectedEntryTextArea.text =
          if (items.size == 1) entry.rawMessage
          else {
            val min = items.head.logEntry.id.timestamp
            val max = items.last.logEntry.id.timestamp
            val diff = Duration.between(min.time, max.time)

            s"Multi selection: ${items.size} rows, range span: $min - $max ($diff)\n\n${entry.rawMessage}"
          }
      }
    }
  }

  private def setupToolsButtons(): Unit = {
    timeShiftButton.onAction = { ae =>
      val timeShiftPanelLayout = "/layout/timeshift-dialog.fxml"
      val (contentPane, ctrl) = UILoader.loadScene[TimeShiftPanelController](timeShiftPanelLayout)

      val dialog = Dialogs.mkModalDialog[(String, Long)](appController.mainStage, contentPane)
      dialog.dialogPane.value.buttonTypes = Seq(ButtonType.Cancel, ButtonType.OK)

      ctrl.init(logStore.indexes.sources.toSeq.sorted, dialog)

      dialog.showAndWait() match {
        case Some((source, offset: Long)) =>
          measure(s"Applying time shift of ${offset}ms to all logs frem $source") { () =>
            logStore.entriesIterator
            .filter(e => e.id.source.name == source)
            .foreach(e => e.id.timestamp.offset = offset)
          }

          val builder = new ImmutableMemoryLogStore.Builder()
          measure("Adding all log entries to new builder") { () =>
            logStore.entriesIterator.foreach(e => builder.add(e))
          }

          setNewLogStore(
            measure("Building and sorting log store") { () =>
              builder.build()
            }
          )
        case _ =>
      }
    }
  }

  private def setupSearchControls(): Unit = {
    val searchTextField = TextFields.createClearableTextField().asInstanceOf[CustomTextField]
    searchTextField.hgrow = Priority.Always
    searchTextField.setLeft(new ImageView(resourceMgr.getIcon("icons8-search-16.png")))

    var previousSearches = List[String]()
    searchTextField.onAction = { ae =>
      val logRows = logTableView.items.value.asScala
      val curPosition = math.max(logTableView.selectionModel.value.focusedIndex, 0)
      val searchText = searchTextField.text.value

      def rowMatch(row: LogRow, text: String) = row.logEntry.rawMessage.contains(text)

//      val searchStartPos = if (rowMatch(logRows(curPosition), searchText)) curPosition + 1
//                           else curPosition
      val searchStartPos = curPosition // handle "next search", start from position +1

      previousSearches = searchText :: previousSearches.filter(search => search != searchText).take(100)

      val foundIdx = logRows.indexWhere(row => rowMatch(row, searchText), searchStartPos)
      if (foundIdx >= 0) {
        logTableView.selectionModel.value.clearAndSelect(foundIdx)
        logTableView.selectionModel.value.focus(foundIdx)
        logTableView.scrollTo(foundIdx)
        statusRightLabel.text = ""
        // TODO: select/hightlight found text in table and raw message panel
      } else Platform.runLater {
        statusRightLabel.text = s"Search query not found any results"
      }
    }
    searchTextFieldPanel.children.setAll(searchTextField)
    TextFields.bindAutoCompletion(searchTextField,
                                  (suggestionRequest: AutoCompletionBinding.ISuggestionRequest) =>
                                    previousSearches.filter(a => a.startsWith(suggestionRequest.getUserText)).asJavaCollection)
  }

  private def setupFilterControls(): Unit = {
//    val knownWords = Seq("id", "directory", "file", "level", "thread", "sessionId", "requestId", "userId", "body", "rawMessage")
//    filterCombo.editor.value.textProperty().addListener(new ChangeListener[String]() {
//      override def changed(observable: ObservableValue[_ <: String], oldValue: String, newValue: String): Unit = {
//        val text = newValue
//        // calculate caret pos by hand, because in this listener carent is always 1 char behind...
//        val diffIdx = oldValue.zip(newValue).indexWhere(e => e._1 != e._2)
//        val cursorIdx = if (diffIdx < 0) text.length else diffIdx
//        val lastSegmentIdx = text.lastIndexOf("entry.", cursorIdx)
//        if (lastSegmentIdx >= 0) {
//          val segStart = math.min(lastSegmentIdx+ 6, text.length)
//          val segEnd = math.max(segStart, cursorIdx)
//          val lastSegment = text.substring(segStart, segEnd)
//          if (lastSegment.length > 0) {
//            val nextSpaceIdx = text.indexWhere(ch => !Character.isLetterOrDigit(ch), segStart)
//            val nextSegmentIdx = if (nextSpaceIdx < 0) text.length else nextSpaceIdx
//            val hints = knownWords.find(str => str.startsWith(lastSegment))
//                        .map(matchingWord => (lastSegment, matchingWord.substring(lastSegment.length), text.substring(segStart, nextSegmentIdx)))
//
//            hints.map(h => s"${h._1}(${h._2})[${h._3}]")
//            .reduceLeftOption((a,b) => a + ", " + b)
//            .foreach(ht => println(s"Filter hints: $ht"))
//          }
//        }
//      }
//    })

    val filterTextField = TextFields.createClearableTextField().asInstanceOf[CustomTextField]
    filterTextField.hgrow = Priority.Always
    filterTextField.setLeft(new ImageView(resourceMgr.getIcon("icons8-filter-16.png")))
    filterTextField.onAction = { ae =>
      buildPredicate(filterTextField.text.value) match {
        case Success(predOpt) =>
          filterTextField.tooltip = null
          filterTextField.setLeft(new ImageView(resourceMgr.getIcon("icons8-filter-16.png")))
          filterTextField.styleClass -= "validation-error"
          setFilterPredicate(predOpt)
        case Failure(exception) =>
          filterTextField.tooltip = s"Filter predicate is not valid. ${exception.getLocalizedMessage}"
          filterTextField.setLeft(new ImageView(resourceMgr.getIcon("icons8-cancel-16.png")))
          filterTextField.styleClass += "validation-error"
      }
    }
    filterTextFieldPanel.children.setAll(filterTextField)

    var filterHistoryPopoverVisible = false
    filterHistoryButton.onAction = { ae =>
      if (!filterHistoryPopoverVisible) {
        filterHistoryPopoverVisible = true

        new PopOverEx {
          title = "Previous filters"
          detachable = false
          autoHide = true
          headerAlwaysVisible = true
          arrowLocation = PopOver.ArrowLocation.TOP_RIGHT
          onHidden = we => filterHistoryPopoverVisible = false
        }.show(filterHistoryButton.delegate)
      }
    }

    var logFilterPopoverVisible = false
    logLevelFilterButton.onAction = { ae =>
      if (!logFilterPopoverVisible) {
        logFilterPopoverVisible = true

        val toggle2LevelMapping = LogLevel.values.map(level => level -> new jfxctrl.ToggleButton(level.toString))
        val levelsSegButton = new SegmentedButton(toggle2LevelMapping.map(_._2): _*)
        levelsSegButton.setToggleGroup(null) // allow multi selection

        toggle2LevelMapping.foreach { case (level, toggle) =>
          toggle.focusTraversable = false
          toggle.selected = logLevelFilterSelection(level)
        }

        new PopOverEx { popOverThis =>
          private val setButton = new Button {
            graphic = new ImageView(resourceMgr.getIcon("icons8-checked-16.png"))
            onAction = { ae =>
              logLevelFilterSelection = toggle2LevelMapping.map { case (level, toggle) => level -> toggle.isSelected }.toMap
              popOverThis.hide()
            }
          }
          val hbox = new javafx.scene.layout.HBox(5, levelsSegButton, setButton)
          hbox.margin = new Insets(10)
          setContentNode(hbox)

          title = "Select log level filters"
          arrowLocation = PopOver.ArrowLocation.TOP_RIGHT
          detachable = false
          headerAlwaysVisible = true
          autoHide = true

          onHidden = we => logFilterPopoverVisible = false
        }.show(logLevelFilterButton.delegate)

      }
    }
  }

  private def buildPredicate(expressionString: String): Try[Option[FilterPredicate]] = {
    val toggleButtonPredicate =
      if (logLevelFilterSelection.forall(_._2 == false)) None
      else logLevelPred(logLevelFilterSelection)

    val expressionPredicate = buildFilterExprPredicate2(expressionString)

    expressionPredicate.map { exprPred =>
      Seq(toggleButtonPredicate, exprPred)
        .flatten
        .reduceLeftOption((resultPred, curPred) => (logRow: LogRow) => resultPred(logRow) && curPred(logRow))
    }
  }

  private def buildFilterExprPredicate2(filterExpression: String): Try[Option[FilterPredicate]] = {
    if (filterExpression.nonEmpty) {
      Try {
        val expressionPredicateTest = parseFilter(filterExpression, suppressExceptions = false)
        // test predicate on some basic data
        testPredicate(expressionPredicateTest)

        parseFilter(filterExpression, suppressExceptions = true)
      }.map(Some(_))
    } else Success(None)
  }

//  private def buildFilterExprPredicate(filterExpression: String): Option[FilterPredicate] = {
//    if (filterExpression.nonEmpty) {
//      val expressionPredicateTest = parseFilter(filterExpression, suppressExceptions = false)
//      // test predicate on some basic data
//      testPredicate(expressionPredicateTest) match {
//        case Some(exception) =>
//          logger.warn(s"Filter predicate is not valid.", exception)
//          filterCombo.editor.value.styleClass += "error"
//          filterValidationError = Some(exceptionMessage(exception))
//          None
//        case None =>
//          filterCombo.editor.value.styleClass -= "error"
//          filterValidationError = None
//          Some(parseFilter(filterExpression, suppressExceptions = true))
//      }
//    } else {
//      filterCombo.editor.value.styleClass -= "error"
//      filterValidationError = None
//      None
//    }
//  }

  private def exceptionMessage(ex: Throwable): String = {
    if (ex.getCause == null || ex.getCause == ex) ex.getLocalizedMessage
    else ex.getLocalizedMessage + "\n" + exceptionMessage(ex.getCause)
  }

  private def testPredicate(pred: FilterPredicate) = {
    val time = Timestamp(LocalDateTime.now())
    val logEntry = new SimpleLogEntry(LogId(LogSource("testDir", "testFile"), time, 0),
      LogLevel.Info, "thread-1", "session-1234","reqest-1234", "testuser",
      "Message 12341234123412341234123412341342", 0)
    val logRow = new LogRow(0, logEntry, resourceMgr)

    pred(logRow)
  }

  private def logLevelPred(selectedLogLevels: Map[LogLevel, Boolean]): Option[FilterPredicate] = {
    selectedLogLevels.flatMap { case (level, selected) => logLevelPred(level, selected) }
      .reduceLeftOption((p1, p2) => (logRow: LogRow) => p1(logRow) || p2(logRow))
  }

  private def logLevelPred(level: LogLevel, selected: Boolean): Option[FilterPredicate] =
    if (selected) Some(logRow => logRow.logEntry.level == level)
    else None

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
          handleException(text, suppressExceptions, new FilterExpressionError(s"Exception occurred during evaluation of filter expression.\nExpression:\n$text\n${e.getLocalizedMessage}", e))
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
    setNewLogStore(request.logStore)
  }

  private def setNewLogStore(newLogStore: LogStore) {
    logger.debug(s"New log requset received, ${newLogStore.size} entries")

    measure("Setting table rows") { () =>
      initLogRows(newLogStore, currentPredicate)
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
    updateStatus()
  }

  private def updateStatus(): Unit = {
    Platform.runLater {
      statusLeftLabel.text =
        if (logStore.isEmpty) s"${logStore.size} log entries."
        else {
          val firstTimestamp = logStore.first.id.timestamp
          val lastTimestamp = logStore.last.id.timestamp
          s"${logStore.size} log total entries, ${logTableView.items.value.size} filtered log entries, time range: $firstTimestamp - $lastTimestamp}"
        }
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
