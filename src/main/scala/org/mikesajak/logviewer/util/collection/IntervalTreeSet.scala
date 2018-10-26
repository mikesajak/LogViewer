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
//    def isEmptyLeaf: Boolean = interval == null
//    def isNotEmptyLeaf: Boolean = interval != null

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
        val result = left.asInstanceOf[ValueNode[A]]
        left = result.right
        result.right = this

        result.updateMaxValue()
        result.left.updateMaxValue()
        result.right.updateMaxValue()

        result
      }
    }

    def smart[A](tp: type[A])

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
    right match {
      case r if r.level < level |
           rv @ ValueNode[A](_, _, _, rl, rr)  =>
    }
  }
    if (right.level < level ||
      right.right.right.level < level) this
    else {
      val result = right
      right = result.left
      result.left = this
      result.level += 1

      result.updateMaxValue()
      result.left.updateMaxValue()
      result.right.updateMaxValue()

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
  import IntervalTreeSet._

  private var root: Node[A] = mkLeafNode()
  private var size0: Int = 0

  override def clear(): Unit = {
    root = mkLeafNode()
    size0 = 0
  }

  override def size: Int = size0

  override def contains(elem: Interval[A]): Boolean = {
    assume(elem != null)

    @tailrec
    def contains(node: Node[A]): Boolean =
      node match {
        case n: ValueNode[A] =>
          if (iord.lt(elem, n.interval)) contains(n.left)
          else if (iord.gt(elem, n.interval)) contains(n.right)
          else true
        case _ => false
      }

    contains(root)
  }

  override def add(elem: Interval[A]): Boolean = {
    assume(elem != null)
    if (size == Int.MaxValue) throw new IllegalStateException("Maximum size reached")
    if (contains(elem)) false
    else {
      root = addTo(root, elem)
      size0 += 1
      true
    }
  }

  override def remove(elem: Interval[A]): Boolean = {
    assume(elem != null)
    if (!contains(elem)) false
    else {
      root = removeFrom(root, elem)
      size0 -= 1
      true
    }
  }

  def iterator = new Iter[A](root)

  override def +=(elem: Interval[A]): IntervalTreeSet.this.type = {
    add(elem)
    this
  }

  override def -=(elem: Interval[A]): IntervalTreeSet.this.type = {
    remove(elem)
    this
  }

  protected def addTo(node: Node[A], v: Interval[A]): Node[A] = {
    assume(v != null)
    node match {
      case n: ValueNode[A] =>
        if (iord.lt(v, n.interval))      n.left = addTo(n.left, v)
        else if (iord.gt(v, n.interval)) n.right = addTo(n.right, v)
        else throw new IllegalArgumentException(s"""Value \"$v\" is already in a tree""""")

        split(skew(n)) // rebalance this node

      case _ => mkValueNode(v)
    }
  }

  protected def removeFrom(node: Node[A], v: Interval[A]): Node[A] = {
    node match {
      case _ : LeafNode[A] => throw new IllegalArgumentException("""Value \"$v\" is not in a tree""")
      case n: ValueNode[A] =>
        if (iord.lt(v, n.interval)) {
          n.left = removeFrom(n.left, v)
          rebalance(n)
        } else if (iord.gt(v, n.interval)) {
          n.right = removeFrom(n.right, v)
          rebalance(n)
        } else {
          n match {
            case ValueNode(_, _, _, left: ValueNode[A], right) =>

            case ValueNode(_, _, _, left, right: ValueNode[A]) =>
            case _ =>
              assume(n.level == 1)
              mkLeafNode()
          }

          if (isNotEmptyLeaf(n.left)) {
            var tmp = n.left.asInstanceOf[ValueNode[A]] // TODO: avoid cast
            while (isNotEmptyLeaf(n.right))
              tmp = tmp.right.asInstanceOf[ValueNode[A]] // TODO: avoid cast

            n.interval = tmp.interval
            n.left = removeFrom(n.left, n.interval)
            rebalance(n)
          } else if (isNotEmptyLeaf(n.right)) {
            var tmp = n.right.asInstanceOf[ValueNode[A]] // TODO: avoid cast
            while (isNotEmptyLeaf(n.left))
              tmp = tmp.left.asInstanceOf[ValueNode[A]] // TODO: avoid cast

            n.interval = tmp.interval
            n.right = removeFrom(n.right, n.interval)
            rebalance(n)
          } else {
            assume(n.level == 1)
            mkLeafNode()
          }
        }
    }
  }

  private def rebalance(node: ValueNode[A]): ValueNode[A] = {
    if (node.level == math.min(node.left.level, node.right.level) + 1) node
    else {
      if (node.right.level == node.level)
        node.right.level -= 1
      node.level -= 1
      var result = skew(node)
      val rr = skew(result.right.asInstanceOf[ValueNode[A]]) // TODO: avoid cast
      result.right = rr

      if (isNotEmptyLeaf(rr.right))
        rr.right = skew(rr.right.asInstanceOf[ValueNode[A]]) // TODO: avoid cast

      result = split(result)
      result.right = split(rr)

      result
    }
  }

  /*
   *       |          |
   *   A - B    ->    A - B
   *  /   / \        /   / \
   * 0   1   2      0   1   2
   */
  private def skew(node: ValueNode[A]): ValueNode[A] = {
    if (node.left.level < node.level) node
    else {
      val result = node.left
      node.left = result.right
      result.right = node

      result.updateMaxValue()
      result.left.updateMaxValue()
      result.right.updateMaxValue()

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
  private def split(node: ValueNode[A]): ValueNode[A] = {
    // must short-circuit because if right.level < self.level, then right.right might be null
    if (node.right.level < node.level || node.right.right.level < node.level) node
    else {
      val result = node.right
      node.right = result.left
      result.left = node
      result.level += 1

      result.updateMaxValue()
      result.left.updateMaxValue()
      result.right.updateMaxValue()

      result
    }
  }

  def checkStructure(node: ValueNode[A], visitedNodes: mutable.Set[ValueNode[A]]): Int = {
    if (node.isEmptyLeaf) 0
    else {
      assume(visitedNodes.add(node))
      assume(!(node.interval == null || node.left == null || node.right == null))
      assume(node.level > 0 && node.level == node.left.level + 1 && (node.level == node.right.level + 1 || node.level == node.right.level))
      assume(!(node.left.isNotEmptyLeaf && !iord.lt(node.left.interval, node.interval)))
      assume(!(node.right.isNotEmptyLeaf && !iord.gt(node.right.interval, node.interval)))

      val size = 1 + checkStructure(node.left, visitedNodes) + checkStructure(node.right, visitedNodes)
      assume(!(size < (1 << node.level) - 1))
      // not checked, but (size <= 3 ^ level - 1) is also true
      size
    }
  }


  override def toString = {
    s"IntervalTreeSet($size):\n${iterator.map(_.toString)}"
  }
}

