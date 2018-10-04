package org.mikesajak.logviewer.log.span

import org.mikesajak.logviewer.log.{LogEntry, LogId, LogStore, Timestamp}

import scala.collection.mutable

sealed trait Marker {
  val category: String
  val id: String
  val logId: LogId
  val timestamp: Timestamp
}

case class StartMarker(category: String, id: String, logId: LogId, timestamp: Timestamp) extends Marker
case class EndMarker(category: String, id: String, logId: LogId, timestamp: Timestamp) extends Marker
case class OccurrenceMarker(category: String, id: String, logId: LogId, timestamp: Timestamp) extends Marker

trait MarkerMatcher {
  def matchMarker(entry: LogEntry): Option[Marker]
}

trait OccurrenceMarkerMatcher extends MarkerMatcher {
  val category: String

  def getId(entry: LogEntry): Option[String]

  def matchMarker(entry: LogEntry): Option[Marker] =
    getId(entry).map(id => OccurrenceMarker(category, id, entry.id, entry.id.timestamp))
}

class SimpleOccurrenceMarkerMatcher(override val category: String, idExtractor: LogEntry => Option[String])
  extends OccurrenceMarkerMatcher {
  override def getId(entry: LogEntry): Option[String] = idExtractor(entry)
}

class RequestIdMarkerMatcher extends SimpleOccurrenceMarkerMatcher("RequestId", logEntry => Option(logEntry.requestId))
class SessionIdMarkerMatcher extends SimpleOccurrenceMarkerMatcher("SessionId", logEntry => Option(logEntry.sessionId))
class UserIdMarkerMatcher extends SimpleOccurrenceMarkerMatcher("UserId", logEntry => Option(logEntry.userId))

class SpanParser(markerMatchers: MarkerMatcher*) {

  def buildSpanStore(logStore: LogStore): SpanStore = {
    val markersMap = processMarkers(logStore.entriesIterator.toSeq)
    val spans = processSpans(markersMap)
    val spanStore = new SpanStore(logStore)
    spanStore.addAll(spans)
    spanStore
  }

  def processMarkers(entries: Seq[LogEntry]): Map[String, List[Marker]] = {
//    var markerMap = ListMultiMap[String, Marker]()
    val markerMap = mutable.Map[String, List[Marker]]()
    entries.flatMap(entry => markerMatchers.flatMap(matcher => matcher.matchMarker(entry).map(m => entry -> m)))
      .foreach { case (entry, marker) =>
        val markersForCategory = markerMap.getOrElse(marker.category, List())
        markerMap(marker.category) = marker :: markersForCategory
      }
    markerMap.toMap
  }

  def processSpans(markerMap: Map[String, List[Marker]]): Seq[Span] = {
    markerMap.flatMap { case (category, markers) =>
      val markersById = markers.groupBy(m => m.id)
      markersById.map { case (id, markersForId) =>
        val sortedMarkers = markersForId.sortBy(m => m.logId)
        // TODO: currently only simplistic approach: first and last timestamp from markers; add start end marker support
        Span(category, id, sortedMarkers.map(_.logId), sortedMarkers.head.timestamp, sortedMarkers.last.timestamp)
      }
    }.toSeq
  }
}

