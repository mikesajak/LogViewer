package org.mikesajak.logviewer.log.span

import com.google.common.collect
import com.google.common.collect.TreeRangeMap
import org.mikesajak.logviewer.log.{LogId, LogStore, Timestamp}

import scala.collection.immutable.TreeMap
import scala.collection.mutable

object SpanStore {
  def empty = new SpanStore(null)
}

class SpanStore(logStore: LogStore) {
  private val spanMap = TreeRangeMap.create[Timestamp, Span]()
  private var spansSeq = IndexedSeq[Span]()

  private var spanStartMap = TreeMap[Timestamp, List[Span]]()
  private var spanEndMap = TreeMap[Timestamp, List[Span]]()

  private var longestSpan: Span = _
  private var shortestSpan: Span = _

  private val spanIndex = mutable.Map[String, Span]()
  private val logIdIndex = mutable.Map[LogId, Seq[Span]]()

  def add(span: Span): Unit =
    addSpan(span)

  def addAll(spans: Seq[Span]): Unit =
    for (span <- spans)
      addSpan(span)

  def addAll(spans: Iterator[Span]): Unit =
    for (span <- spans)
      addSpan(span)

  private def addSpan(span: Span): Unit = {
    spanMap.put(collect.Range.closed(span.begin, span.end), span)

    val spansForBegin = spanStartMap.getOrElse(span.begin, List())
    spanStartMap += span.begin -> (span :: spansForBegin)

    val spansForEnd = spanEndMap.getOrElse(span.end, List())
    spanEndMap += span.end -> (span :: spansForEnd)

    spansSeq :+= span

    if (longestSpan == null || span.duration.compareTo(longestSpan.duration) > 0)
      longestSpan = span

    if (shortestSpan == null || span.duration.compareTo(shortestSpan.duration) < 0)
      shortestSpan = span

    spanIndex(s"${span.category}:${span.name}") = span

    val spanLogEntries = logStore.range(span.begin, span.end)
//    span.logIds
    spanLogEntries.map(_.id).foreach { logId =>
      val spansForId = logIdIndex.getOrElse(logId, Seq())
      logIdIndex(logId) = spansForId :+ span
    }
  }

  def get(category: String, name: String) = spanIndex(s"$category:$name")
  def get(spanId: String) = spanIndex(spanId)

//  def get(timestamp: Timestamp): Option[Span] = Option(spanMap.get(timestamp))
  def get(timestamp: Timestamp) = {
//    val startPointsAfter = spanStartMap.from(timestamp).keys
//    val endPointsBefore = spanEndMap.to(timestamp).keys
    val rangeSpans = spanStartMap.range(timestamp.minus(longestSpan.duration), timestamp)
    val curTimeSpans = spanStartMap.getOrElse(timestamp, Seq.empty)

    (rangeSpans.view.flatMap(_._2) ++ curTimeSpans)
      .filter(span => span.contains(timestamp))
      .toSeq
      .distinct
      .sortBy(_.begin)
  }

  def get(from: Timestamp, to: Timestamp) = {
    val rangeSpans = spanStartMap.range(from.minus(longestSpan.duration), to)
    val toSpans = spanStartMap.getOrElse(to, Seq.empty)
    (rangeSpans.view.flatMap(_._2) ++ toSpans)
      .filter(span => span.overlaps(from, to))
      .toSeq
      .distinct
      .sortBy(_.begin)
  }

  def get(logId: LogId): Seq[Span] = logIdIndex.getOrElse(logId, Seq.empty)

  def size: Int = spansSeq.size
  def spans: Seq[Span] = spansSeq
}
