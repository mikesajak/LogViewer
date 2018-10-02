package org.mikesajak.logviewer.log.span

import com.google.common.collect
import com.google.common.collect.TreeRangeMap
import org.mikesajak.logviewer.log.{LogEntry, Timestamp}
import org.mikesajak.logviewer.util.ListMultiMaps._

case class Span(category: String, name: String, begin: Timestamp, end: Timestamp)

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

class SpanStoreBuilder(markerMatchers: Seq[MarkerMatcher]) {

  def buildSpanStore(entries: Seq[LogEntry]): SpanStore = {
    val markersMap = processMarkers(entries)
    val spans = processSpans(markersMap)
    val spanStore = new SpanStore
    spanStore.addAll(spans)
    spanStore
  }

  def processMarkers(entries: Seq[LogEntry]): ListMultiMap[String, Marker] = {
    val markerMap = ListMultiMap[String, Marker]()
    entries.flatMap(entry => markerMatchers.flatMap(matcher =>
        matcher.matchMarker(entry).map(m => entry -> m)))
      .foreach { case (entry, marker) =>
        markerMap.addBinding(marker.category, marker)
//        entryMarkerMap.addBinding(entry, marker)
      }
    markerMap
  }

  def processSpans(markerMap: ListMultiMap[String, Marker]): Seq[Span] = {
    markerMap.flatMap { case (category, markers) =>
      val markersById = markers.groupBy(m => m.id)
      markersById.map { case (id, markersForId) =>
        // TODO: currently only simplistic approach: first and last timestamp from markers; add start end marker support
        Span(category, id, markersForId.view.map(_.timestamp).min, markersForId.view.map(_.timestamp).max)
      }
    }.toSeq
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