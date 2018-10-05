package org.mikesajak.logviewer.log
import java.time.LocalDateTime

import com.typesafe.scalalogging.Logger
import javafx.collections.ObservableListBase
import org.mikesajak.logviewer.util.SearchingEx

import scala.collection.Searching.{Found, InsertionPoint}
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

  override def range(start: Timestamp, end: Timestamp): IndexedSeq[LogEntry] = {
    val startIdx = SearchingEx.binarySearch(entryStore, (e: LogEntry) => e.id.timestamp, start) match {
      case Found(foundIndex) => foundIndex
      case InsertionPoint(insertionPoint) => insertionPoint
    }

    val endIdx = SearchingEx.binarySearch(entryStore, (e: LogEntry) => e.id.timestamp, end) match {
      case Found(foundIndex) => foundIndex
      case InsertionPoint(insertionPoint) => insertionPoint
    }

    def findFirstMatching(idx: Int, tm: Timestamp): Int =
      if (idx == 0) idx
      else if (entryStore(idx).id.timestamp == tm) findFirstMatching(idx - 1, tm)
      else idx + 1

    def findLastMatching(idx: Int, tm: Timestamp): Int =
      if (idx == entryStore.size - 1) idx
      else if (entryStore(idx).id.timestamp == tm) findLastMatching(idx + 1, tm)
      else idx - 1

    entryStore.slice(findFirstMatching(startIdx, start), findLastMatching(endIdx, end) + 1)
  }
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

class ImmutableIndexes(override val idToIdx: Map[LogId, Int],
                       override val sources: Set[String],
                       override val threads: Set[String],
                       override val sessions: Set[String],
                       override val users: Set[String]) extends Indexes {

//  override def spansForEntry(logId: LogId): Seq[Span] = ???
  override def spansForTime(time: Timestamp) = ???
}

object ImmutableIndexes {
  val empty = new ImmutableIndexes(Map.empty, Set.empty, Set.empty, Set.empty, Set.empty)
}

object ImmutableMemoryLogStore {
  val empty = new ImmutableMemoryLogStore(IndexedSeq.empty, ImmutableIndexes.empty)

  class Builder extends LogStoreBuilder {
    private val logger = Logger[ImmutableMemoryLogStore.Builder]
    private val entries = new mutable.TreeSet[LogEntry]()

    private val idToIdx = mutable.Map[LogId, Int]()
    private val sources = mutable.TreeSet[String]()
    private val threads = mutable.TreeSet[String]()
    private val sessions = mutable.TreeSet[String]()
    private val users = mutable.TreeSet[String]()

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
      val indexedEntries = entries.toIndexedSeq
      for (i <- indexedEntries.indices)
        idToIdx(indexedEntries(i).id) = i

      new ImmutableMemoryLogStore(indexedEntries,
                                  new ImmutableIndexes(idToIdx.toMap,
                                                       TreeSet.empty ++ sources,
                                                       TreeSet.empty ++ threads,
                                                       TreeSet.empty ++ sessions,
                                                       TreeSet.empty ++ users))
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
