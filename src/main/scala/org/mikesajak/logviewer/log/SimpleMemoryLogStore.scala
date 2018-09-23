package org.mikesajak.logviewer.log
import java.time.LocalDateTime

import com.typesafe.scalalogging.Logger
import javafx.collections.ObservableListBase
import org.mikesajak.logviewer.log.parser.{IdGenerator, ParserContext}
import org.mikesajak.logviewer.util.SearchingEx

import scala.collection.Searching.{Found, InsertionPoint}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.math.Ordering

class ImmutableMemoryLogStore(entryStore: IndexedSeq[LogEntry]) extends ObservableListBase[LogEntry] with LogStore {
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

object ImmutableMemoryLogStore {
  val empty = new ImmutableMemoryLogStore(IndexedSeq.empty)

  class Builder extends LogStoreBuilder {
    private val logger = Logger[ImmutableMemoryLogStore.Builder]
    private val entriesSet = new mutable.TreeSet[LogEntry]()

    override def threadSafe: Boolean = true

    override def size: Long = synchronized { entriesSet.size }

    override def add(entry: LogEntry): Unit = synchronized {
      if (!entriesSet.contains(entry)) entriesSet += entry
      else logger.debug(s"Store already contains entry with id=${entry.id}. Ignoring")
    }

    override def add(batch: Seq[LogEntry]): Unit = synchronized {
      batch.foreach(add)
    }

    override def build(): LogStore = synchronized {
      new ImmutableMemoryLogStore(entriesSet.toIndexedSeq)
    }
  }
}
