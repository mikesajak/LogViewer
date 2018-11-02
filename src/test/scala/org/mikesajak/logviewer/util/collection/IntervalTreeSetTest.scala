package org.mikesajak.logviewer.util.collection

import org.scalatest.{FlatSpec, Matchers}

class IntervalTreeSetTest extends FlatSpec with Matchers {

  implicit val ord = new IntervalStartOrdering[Int]()

  "Interval tree" should "allow adding intervals" in {
    val intervalTree = new IntervalTreeSet[Int]()

    for (i <- 1 to 1000 by 3) {
      intervalTree.add(Interval[Int](i, i+1))
    }

    println(s"tree:\n$intervalTree")

  }
}