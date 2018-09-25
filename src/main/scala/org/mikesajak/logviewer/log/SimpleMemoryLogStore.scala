package org.mikesajak.logviewer.log
import java.time.LocalDateTime

import com.typesafe.scalalogging.Logger
import javafx.collections.ObservableListBase

import scala.collection.immutable.TreeSet
import scala.collection.mutable
import scala.math.Ordering

class ImmutableMemoryLogStore(entryStore: IndexedSeq[LogEntry], override val indexes: Indexes) extends ObservableListBase[LogEntry] with LogStore {
  def entries: IndexedSeq[LogEntry] = entryStore

  override def get(index: Int): LogEntry = entryStore(index)

  override def size(): Int = entryStore.size

  override def isEmpty: Boolean = entries.isEmpty

  override def nonEmpty: Boolean = entries.nonEmpty

  override def first: LogEntry = entryStore.head

  override def last: LogEntry = entryStore.last

  implicit object DateTimeOrdering extends Ordering[LocalDateTime] {
    override def compare(x: LocalDateTime, y: LocalDateTime): Int = x.compareTo(y)
  }

  override def entriesIterator: Iterator[LogEntry] = entries.iterator

//  override def range(start: LocalDateTime, end: LocalDateTime): IndexedSeq[LogEntry] = {
//    val startIdx = SearchingEx.binarySearch(entryStore, (e: LogEntry) => e.id.timestamp, start) match {
//      case Found(foundIndex) => foundIndex
//      case InsertionPoint(insertionPoint) => insertionPoint
//    }
//
//    val endIdx = SearchingEx.binarySearch(entryStore, (e: LogEntry) => e.id.timestamp, end) match {
//      case Found(foundIndex) => foundIndex
//      case InsertionPoint(insertionPoint) => insertionPoint
//    }
//
//    entryStore.slice(startIdx, endIdx)
//  }
//
//  override def logStoreForRange(start: LocalDateTime, end: LocalDateTime): LogStore = {
//    val startIdx = SearchingEx.binarySearch(entryStore, (e: LogEntry) => e.id.timestamp, start) match {
//      case Found(foundIndex) => foundIndex
//      case InsertionPoint(insertionPoint) => insertionPoint
//    }
//
//    val endIdx = SearchingEx.binarySearch(entryStore, (e: LogEntry) => e.id.timestamp, end) match {
//      case Found(foundIndex) => foundIndex
//      case InsertionPoint(insertionPoint) => insertionPoint
//    }
//
//    new ImmutableMemoryLogStore(new IndexedSeqRangeWrapper(entryStore, startIdx, endIdx))
//  }
}

class IndexedSeqRangeWrapper[A](internalSeq: IndexedSeq[A], startIdx: Int, endIdx: Int) extends IndexedSeq[A] {

  override def length: Int = endIdx - startIdx

  override def apply(idx: Int): A = internalSeq(startIdx + idx)
}

class ImmutableIndexes(override val sources: Set[String],
                       override val threads: Set[String],
                       override val sessions: Set[String],
                       override val users: Set[String]) extends Indexes

object ImmutableIndexes {
  val empty = new ImmutableIndexes(Set.empty, Set.empty, Set.empty, Set.empty)
}

object ImmutableMemoryLogStore {
  val empty = new ImmutableMemoryLogStore(IndexedSeq.empty, ImmutableIndexes.empty)

  class Builder extends LogStoreBuilder {
    private val logger = Logger[ImmutableMemoryLogStore.Builder]
    private val entries = new mutable.TreeSet[LogEntry]()

    private var sources = new TreeSet[String]()
    private var threads = new TreeSet[String]()
    private var sessions = new TreeSet[String]()
    private var users = new TreeSet[String]()

    override def threadSafe: Boolean = true

    override def size: Long = synchronized { entries.size }

    override def add(entry: LogEntry): Unit = synchronized {
      if (!entries.contains(entry)) {
        entries += entry
        updateIndexes(entry)
      }
      else logger.debug(s"Store already contains entry with id=${entry.id}. Ignoring")
    }

    override def add(batch: Seq[LogEntry]): Unit = synchronized {
      batch.foreach(add)
    }

    override def build(): LogStore = synchronized {
      new ImmutableMemoryLogStore(entries.toIndexedSeq,
                                  new ImmutableIndexes(sources, threads, sessions, users))
    }

    private def updateIndexes(entry: LogEntry): Unit = {
      sources += entry.id.source.name

      if (entry.thread != null && entry.thread.length > 0)
        threads += entry.thread

      if (entry.sessionId != null && entry.sessionId.length > 0)
        sessions += entry.sessionId

      if (entry.userId != null && entry.userId.length > 0)
        users += entry.userId
    }
  }
}
