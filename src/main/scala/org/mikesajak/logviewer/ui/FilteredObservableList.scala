package org.mikesajak.logviewer.ui

import java.util

import com.typesafe.scalalogging.Logger
import javafx.collections.ListChangeListener.Change
import javafx.collections.transformation.TransformationList
import javafx.collections.{ListChangeListener, ObservableList}
import org.mikesajak.logviewer.util.Measure.measure

import scala.collection.JavaConverters._

class FilteredObservableList[E](source: ObservableList[_ <: E], predicate: E => Boolean) extends TransformationList[E, E](source) {
  private implicit val logger: Logger = Logger[FilteredObservableList[E]]

  private val filterTable = measure("Preparing filter table") { () =>
    val indexes = source.iterator().asScala.zipWithIndex
      .filter { case (elem, idx) => predicate(elem) }
      .zipWithIndex
      .map { case ((_, origIdx), filterIdx) => filterIdx -> origIdx }
      .toSeq

//    val indexesTable = source.iterator().asScala.zipWithIndex
//      .filter { case (elem, idx) => predicate(elem) }
//      .zipWithIndex
//      .map { case ((_, origIdx), filterIdx) => filterIdx -> origIdx }
//      .toArray

    val tableSize = indexes.size
    val table = new Array[Int](tableSize)
    indexes.foreach { case (filterIdx, origIdx) => table(filterIdx) = origIdx }
    table
  }

  override def getSourceIndex(index: Int): Int = filterTable(index)
  override def get(index: Int): E = getSource.get(filterTable(index))
  override def size(): Int = filterTable.length

  override def sourceChanged(c: ListChangeListener.Change[_ <: E]): Unit = {
    fireChange(new Change[E](this) {
      override def wasAdded(): Boolean = c.wasAdded()
      override def wasRemoved(): Boolean = c.wasRemoved()
      override def wasReplaced(): Boolean = c.wasReplaced()
      override def wasUpdated(): Boolean = c.wasUpdated()
      override def wasPermutated(): Boolean = c.wasPermutated()

      override def getPermutation(i: Int): Int = c.getPermutation(i)

      override def getPermutation: Array[Int] = {
        // This method is only called by the superclass methods
        // wasPermutated() and getPermutation(int), which are
        // both overriden by this class. There is no other way
        // this method can be called.
        throw new AssertionError("Unreachable code")
      }

      override def getRemoved: util.List[E] = {
        val res = new util.ArrayList[E](c.getRemovedSize)
        c.getRemoved.forEach(e => res.add(e))
        res
      }

      override def getFrom: Int = c.getFrom
      override def getTo: Int = c.getTo

      override def next(): Boolean = c.next()
      override def reset(): Unit = c.reset()
    })
  }
}
