package org.mikesajak.logviewer.util.collection

import scala.annotation.tailrec
import scala.collection.mutable

class IntervalStartOrdering[A](implicit ord: Ordering[A]) extends Ordering[Interval[A]] {
  override def compare(x: Interval[A], y: Interval[A]): Int = ord.compare(x.start, y.start)
}

case class Interval[A](start: A, end: A)

object IntervalTreeSet {

  sealed abstract class Node[A, B](var level: Int) {
    def mkStr(indent: String, short: Boolean = false) = s"$toString"
  }
  case class LeafNode[A, B]() extends Node[A, B](0)

  def addTo[A, B](node: Node[A, B], interval: Interval[A], value: B)(implicit ord: Ordering[A], iord: Ordering[Interval[A]]): ValueNode[A, B] = {
//    println(s"addTo: $interval to ${node.mkStr("", short = true)}")
    node match {
      case l : LeafNode[A, B] => mkValueNode(interval, value)(ord, iord)
      case v : ValueNode[A, B] => v.add(interval, value)
    }
  }

  case class ValueNode[A, B](var interval: Interval[A], var maxEnd: A, lvl: Int, valueSet: mutable.Set[B],
                             var left: Node[A, B], var right: Node[A, B])
                         (implicit ord: Ordering[A], iord: Ordering[Interval[A]]) extends Node[A, B](lvl) {

    def this(interval: Interval[A], maxEnd: A, level: Int, value: B, left: Node[A, B], right: Node[A, B])
            (implicit ord: Ordering[A], iord: Ordering[Interval[A]]) =
      this(interval, maxEnd, level, mutable.Set(value), left, right)

    def updateMaxValues(): Unit = {
      left match {
        case lv: ValueNode[A, B] => lv.updateMaxValue()
        case _ =>
      }
      right match {
        case rv: ValueNode[A, B] => rv.updateMaxValue()
        case _ =>
      }
      updateMaxValue()
    }

    def updateMaxValue(): Unit = {
      maxEnd = right match {
        case r: ValueNode[A, B] => ord.max(interval.end, r.maxEnd)
        case _ => interval.end
      }
    }

    def add(intvl: Interval[A], value: B): ValueNode[A, B] = {
      assume(intvl != null)
//      println(s"add $v to ${mkStr("", short = true)}")

      if (iord.lt(intvl, interval)) left = addTo(left, intvl, value)
      else if (iord.gt(intvl, interval)) right = addTo(right, intvl, value)
      else throw new IllegalArgumentException(s"""Value \"$intvl\" is already in a tree""""")

      val result = split().skew() // rebalance this node
      updateMaxValues()
      result
    }

    def remove(v: Interval[A]): Node[A, B] = {
      iord.compare(v, interval) match {
        case x if x < 0 =>
          left = left.asInstanceOf[ValueNode[A, B]].remove(v)
          rebalance()

        case x if x > 0 =>
          right = right.asInstanceOf[ValueNode[A, B]].remove(v)
          rebalance()

        case _ => // remove value at this node
          this match {
            case ValueNode(_, _, _, _, lv @ ValueNode(_, _, _, _, _, _), _) =>
              val bottomRight = deepestRight(lv)
              interval = bottomRight.interval
              left = lv.remove(interval)
              rebalance()

            case ValueNode(_, _, _, _, _, rv @ ValueNode(_, _, _, _, _, _)) =>
              val bottomLeft = deepestLeft(rv)
              interval = bottomLeft.interval
              right = rv.remove(interval)
              rebalance()

            case _ =>
              assume(level == 1)
              LeafNode()
          }
      }
    }

    private def rebalance() = {
      if (level == math.min(left.level, right.level) + 1) {
        updateMaxValues()
        this
      }
      else {
        if (right.level == level)
          right.level -= 1

        level -= 1

        var result = this.skew()
        result.right = result.right.asInstanceOf[ValueNode[A, B]].skew()
        if (!isLeafNode(result.right.asInstanceOf[ValueNode[A, B]].right))
          result.right.asInstanceOf[ValueNode[A, B]].right =
            result.right.asInstanceOf[ValueNode[A, B]].right.asInstanceOf[ValueNode[A, B]].skew()
        result = result.split()
        result.right = result.right.asInstanceOf[ValueNode[A, B]].split()
        result.updateMaxValues()
        updateMaxValues()
        result
      }
    }


    @tailrec
    private def deepestRight(node: ValueNode[A, B]): ValueNode[A, B] = {
      node.right match {
        case rv: ValueNode[A, B] => deepestRight(rv)
        case _ => this
      }
    }

    @tailrec
    private def deepestLeft(node: ValueNode[A, B]): ValueNode[A, B] = {
      node.left match {
        case lv: ValueNode[A, B] => deepestLeft(lv)
        case _ => this
      }
    }

    /*
     *       |          |
     *   A - B    ->    A - B
     *  /   / \        /   / \
     * 0   1   2      0   1   2
     */
    private def skew(): ValueNode[A, B] = {
      if (left.level < level) this
      else {
        assume(left.isInstanceOf[ValueNode[A, B]])
        val result = left.asInstanceOf[ValueNode[A, B]]
        left = result.right
        result.right = this

        result.updateMaxValues()

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
    private def split(): ValueNode[A, B] = {
      // must short-circuit because if right.level < self.level, then right.right might be null
      assume(right.isInstanceOf[ValueNode[A, B]])

      if (right.level < level ||
        (right.asInstanceOf[ValueNode[A, B]].right.level < level)) this
      else {
        val result = right.asInstanceOf[ValueNode[A, B]]
        right = result.left
        result.left = this
        result.level += 1

        result.updateMaxValues()

        result
      }
    }

    override def toString: String = mkStr("")

    override def mkStr(indent: String, short: Boolean): String = {
      def descr(node: Node[A, B]) = node match {
        case vn: ValueNode[A, B] => s"ValueNode(${vn.interval})"
        case _ => "LeafNode()"
      }

      if (short)
        s"ValueNode($interval, max=$maxEnd, level=$level, left=${descr(left)}, right=${descr(right)})"
      else {
        val indent2 = indent + "  "
        s"ValueNode($interval, max=$maxEnd, level=$level\n${indent2}left=${left.mkStr(indent2)}\n${indent2}right=${right.mkStr(indent2)}\n$indent)"
      }
    }
  }

  def isLeafNode[A, B](node: Node[A, B]): Boolean = node.isInstanceOf[LeafNode[A, B]]
  def isNotLeafNode[A, B](node: Node[A, B]): Boolean = !isLeafNode(node)

  def mkLeafNode[A, B](): LeafNode[A, B] = LeafNode()

  def mkValueNode[A, B](interval: Interval[A], value: B)(implicit ord: Ordering[A], iord: Ordering[Interval[A]]): ValueNode[A, B] =
    new ValueNode[A, B](interval, interval.end, 1, value, mkLeafNode(), mkLeafNode())


  class Iter[A, B](root: Node[A, B]) extends Iterator[IntervalValue[A, B]] {
    private var stack: List[ValueNode[A, B]] = pushLeft(root, List())
    private var curNode: ValueNode[A, B] = _
    private var nodeIterator: Iterator[B] = _

    @tailrec
    private def pushLeft(node: Node[A, B], st: List[ValueNode[A, B]]): List[ValueNode[A, B]] =
      node match {
        case _ : LeafNode[A, B] => st
        case n: ValueNode[A, B] => pushLeft(n.left, n :: st)
      }

    override def hasNext: Boolean =
      nodeIterator != null && nodeIterator.hasNext ||
        stack.nonEmpty

    override def next(): IntervalValue[A, B] = {
      if (!hasNext) throw new NoSuchElementException
      else {
        if (nodeIterator != null) {
          val value = nodeIterator.next()
          if (!nodeIterator.hasNext) {
            curNode = null
            nodeIterator = null
          }
          IntervalValue(curNode.interval, value)
        } else {
          val node = stack.head
          stack = stack.tail
          val result = node
          stack = pushLeft(node.right, stack)
          nodeIterator = node.valueSet.iterator
          val value = nodeIterator.next()
          IntervalValue(result.interval, value)
        }
      }
    }
  }
}

case class IntervalValue[A, B](interval: Interval[A], value: B)

class IntervalTreeSet[A, B](implicit ord: Ordering[A], iord: Ordering[Interval[A]])
    extends mutable.Set[IntervalValue[A, B]] {

  import IntervalTreeSet._

  var root: Node[A, B] = LeafNode()
  var size0 = 0

  override def clear(): Unit = {
    root = LeafNode()
    size0 = 0
  }

  override def size: Int = size0

  override def contains(elem: IntervalValue[A, B]): Boolean = {
    findInternal(elem.interval) match {
      case vn: ValueNode[A, B] => vn.valueSet.contains(elem.value)
      case _ => false
    }
  }

  def find(intervalToFind: Interval[A]): Option[ValueNode[A, B]] =
    findInternal(intervalToFind) match {
      case vn: ValueNode[A, B] => Some(vn)
      case _ => None
    }

  def contains(intervalToFind: Interval[A]): Boolean =
    findInternal(intervalToFind) match {
      case vn: ValueNode[A, B] => true
      case _ => false
    }

  private def findInternal(intervalToFind: Interval[A]): Node[A, B] = {
    assume(intervalToFind != null)

    var node = root
    var found = false
    while (!node.isInstanceOf[LeafNode[A, B]] && !found) {
      node match {
        case ValueNode(nodeInterval, maxEnd, level, _, left, right) =>
          iord.compare(intervalToFind, nodeInterval) match {
            case x if x < 0 => node = left
            case x if x > 0 => node = right
            case _ => found = true
          }

        case _ =>
      }
    }

    node
  }

  override def +=(elem: IntervalValue[A, B]): IntervalTreeSet.this.type = {
    assume(elem != null)

    if (size0 == Int.MaxValue) throw new IllegalStateException("Maximum size reached")
    else {
      if (!contains(elem)) {
        root = addTo(root, elem.interval, elem.value)
        size0 += 1
      }
    }
    this
  }

  override def -=(elem: IntervalValue[A, B]): IntervalTreeSet.this.type = {
    assume(elem != null)

    if (contains(elem)) {
      root = root.asInstanceOf[ValueNode[A, B]].remove(elem)
      size0 -= 1
    }

    this
  }

  override def iterator: Iterator[IntervalValue[A, B]] = new Iter(root)

  override def toString = {
    s"IntervalTreeSet($size):\nroot=$root"
  }
}

