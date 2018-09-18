package org.mikesajak.logviewer.log
import java.util

import com.typesafe.scalalogging.Logger
import javafx.collections.ObservableListBase

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

class ImmutableMemoryLogStore(input: Seq[LogEntry]) extends ObservableListBase[LogEntry] with LogStore {
  private val entryStore = input.toIndexedSeq
  override def entries: Seq[LogEntry] = entryStore

  override def get(index: Int): LogEntry = entryStore(index)

  override def size(): Int = entryStore.size
}

object ImmutableMemoryLogStore {
  class Builder {
    private val logger = Logger[ImmutableMemoryLogStore.Builder]
    private val entries = new ArrayBuffer[LogEntry](1000000)

    def size: Int = entries.size

    def add(batch: Seq[LogEntry]): Unit = {
      entries ++= batch
    }

    def build(): ImmutableMemoryLogStore = {
      new ImmutableMemoryLogStore(entries.sortWith((e1, e2) => e1.timestamp.isBefore(e2.timestamp)))
    }

  }
}

class SimpleMemoryLogStore(initialEntries: Seq[LogEntry]) extends ObservableListBase[LogEntry] with LogStore {
  private val logger = Logger[SimpleMemoryLogStore]

  private val logEntries = new util.ArrayList[LogEntry](initialEntries.size)
  private var newEntries = new util.ArrayList[LogEntry](initialEntries.size)

  initialEntries.foreach(newEntries.add(_))

  def this() = this(List.empty)

  override def entries: Seq[LogEntry] = synchronized {
    logEntries.asScala.toList
  }

  override def get(index: Int): LogEntry = synchronized {
    sort()
    entries(index)
  }

  override def size(): Int = synchronized {
    entries.size + newEntries.size
  }

  def sort(): Unit = synchronized {
    if (!newEntries.isEmpty) {
      logEntries.addAll(newEntries)
      logEntries.sort((e1, e2) => e1.timestamp.compareTo(e2.timestamp))
      newEntries.clear()
    }
  }

  def add(toAdd: Seq[LogEntry]): Unit = synchronized {
    newEntries.ensureCapacity(newEntries.size + toAdd.size)
    toAdd.foreach(newEntries.add(_))

    logger.debug(s"Added ${toAdd.size} entries to log store. Current size: ${size()}")
  }

}

object SimpleMemoryLogStore {
  def apply(entries: Seq[LogEntry]): LogStore = {
    new SimpleMemoryLogStore(entries.toIndexedSeq)
  }
}
