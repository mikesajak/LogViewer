package org.mikesajak.logviewer.log

import java.time.LocalDateTime

import org.dizitart.no2.{Document, Nitrite}
import org.dizitart.no2.mapper.{Mappable, NitriteMapper}
import scala.collection.JavaConverters._

object NitriteLogStore {
  class Builder(dbFileName: String) {
    private val db = Nitrite.builder()
                     .compressed()
                     .filePath(dbFileName)
                     .openOrCreate()

    private val logEntryCollection = db.getCollection("logEntries")

    def mkDocument(logEntry: LogEntry): Document = {
      new Document()
        .put("directory", logEntry.directory)
        .put("file", logEntry.file)
        .put("timestamp", logEntry.timestamp)
        .put("logLevel", logEntry.level)
        .put("thread", logEntry.thread)
        .put("sessionId", logEntry.sessionId)
        .put("requestId", logEntry.requestId)
        .put("userId", logEntry.userId)
        .put("bodyIdx", logEntry.bodyIdx)
        .put("rawMessage", logEntry.rawMessage)
    }

    class LogEntryMapper(var logEntry: LogEntry) extends Mappable {
      override def write(mapper: NitriteMapper): Document = {
        mkDocument(logEntry)
      }

      override def read(mapper: NitriteMapper, document: Document): Unit = {
        val dir = document.get("directory").asInstanceOf[String]
        val file = document.get("file").asInstanceOf[String]
        val timestamp = document.get("timestamp").asInstanceOf[LocalDateTime]

        logEntry = new SimpleLogEntry(LogId(dir, file, timestamp),
          timestamp,
          document.get("logLevel", classOf[LogLevel]),
          document.get("thread", classOf[String]),
          document.get("sessionId", classOf[String]),
          document.get("requestId", classOf[String]),
          document.get("userId", classOf[String]),
          document.get("rawMessage", classOf[String]),
          document.get("bodyIdx", classOf[Int]))
      }
    }

    def add(logEntry: LogEntry): Unit = {
      val doc = mkDocument(logEntry)
      logEntryCollection.insert(doc)
    }

    def add(logEntries: Seq[LogEntry]): Unit = {
      val docs = logEntries.map(entry => mkDocument(entry)).toArray
      logEntryCollection.insert(docs)
    }

  }
}

class NitriteLogStore(dbFileName: String) {
  private val db = Nitrite.builder()
    .compressed()
    .filePath(dbFileName)
    .openOrCreate()



}
