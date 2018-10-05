package org.mikesajak.logviewer.ui.controller

import java.time.Duration
import java.time.format.DateTimeFormatter

import com.google.common.eventbus.Subscribe
import com.typesafe.scalalogging.Logger
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.scene.{control => jfxctrl}
import org.controlsfx.control.textfield.{AutoCompletionBinding, CustomTextField, TextFields}
import org.controlsfx.control.{BreadCrumbBar, PopOver, SegmentedButton}
import org.mikesajak.logviewer.AppController
import org.mikesajak.logviewer.log._
import org.mikesajak.logviewer.log.span.{RequestIdMarkerMatcher, Span, SpanParser, SpanStore}
import org.mikesajak.logviewer.ui.FilterExpressionParser.FilterPredicate
import org.mikesajak.logviewer.ui._
import org.mikesajak.logviewer.util.Measure.measure
import org.mikesajak.logviewer.util.{EventBus, ResourceManager}
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.beans.property.{ObjectProperty, StringProperty}
import scalafx.collections.ObservableBuffer
import scalafx.css.PseudoClass
import scalafx.scene.CacheHint
import scalafx.scene.control._
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.input._
import scalafx.scene.layout.{HBox, Priority, VBox}
import scalafxml.core.macros.sfxml

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.matching.Regex
import scala.util.{Failure, Success}

object LogRow {
  val whiteSpacePattern: Regex = """\r\n|\n|\r""".r

  val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
}

case class LogRow(index: Int, logEntry: LogEntry, resourceMgr: ResourceManager) {
  import LogRow._

  val idx = StringProperty(index.toString)
  val timestamp = StringProperty(logEntry.id.timestamp.toString)
  val source = StringProperty(logEntry.id.source.name)
  val file = StringProperty(logEntry.id.source.file)
  val level = StringProperty(logEntry.level.toString)
  val thread = StringProperty(logEntry.thread)
  val session = StringProperty(logEntry.sessionId)
  val spans = StringProperty("")
  val requestId = StringProperty(logEntry.requestId)
  val userId = StringProperty(logEntry.userId)
  val body = StringProperty(whiteSpacePattern.replaceAllIn(logEntry.body, "\\\\n"))
  val observableLogEntry = ObjectProperty(logEntry)
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
                         spansColumn: TableColumn[LogRow, LogEntry], // TODO: use specialized type for spans value (not string)
                         requestColumn: TableColumn[LogRow, String],
                         userColumn: TableColumn[LogRow, String],
                         bodyColumn: TableColumn[LogRow, String],

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

                         selEntryVBox: VBox,
                         selectedEntryTextArea: TextArea,
                         selectedEntryBreadCrumbBar: BreadCrumbBar[(String, String)],

                         pendingSpansTextArea: TextArea,

                         filterExpressionParser: FilterExpressionParser,
                         appController: AppController,
                         resourceMgr: ResourceManager,
                         eventBus: EventBus) {
  private implicit val logger: Logger = Logger[LogTableController]

  private var tableRows: ObservableList[LogRow] = ObservableBuffer[LogRow]()
  private var currentPredicate: Option[FilterPredicate] = None
  private var filterStack = IndexedSeq[FilterPredicate]()

  private var logStore: LogStore = ImmutableMemoryLogStore.empty
  private var spanStore: SpanStore = SpanStore.empty
  private var spanSlots: IndexedSeq[SpanRow] = IndexedSeq.empty

  private val logLevelStyleClassMap = Map[LogLevel, String](
    LogLevel.Error   -> "error",
    LogLevel.Warning -> "warn",
    LogLevel.Info    -> "info",
    LogLevel.Debug   -> "debug",
    LogLevel.Trace   -> "trace"
  )

  private var logLevelFilterSelection: Map[LogLevel, Boolean] = LogLevel.values.map(_ -> true).toMap

  init()

  def init() {
    splitPane.vgrow = Priority.Always

    setupTableView()

    setupMessageDetailsPanel()

    setupToolsButtons()

    setupSearchControls()

    setupFilterControls()


    initLogRows(ImmutableMemoryLogStore.empty, SpanStore.empty, None)

    eventBus.register(this)
  }

  private def setupTableView(): Unit = {
    idColumn.cellValueFactory = { _.value.idx }

    sourceColumn.cellValueFactory = { _.value.source }
    sourceColumn.cellFactory = prepareColumnCellFactory(basicColumnMenuItems("source"), Some("source"))

    fileColumn.cellValueFactory = { _.value.file }
    fileColumn.cellFactory = prepareColumnCellFactory(basicColumnMenuItems("file"), Some("file"))

    timestampColumn.cellValueFactory = { _.value.timestamp }
    timestampColumn.cellFactory = prepareColumnCellFactory(basicColumnMenuItems("timestamp"))

    levelColumn.cellValueFactory = { t => ObjectProperty(t.value.logEntry.level) }
    levelColumn.cellFactory = { tc: TableColumn[LogRow, LogLevel] =>
      new TableCell[LogRow, LogLevel]() { cell =>
        item.onChange { (_, _, newLogLevel) =>
          text = if (newLogLevel != null) newLogLevel.toString else null
          graphic = if (newLogLevel != null) findIconFor(newLogLevel).orNull else null

          if (!tableRow.value.isEmpty) {
            val value = tableRow.value.item.value
            if (value != null) {
              val logEntry = value.asInstanceOf[LogRow].logEntry
              logLevelStyleClassMap.foreach { case (level, pc) =>
                cell.delegate.pseudoClassStateChanged(PseudoClass(pc), if (level == logEntry.level) true else false)
              }
            }
          }
        }

        addCellContextMenu(cell, columnContextMenuItems(tc.text.value))
      }
    }

    threadColumn.cellValueFactory = { _.value.thread }
    threadColumn.cellFactory = prepareColumnCellFactory(columnContextMenuItems("thread"), Some("thread"))

    sessionColumn.cellValueFactory = { _.value.session }
    sessionColumn.cellFactory = prepareColumnCellFactory(columnContextMenuItems("session"), Some("session"))


    spansColumn.cellValueFactory = { _.value.observableLogEntry }
    spansColumn.cellFactory = { tc: TableColumn[LogRow, LogEntry] =>
      new TableCell[LogRow, LogEntry]() { cell =>
        item.onChange { (_, _, newLogEntry) =>
          text = "x"//if (newLogEntry != null) newLogEntry.toString else ""
          graphic = if (newLogEntry != null) {
            val curSlot = spanSlots(tableRow.value.getIndex)
            spanImageCreator.drawSpans2(curSlot.slots, s"RequestId:${newLogEntry.requestId}", newLogEntry.id)
          } else null
        }
      }
    }

    requestColumn.cellValueFactory = { _.value.requestId }
    requestColumn.cellFactory = { tc: TableColumn[LogRow, String] =>
      new TableCell[LogRow, String]() { cell =>
        item.onChange { (_,_, newValue) =>
          text = newValue
          graphic = if (newValue != null) {
            val spanId = s"RequestId:$newValue"
            val span = spanStore.get(spanId)
            val row = tableRow.value.item.value.asInstanceOf[LogRow]
            if (row != null) spanImageCreator.getSpanIcon(span, row.logEntry.id)
            else null
          } else null
          //          style = s"-fx-background-color: #${sourceColors.getOrElse(newValue, "ffffff")};"
          //          this.pseudoClassStateChanged()
        }

        addCellContextMenu(cell, columnContextMenuItems("request"))
      }
    }


//      prepareColumnCellFactory(columnContextMenuItems("request"), Some("request"))

    userColumn.cellValueFactory = { _.value.userId }
    userColumn.cellFactory = prepareColumnCellFactory(columnContextMenuItems("user"), Some("user"))

    bodyColumn.cellValueFactory = { _.value.body }
    bodyColumn.cellFactory = prepareColumnCellFactory(bodyColumnContetxMenuItems())
  }

  private def getSpansForEntry(newLogEntry: LogEntry, rowIdx: Int) = {
    val curSpans = spanStore.get(newLogEntry.id.timestamp).toSet
    val fromTime = logTableView.items.value(math.max(0, rowIdx - 30)).logEntry.id.timestamp
    val toTime = logTableView.items.value(math.min(logTableView.items.value.size - 1, rowIdx + 30)).logEntry.id.timestamp
    val spansToShow = spanStore.get(fromTime, toTime)
                      .map(s => s.name -> curSpans.contains(s))
    spansToShow
  }

  val spanImageCreator = new SpanImageCreator

  private def prepareColumnCellFactory(contextMenuItemsFunc: TableCell[LogRow, _] => Seq[MenuItem],
                                       colorPool: Option[String] = None) = { tc: TableColumn[LogRow, String] =>
    new TableCell[LogRow, String]() { cell =>
      item.onChange { (_,_, newValue) =>
        text = newValue
        graphic = if (newValue != null)
                    colorPool.map(p => spanImageCreator.getColorBoxFor(newValue, 8)).orNull
                  else null
        //          style = s"-fx-background-color: #${sourceColors.getOrElse(newValue, "ffffff")};"
        //          this.pseudoClassStateChanged()
      }

      addCellContextMenu(cell, contextMenuItemsFunc)
    }
  }

  private def addCellContextMenu(cell: TableCell[LogRow, _], itemsFunc: TableCell[LogRow,_] => Seq[MenuItem]): Unit = {
    var ctxMenuVisible = false
    cell.onMouseClicked = { me: MouseEvent => me.button match {
      case MouseButton.Secondary if !ctxMenuVisible =>
        ctxMenuVisible = true

        new ContextMenu(itemsFunc(cell): _*) {
          onHidden = we => ctxMenuVisible = false
        }.show(cell, me.screenX, me.screenY)
      case _ =>
    }}
  }

  private def columnContextMenuItems(column: String) = { cell: TableCell[LogRow, _] =>
    Seq(
         new MenuItem {
           text = s"Copy $column value to clipboard"
           graphic = new ImageView(resourceMgr.getIcon("icons8-copy-to-clipboard-16.png"))
           onAction = { ae =>
             val content = new ClipboardContent
             content.putString(cell.text.value)
             Clipboard.systemClipboard.content = content
           }
        },
        new MenuItem {
          text = s"Filter list by $column value"
          graphic = new ImageView(resourceMgr.getIcon("icons8-filter-16.png"))
          onAction = { ae =>
            println(s"TODO: Set filter to ${cell.text.value}")
          }
        })
  }

  private def basicColumnMenuItems(column: String) = { cell: TableCell[LogRow, _] =>
    Seq(
         new MenuItem {
           text = s"Copy $column value to clipboard"
           graphic = new ImageView(resourceMgr.getIcon("icons8-copy-to-clipboard-16.png"))
           onAction = { ae =>
             val content = new ClipboardContent
             content.putString(cell.text.value)
             Clipboard.systemClipboard.content = content
           }
         })
  }

  private def bodyColumnContetxMenuItems() = { cell: TableCell[LogRow, _] =>
    Seq(
         new MenuItem {
           text = s"Copy message body to clipboard"
           graphic = new ImageView(resourceMgr.getIcon("icons8-copy-to-clipboard-16.png"))
           onAction = { ae =>
             val content = new ClipboardContent
             val logRow = cell.tableRow.value.item.value.asInstanceOf[LogRow]
             content.putString(logRow.logEntry.body)
             Clipboard.systemClipboard.content = content
           }
         },
         new MenuItem {
           text = s"Copy original message to clipboard"
           graphic = new ImageView(resourceMgr.getIcon("icons8-copy-to-clipboard-16.png"))
           onAction = { ae =>
             val content = new ClipboardContent
             val logRow = cell.tableRow.value.item.value.asInstanceOf[LogRow]
             content.putString(logRow.logEntry.rawMessage)
             Clipboard.systemClipboard.content = content
           }
         })
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

        val spans = spanStore.get(entry.id)

        val spansStr = spans.map(s => s"${s.category}: ${s.name} ${s.begin}-${s.end}")
            .foldLeft("")(_ + "\n" + _)

        pendingSpansTextArea.text = s"Spans(${spans.size}):\n$spansStr"
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
          // TODO: refactor this, use common parser/log store processor
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
            },
            measure("Building span store") { () =>
              new SpanParser(new RequestIdMarkerMatcher) // TODO: use already configured spanParser (during loading e.g. in LogParserMgr)
                .buildSpanStore(logStore)
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
        logTableView.scrollTo(math.max(0, foundIdx - 5))
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
      filterExpressionParser.buildPredicate(filterTextField.text.value, logLevelFilterSelection) match {
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
    setNewLogStore(request.logStore, request.spanStore)
  }

  private def setNewLogStore(newLogStore: LogStore, newSpanStore: SpanStore) {
    logger.debug(s"New log requset received, ${newLogStore.size} entries, and ${newSpanStore.size} spans")

    measure("Setting table rows") { () =>
      initLogRows(newLogStore, newSpanStore, currentPredicate)
    }
  }

  private def initLogRows(logStore: LogStore, spanStore: SpanStore, predicate: Option[FilterPredicate]): Unit = {
    this.logStore = logStore
    this.spanStore = spanStore

    val logRowList = new MappedIndexedObservableList[LogRow, LogEntry](logStore,
      (index, entry) => new LogRow(index, entry, resourceMgr))
    tableRows = new CachedObservableList(logRowList)

    setFilterPredicate(predicate)
  }

  private def setFilterPredicate(predicateOption: Option[FilterPredicate]): Unit = {
    val filteredItems = predicateOption.map { predicate =>
      measure("Preparing filtered view") { () =>
        new FilteredObservableList[LogRow](tableRows, predicate)
      }
    }.getOrElse(tableRows)

    this.spanSlots = measure("Calculating span slots") { () => parseSpanSlots(filteredItems) }

    logTableView.items = filteredItems
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

  private def parseSpanSlots(logRows: Seq[LogRow]): IndexedSeq[SpanRow] = {
    val slots = logRows.foldLeft(mutable.Buffer(SpanRow(IndexedSeq(), Map()))) { (curList, row) =>
      val spanRow = getCurRow(curList.last, spanStore.get(row.logEntry.id))
      curList += spanRow
    }

    slots.drop(1).toIndexedSeq // TODO: try to avoid copying...
  }

  private def getCurRow(prevRow: SpanRow, spans: Seq[Span]): SpanRow = {
    val curSlots = mutable.IndexedSeq.tabulate(prevRow.slots.size) { i =>
      val curSlot = prevRow.slots(i)
      if (spans.contains(curSlot)) curSlot
      else null
    }

    val remainingSpans = spans.filter(s => !curSlots.contains(s))
    remainingSpans.foreach { s =>
      curSlots.indexWhere(s => s == null) match {
        case idx if idx >= 0 => curSlots(idx) = s
        case _ =>
      }
    }

    val lastRemainingSlots = spans.filter(s => !curSlots.contains(s))

    val resultSlots = curSlots ++ lastRemainingSlots

    val lastFilledIdx = resultSlots.lastIndexWhere(s => s != null)
    val strippedSlots =
      if (lastFilledIdx >= 0 && lastFilledIdx < resultSlots.size - 1)
        resultSlots.take(lastFilledIdx + 1)
      else resultSlots

    SpanRow(strippedSlots, strippedSlots.zipWithIndex.toMap)
  }

  case class SpanRow(slots: IndexedSeq[Span], slotsMap: Map[Span, Int])

}
