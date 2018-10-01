package org.mikesajak.logviewer.log.span

import com.google.common.collect
import com.google.common.collect.TreeRangeMap
import com.typesafe.scalalogging.Logger
import org.mikesajak.logviewer.log.{LogEntry, Timestamp}

import scala.collection.mutable

case class Span(category: String, name: String, begin: Timestamp, end: Timestamp)

case class SpanMatcher(category: String, beginMatcher: LineMatcher, endMatcher: LineMatcher) {
  def matchBeginning(line: String): Option[(String, Timestamp)] = beginMatcher.matchMarker(line)
  def matchEnd(line: String): Option[(String, Timestamp)] = endMatcher.matchMarker(line)
}

trait LineMatcher {
  def matchMarker(line: String): Option[(String, Timestamp)]
}

sealed trait Marker {
  val category: String
  val id: String
  val timestamp: Timestamp
}
case class StartMarker(category: String, id: String, timestamp: Timestamp) extends Marker
case class EndMarker(category: String, id: String, timestamp: Timestamp) extends Marker
case class OccurrenceMarker(category: String, id: String, timestamp: Timestamp) extends Marker

trait MarkerMatcher {
  def matchMarker(entry: LogEntry): Option[Marker]
}

trait OccurrenceMarkerMatcher extends MarkerMatcher {
  val category: String

  def getId(entry: LogEntry): Option[String]

  def matchMarker(entry: LogEntry): Option[Marker] =
    getId(entry).map(id => OccurrenceMarker(category, id, entry.id.timestamp))
}

class SimpleOccurrenceMarkerMatcher(override val category: String, idExtractor: LogEntry => Option[String])
    extends OccurrenceMarkerMatcher {
  override def getId(entry: LogEntry): Option[String] = idExtractor(entry)
}

class RequestIdMarkerMatcher extends SimpleOccurrenceMarkerMatcher("RequestId", logEntry => Option(logEntry.requestId))
class SessionIdMarkerMatcher extends SimpleOccurrenceMarkerMatcher("SessionId", logEntry => Option(logEntry.sessionId))
class UserIdMarkerMatcher extends SimpleOccurrenceMarkerMatcher("UserId", logEntry => Option(logEntry.userId))

class SpanBuilder(markerMatchers: Seq[MarkerMatcher]) {
  val markerMap = new mutable.HashMap[String, mutable.Set[Marker]]() with mutable.MultiMap[String, Marker]
  val entryMarkerMap = new mutable.HashMap[LogEntry, mutable.Set[Marker]]() with mutable.MultiMap[LogEntry, Marker]

  def processMarkers(entries: Seq[LogEntry]) = {
    entries.flatMap(entry => markerMatchers.flatMap(matcher =>
        matcher.matchMarker(entry).map(m => entry -> m)))
      .foreach { case (entry, marker) =>
        markerMap.addBinding(marker.category, marker)
        entryMarkerMap.addBinding(entry, marker)
      }
  }

//  def processSpans
}


class SpanParser {
  private val logger = Logger[SpanMatcher]

  def parse(lines: Iterator[String]) = {
    val spanStore = new SpanStore

//    val matcher = new SpanMatcher()

//    parseSpans2(lines)
  }

  def parseSpans(lines: Iterator[String], matcher: SpanMatcher): Iterator[Span] = {
    var beginMarkers = Map[String, Timestamp]()
    lines.flatMap { line =>
      matcher.matchBeginning(line) match {
        case Some((name, startTime)) =>
          if (beginMarkers.contains(name))
            logger.warn(s"Illegal parser state - double span begin marker detected $name -> $startTime, previous: ${beginMarkers(name)}")
          beginMarkers += name -> startTime
        case _ =>
      }

      val spanOpt = matcher.matchEnd(line) flatMap { case (name, endTime) =>
        if (!beginMarkers.contains(name)) {
          logger.warn(s"Illegal parser state - no begin marker for end span detected: $name -> $endTime")
          None
        } else {
          val beginTime = beginMarkers(name)
          Some(Span(matcher.category, name, beginTime, endTime))
        }
      }

      spanOpt
    }
  }
}

class SpanStore {
  private val spanMap = TreeRangeMap.create[Timestamp, Span]()

  def add(span: Span): Unit =
    spanMap.put(collect.Range.closed(span.begin, span.end), span)

  def addAll(spans: Seq[Span]): Unit = {
    for (span <- spans) {
      spanMap.put(collect.Range.closed(span.begin, span.end), span)
    }
  }

  def addAll(spans: Iterator[Span]): Unit = {
    for (span <- spans) {
      spanMap.put(collect.Range.closed(span.begin, span.end), span)
    }
  }

  def get(timestamp: Timestamp) = Option(spanMap.get(timestamp))
}