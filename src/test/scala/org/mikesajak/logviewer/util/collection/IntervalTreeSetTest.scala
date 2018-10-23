package org.mikesajak.logviewer.util.collection

import org.scalatest.{FlatSpec, Matchers}

class IntervalTreeSetTest extends FlatSpec with Matchers {

  implicit val ord = new IntervalStartOrdering[Int]()

  "Interval tree" should "allow adding intervals" in {
    val intervalTree = new IntervalTreeSet[Int]()

    intervalTree.add(Interval[Int](1, 2))
    intervalTree.add(Interval[Int](3, 4))
    intervalTree.add(Interval[Int](5, 6))
    intervalTree.add(Interval[Int](7, 8))
    intervalTree.add(Interval[Int](9, 10))
    intervalTree.add(Interval[Int](11, 12))

    println(s"tree:\n$intervalTree")
  }
}