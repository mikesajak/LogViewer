package org.mikesajak.logviewer.util.collection

import scala.annotation.tailrec
import scala.collection.mutable

class IntervalStartOrdering[A](implicit ord: Ordering[A]) extends Ordering[Interval[A]] {
  override def compare(x: Interval[A], y: Interval[A]): Int = ord.compare(x.start, y.start)
}

case class Interval[A](start: A, end: A)

object IntervalTreeSet {
  sealed abstract class Node[A](var level: Int) {
    def add(v: Interval[A]): Node[A]
    def updateMaxValue(): Unit
  }
  case class LeafNode[A]() extends Node[A](0) {
    override def add(v: Interval[A]) = mkValueNode(v)

    override def updateMaxValue(): Unit = {
      // do nothing
    }
  }

  case class ValueNode[A](var interval: Interval[A], var maxEnd: A, override var level: Int, var left: Node[A], var right: Node[A])
                         (implicit ord: Ordering[A], implicit val iord: Ordering[Interval[A]]) extends Node[A](level) {
    def updateMaxValue(): Unit = {
      maxEnd = right match {
        case r: ValueNode[A] => ord.max(maxEnd, r.maxEnd)
        case _ => interval.end
      }
    }

    def add(v: Interval[A]) = {
      assume(v != null)

      if (iord.lt(v, interval))      left = left.add(v)
      else if (iord.gt(v, interval)) right = right.add(v)
      else throw new IllegalArgumentException(s"""Value \"$v\" is already in a tree""""")

      split(skew()) // rebalance this node
    }

    /*
     *       |          |
     *   A - B    ->    A - B
     *  /   / \        /   / \
     * 0   1   2      0   1   2
     */
    private def skew(): ValueNode[A] = {
      if (left.level < level) this
      else {
        assume(left.isInstanceOf[ValueNode[A]])
        val result = left.asInstanceOf[ValueNode[A]]
        left = result.right
        result.right = this
//        result.updateMaxValue()
//        result.left.updateMaxValue()
//        result.right.updateMaxValue()
        result
      }
    }

  /*
   *   |                      |
   *   |                    - B -
   *   |                   /     \
   *   A - B - C    ->    A       C
   *  /   /   / \        / \     / \
   * 0   1   2   3      0   1   2   3
   */
  private def split(): ValueNode[A] = {
    // must short-circuit because if right.level < self.level, then right.right might be null
    assume(right.isInstanceOf[ValueNode[A]])

    if (right.level < level ||
      (right.asInstanceOf[ValueNode[A]].right.level < level)) this
    else {
      val result = right.asInstanceOf[ValueNode[A]]
      right = result.left
      result.left = this
      result.level += 1

//      result.updateMaxValue()
//      result.left.updateMaxValue()
//      result.right.updateMaxValue()

      result
    }
  }

  def isEmptyLeaf[A](node: Node[A]): Boolean = node.isInstanceOf[LeafNode[A]]
  def isNotEmptyLeaf[A](node: Node[A]): Boolean = !isEmptyLeaf(node)

  def mkLeafNode[A]()(implicit ord: Ordering[A]): LeafNode[A] = LeafNode()
  def mkValueNode[A](interval: Interval[A])(implicit ord: Ordering[A]): ValueNode[A] =
    ValueNode[A](interval, interval.end, 1, mkLeafNode(), mkLeafNode())

  class Iter[A](root: Node[A]) extends Iterator[Interval[A]] {
    private var stack: List[ValueNode[A]] = pushLeft(root, List())

    @tailrec
    private def pushLeft(node: Node[A], st: List[ValueNode[A]]): List[ValueNode[A]] =
      node match {
        case _ : LeafNode[A] => st
        case n: ValueNode[A] => pushLeft(n.left, n :: st)
      }

    override def hasNext: Boolean = stack.nonEmpty

    override def next(): Interval[A] = {
      if (!hasNext) throw new NoSuchElementException
      else {
        val node = stack.head
        stack = stack.tail
        val result = node.interval
        stack = pushLeft(node.right, stack)
        result
      }
    }
  }
}

class IntervalTreeSet[A](implicit ord: Ordering[A], implicit val iord: Ordering[Interval[A]]) extends mutable.Set[Interval[A]] {




  override def toString = {
    s"IntervalTreeSet($size):\n${iterator.map(_.toString)}"
  }
}

