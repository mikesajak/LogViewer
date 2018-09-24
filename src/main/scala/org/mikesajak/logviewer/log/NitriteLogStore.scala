package org.mikesajak.logviewer.log

import java.io.File
import java.time.LocalDateTime

import com.typesafe.scalalogging.Logger
import javafx.collections.ObservableListBase
import org.dizitart.no2.IndexOptions.indexOptions
import org.dizitart.no2._
import org.dizitart.no2.filters.Filters
import org.dizitart.no2.mapper.{Mappable, NitriteMapper}
import org.mikesajak.logviewer.log.NitriteLogStore.LogEntryConverter
import org.mikesajak.logviewer.util.{Check, LoggingLevel}
import org.mikesajak.logviewer.util.Measure.measure

import scala.collection.JavaConverters._
import scala.collection.mutable

object NitriteLogStore {

  object LogEntryConverter {
    def toDocument(logEntry: LogEntry): Document = {
      val id = logEntry.id
      val logIdDoc = toDocument(id)
      new Document()
        .put("logId", logIdDoc)
        .put("sortableLogId", logIdRep(id))
        .put("level", logEntry.level)
        .put("thread", logEntry.thread)
        .put("sessionId", logEntry.sessionId)
        .put("requestId", logEntry.requestId)
        .put("userId", logEntry.userId)
        .put("bodyIdx", logEntry.bodyIdx)
        .put("rawMessage", logEntry.rawMessage)
    }

    def toDocument(logId: LogId): Document = {
      val sourceDoc = new Document()
        .put("name", logId.source.name)
        .put("file", logId.source.file)
      new Document()
        .put("source", sourceDoc)
        .put("timestamp", logId.timestamp)
        .put("ordinal", logId.ordinal)
    }

    def logIdRep(id: LogId) =
      s"${id.source.name}:${id.source.file}:${id.timestamp}:${id.ordinal}"

    def toLogEntry(doc: Document): SimpleLogEntry = {
      new SimpleLogEntry(toLogId(doc.get("logId", classOf[Document])),
        doc.get("level", classOf[LogLevel]),
        doc.get("thread", classOf[String]),
        doc.get("sessionId", classOf[String]),
        doc.get("requestId", classOf[String]),
        doc.get("userId", classOf[String]),
        doc.get("rawMessage", classOf[String]),
        doc.get("bodyIdx", classOf[java.lang.Integer]).toInt)
    }

    def toLogId(doc: Document): LogId =
      LogId(toLogSource(doc.get("source", classOf[Document])),
        doc.get("timestamp", classOf[LocalDateTime]),
        doc.get("ordinal", classOf[java.lang.Integer]).toInt)

    private def toLogSource(doc: Document) =
      LogSource(doc.get("name", classOf[String]), doc.get("file", classOf[String]))
  }

  class NitriteLogEntryWrapper(var logEntry: LogEntry) extends LogEntry with Mappable {
    override def write(mapper: NitriteMapper): Document =
      LogEntryConverter.toDocument(logEntry)

    override def read(mapper: NitriteMapper, document: Document): Unit =
      logEntry = LogEntryConverter.toLogEntry(document)

    override val id: LogId = logEntry.id
    override def level: LogLevel = logEntry.level
    override def thread: String = logEntry.thread
    override def sessionId: String = logEntry.sessionId
    override def requestId: String = logEntry.requestId
    override def userId: String = logEntry.userId
    override def bodyIdx: Int = logEntry.bodyIdx
    override def rawMessage: String = logEntry.rawMessage
  }

  class Builder(dbFileName: String) extends LogStoreBuilder {
    private implicit val logger: Logger = Logger[Builder]
    private val db = Nitrite.builder()
                     .compressed()
                     .filePath(dbFileName)
                     .openOrCreate()

    private val logEntryRepo = db.getRepository("logEntries", classOf[LogEntry])
    createIndexes()

    private def createIndexes(): Unit = {
      if (!logEntryRepo.hasIndex("logId"))
        logEntryRepo.createIndex("logId", indexOptions(IndexType.Unique))

      if (!logEntryRepo.hasIndex("timestamp"))
        logEntryRepo.createIndex("timestamp", indexOptions(IndexType.NonUnique))
    }

    override def threadSafe: Boolean = true

    override def size: Long = logEntryRepo.size()

    override def add(logEntry: LogEntry): Unit = {
      logEntryRepo.insert(logEntry)
    }

    override def add(logEntries: Seq[LogEntry]): Unit = {
      logEntryRepo.insert(logEntries.toArray)
    }

    override def build(): NitriteRepoLogStore = {
      measure("commit") { () => db.commit() }

      db.compact()
      db.close()

      new NitriteRepoLogStore(dbFileName)
    }
  }

  class WrappedBuilder(dbFileName: String) extends LogStoreBuilder {
    private val db = Nitrite.builder()
                     .compressed()
                     .filePath(dbFileName)
                     .openOrCreate()

    private val logEntryRepo = db.getRepository("logEntries", classOf[NitriteLogEntryWrapper])
    if (!logEntryRepo.hasIndex("logId"))
      logEntryRepo.createIndex("logId", indexOptions(IndexType.Unique))

    override def threadSafe: Boolean = true

    override def size: Long = logEntryRepo.size()

    override def add(logEntry: LogEntry): Unit = {
      logEntryRepo.insert(new NitriteLogEntryWrapper(logEntry))
    }

    override def add(logEntries: Seq[LogEntry]): Unit = {
      val nitriteLogEntries = logEntries.view.map(new NitriteLogEntryWrapper(_)).toArray
      logEntryRepo.insert(nitriteLogEntries)
    }

    override def build(): NitriteWrappedLogStore = {
      db.commit()
      db.compact()
      db.close()

      new NitriteWrappedLogStore(dbFileName)
    }
  }

  class MappedBuilder(dbFileName: String) extends LogStoreBuilder {
    private implicit val logger: Logger = Logger[MappedBuilder]
    private val debugDelete = true
    private val logId2NidMap = mutable.SortedMap[LogId, NitriteId]()

    private val db = {
      if (debugDelete) {
        val file = new File(dbFileName)
        if (file.exists()) {
          logger.info(s"Previous database file exists: $file")
          val res = file.delete()
          logger.info(s"  Previous database file deleted: $res")
        }
      }

      Nitrite.builder()
        .compressed()
//        .nitriteMapper(new GSONMapper())
        .filePath(dbFileName)
        .autoCommitBufferSize(10000)
        .disableAutoCompact()
        .openOrCreate()
    }

    private val logEntryColl = db.getCollection("logEntries")
    measure("Creating indexes") { () => createIndexes() }


    override def threadSafe: Boolean = true

    override def size: Long = synchronized { logEntryColl.size() }

    override def add(logEntry: LogEntry): Unit = synchronized {
      val res = logEntryColl.insert(LogEntryConverter.toDocument(logEntry))
      res.asScala.foreach( nid => logId2NidMap += logEntry.id -> nid)
    }

    override def add(logEntries: Seq[LogEntry]): Unit = synchronized {
      val entries = logEntries.view.map(LogEntryConverter.toDocument)
      val res = try {
        logEntryColl.insert(entries.toArray).asScala
      } catch {
        case e: Exception =>
          logger.error(s"Exception occurred while adding entries(${logEntries.size}) to collection. Log entries: $logEntries", e)
          throw e
      }
//      entries.foreach(e => logEntryColl.update(e, true))
//      logEntries.foreach(e => idSet += e.id)
      val uniqRes = res.toSet
      if (logEntries.size != res.size)
        println("!!!")
      if (logEntries.size != uniqRes.size)
        println("???")

      Check.state(logEntries.size == res.size, s"Number inserted ${res.size} is different that number input entries ${logEntries.size}")
      res.zip(logEntries)
        .foreach { case (nid, entry) =>
          if (entry == null || entry.id == null || nid == null){
            println("###")
          }
          if (logId2NidMap.contains(entry.id))
            println("$$$")
          logId2NidMap += entry.id -> nid
        }
      val i = 10
    }

    private def updatePositions(): Unit = {
      logId2NidMap.toSeq.zipWithIndex
      .foreach { case (id, pos) => updatePositionIndex(id, pos) }
    }

    private def updatePositionsBatch(): Unit = {
      val batchSize = 1000
      if (logId2NidMap.size != logEntryColl.size())
        println("%%%")
      logId2NidMap.view.toSeq.zipWithIndex
//      .grouped(batchSize)
//      .foreach(group => updatePositionIndex(group))
//      .foreach { group =>
//        measure(s"Updating $batchSize positions") { () =>
//          group
            .foreach { case (id@(logId, nid), pos) => updatePositionIndex(id, pos) }
//        }
//      }
    }

    private def updatePositionIndex(id: (LogId, NitriteId), pos: Int): Unit = {
//      measure(s"Updating position for $id -> $pos") { () =>
//        val cursor = logEntryColl.find(Filters.eq("sortableLogId", LogEntryConverter.logIdRep(id._1)))
        val doc = logEntryColl.getById(id._2)
        doc.put("position", pos)
        logEntryColl.update(doc)

      }

    private def updatePositionIndex(logIds: Seq[((LogId, NitriteId), Int)]): Unit =
      measure(s"Updating positions for ${logIds.size}(${logIds.head._2})") { () =>
        val idToPosMap = logIds.map(e => e._1._2 -> e._2).toMap
        val cursor = measure("Query", LoggingLevel.Trace) { () =>
//          logEntryColl.find(Filters.in("sortableLogId",
//            logIds.view.map { case ((logId, nid), pos) => LogEntryConverter.logIdRep(logId) }: _*))

          logEntryColl.find(Filters.in("_id", logIds.view.map { case ((logId, nid), pos) => nid }: _*))
        }

        measure("Updating", LoggingLevel.Trace) { () =>
          cursor.iterator().asScala.foreach { doc =>
//            val logId = LogEntryConverter.toLogId(doc.get("logId", classOf[Document]))
            doc.put("position", idToPosMap(doc.getId))
            logEntryColl.update(doc)
          }
        }
      }

    override def build(): LogStore = synchronized {
      measure("Updating positions") { () => updatePositionsBatch() }
      measure("Rebuiding indexes") { () => logEntryColl.rebuildIndex("position", false) }
      measure("Committing") { () => db.commit() }
      measure("Compacting") { () => db.compact() }
      measure("Closing db") { () => db.close() }

      new NitriteCollLogStore(dbFileName)
    }

    private def createIndexes(): Unit = {
      if (!logEntryColl.hasIndex("logId"))
        logEntryColl.createIndex("logId", null)

      if (!logEntryColl.hasIndex("sortableLogId"))
        logEntryColl.createIndex("sortableLogId", null)

      if (!logEntryColl.hasIndex("timestamp"))
        logEntryColl.createIndex("timestamp", indexOptions(IndexType.NonUnique))

      if (!logEntryColl.hasIndex("position"))
        logEntryColl.createIndex("position", null)
    }
  }
}

abstract class NitriteLogStoreBase(dbFileName: String) extends ObservableListBase[LogEntry] with LogStore {
  protected val db: Nitrite = Nitrite.builder()
                              .compressed()
                              .filePath(dbFileName)
                              .openOrCreate()

  override def first: LogEntry = get(0)
  override def last: LogEntry = get(size() - 1)

}

class NitriteCollLogStore(dbFileName: String) extends NitriteLogStoreBase(dbFileName) {
  private implicit val logger: Logger = Logger[NitriteCollLogStore]
  private val logEntryColl = db.getCollection("logEntries")
  private var buffer: Buffer = _
  private val bufferSize = 10000

  class Buffer(startIdx: Int, entries: IndexedSeq[LogEntry]) {
    def hasEntry(index: Int): Boolean = {
      val bufferPos = index - startIdx
      bufferPos >= 0 && bufferPos < entries.size
    }

    def get(index: Int): LogEntry = {
      val bufferPos = index - startIdx
      entries(bufferPos)
    }
  }

  def fetch(index: Int, length: Int): IndexedSeq[LogEntry] =
    measure(s"Fetching $length entries from index $index", LoggingLevel.Trace) { () =>
      val cursor = logEntryColl.find(FindOptions.sort("sortableLogId", SortOrder.Ascending)
                                     .thenLimit(index, length))
      cursor.iterator().asScala.map(LogEntryConverter.toLogEntry).toIndexedSeq
    }

  override def get(index: Int): LogEntry = {
    getEntryByPosition(index)
  }

  private def getBuffered(index: Int): LogEntry = {
    if (buffer == null || !buffer.hasEntry(index)) {
      val startIdx = math.max(0, index - bufferSize/2)
      logger.trace(s"Reinitializing buffer with startIdx=$startIdx, size=$bufferSize")
      buffer = new Buffer(startIdx, fetch(startIdx, bufferSize))
    }

    buffer.get(index)
  }

  private def getEntry(index: Int): LogEntry = measure(s"Getting logEntry $index from collection") { () =>
    val cursor = logEntryColl.find(FindOptions.sort("sortableLogId", SortOrder.Ascending)
                                              .thenLimit(index, 1))

    val res = cursor.firstOrDefault()
    if (res != null ) LogEntryConverter.toLogEntry(res)
    else null
  }

  private def getEntryByPosition(index: Int) = {//measure(s"Getting entry $index by position field") { () =>
    val cursor = logEntryColl.find(Filters.eq("position", index))
    val res = cursor.firstOrDefault()
    val result = if (res != null) LogEntryConverter.toLogEntry(res)
                 else null
    result
  }


  override def size(): Int = logEntryColl.size().toInt

  override def indexes: Indexes = ImmutableIndexes.empty // tmp mock
}

//class BufferedNitriteCollLogStore(logStore: LogStore, bufferSize: Int = 100) extends ObservableListBase[LogEntry] with LogStore {
//
//  private var startRange: Int = -1
//  private var buffer = IndexedSeq[LogEntry]()
//
//  override def first: LogEntry = logStore.first
//  override def last: LogEntry = logStore.last
//  override def get(index: Int): LogEntry = {
//    if (!insideBufferRange(index)) {
//      buffer =
//    }
//    buffer(index - startRange)
//  }
//
//  private def insideBufferRange(index: Int) =
//    startRange > 0 && index >= startRange && index < startRange + bufferSize
//
//
//  def fetch(index: Int, length: Int): IndexedSeq[LogEntry] = {
//    val cursor = logEntryRepo.find(FindOptions.sort("logId", SortOrder.Ascending)
//                                   .thenLimit(index, length))
//    if (cursor.hasMore)
//      cursor.iterator().asScala.toIndexedSeq
//    else null
//  }
//
//  override def size(): Int = logStore.size()
//}

class NitriteRepoLogStore(dbFileName: String) extends NitriteLogStoreBase(dbFileName) {
  private val logEntryRepo = db.getRepository("logEntries", classOf[LogEntry])

  override def get(index: Int): LogEntry = {
    val cursor = logEntryRepo.find(FindOptions.sort("logId", SortOrder.Ascending)
                                   .thenLimit(index, 1))
    if (cursor.hasMore)
      cursor.iterator().next()
    else null
  }
  override def size(): Int = logEntryRepo.size().toInt

  override def indexes: Indexes = ImmutableIndexes.empty // tmp mock
}

class NitriteWrappedLogStore(dbFileName: String) extends NitriteLogStoreBase(dbFileName) {
  private val logEntryRepo = db.getRepository("logEntries", classOf[NitriteLogStore.NitriteLogEntryWrapper])

  override def get(index: Int): LogEntry = {
    val cursor = logEntryRepo.find(FindOptions.sort("logId", SortOrder.Ascending)
                                   .thenLimit(index, 1))
    if (cursor.hasMore)
      cursor.iterator().next().logEntry
    else null
  }
  override def size(): Int = logEntryRepo.size().toInt

  override def indexes: Indexes = ImmutableIndexes.empty // tmp mock
}