package org.mikesajak.logviewer.ui

import java.util

import javafx.collections.ListChangeListener.Change
import javafx.collections.{ListChangeListener, ObservableList}
import javafx.collections.transformation.TransformationList

class MappedObservableList[E, F](source: ObservableList[_ <: F], mapper: F => E) extends TransformationList[E, F](source) {

  override def getSourceIndex(index: Int): Int = index
  override def get(index: Int): E = mapper(getSource.get(index))
  override def size(): Int = getSource.size()

  override def sourceChanged(c: ListChangeListener.Change[_ <: F]): Unit = {
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
        c.getRemoved.forEach(e => res.add(mapper(e)))
        res
      }

      override def getFrom: Int = c.getFrom
      override def getTo: Int = c.getTo

      override def next(): Boolean = c.next()
      override def reset(): Unit = c.reset()
    })
  }
}
