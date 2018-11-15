package org.mikesajak.logviewer.util.collection

import scala.annotation.tailrec
import scala.collection.mutable

class IntervalStartOrdering[A](implicit ord: Ordering[A]) extends Ordering[IntervalLike[A]] {
  override def compare(x: IntervalLike[A], y: IntervalLike[A]): Int = ord.compare(x.start, y.start)
}

trait IntervalLike[A] {
  val start: A
  val end: A
}

//case class Interval[A](start: A, end: A) extends IntervalLike[A]

object IntervalTreeSet {

  sealed abstract class Node[A](var level: Int) {
    def mkStr(indent: String, short: Boolean = false) = s"$toString"
  }
  case class LeafNode[A]() extends Node[A](0)

  def addTo[A](node: Node[A], interval: IntervalLike[A])(implicit ord: Ordering[A], iord: Ordering[IntervalLike[A]]): ValueNode[A] = {
//    println(s"addTo: $interval to ${node.mkStr("", short = true)}")
    node match {
      case l : LeafNode[A] => mkValueNode(interval)(ord, iord)
      case v : ValueNode[A] => v.add(interval)
    }
  }

  case class ValueNode[A](var interval: IntervalLike[A], var maxEnd: A, lvl: Int,
                          var left: Node[A], var right: Node[A])
                         (implicit ord: Ordering[A], iord: Ordering[IntervalLike[A]]) extends Node[A](lvl) {

    def this(interval: IntervalLike[A], maxEnd: A, level: Int, left: Node[A], right: Node[A])
            (implicit ord: Ordering[A], iord: Ordering[IntervalLike[A]]) =
      this(interval, maxEnd, level, left, right)

    def updateMaxValues(): Unit = {
      left match {
        case lv: ValueNode[A] => lv.updateMaxValue()
        case _ =>
      }
      right match {
        case rv: ValueNode[A] => rv.updateMaxValue()
        case _ =>
      }
      updateMaxValue()
    }

    def updateMaxValue(): Unit = {
      maxEnd = right match {
        case r: ValueNode[A] => ord.max(interval.end, r.maxEnd)
        case _ => interval.end
      }
    }

    def add(intvl: IntervalLike[A]): ValueNode[A] = {
      assume(intvl != null)
//      println(s"add $v to ${mkStr("", short = true)}")

      if (iord.lt(intvl, interval)) left = addTo(left, intvl)
      else if (iord.gt(intvl, interval)) right = addTo(right, intvl)
      else throw new IllegalArgumentException(s"""Value \"$intvl\" is already in a tree""""")

      val result = split().skew() // rebalance this node
      updateMaxValues()
      result
    }

    def remove(v: IntervalLike[A]): Node[A] = {
      iord.compare(v, interval) match {
        case x if x < 0 =>
          left = left.asInstanceOf[ValueNode[A]].remove(v)
          rebalance()

        case x if x > 0 =>
          right = right.asInstanceOf[ValueNode[A]].remove(v)
          rebalance()

        case _ => // remove value at this node
          this match {
            case ValueNode(_, _, _, lv @ ValueNode(_, _, _, _, _), _) =>
              val bottomRight = deepestRight(lv)
              interval = bottomRight.interval
              left = lv.remove(interval)
              rebalance()

            case ValueNode(_, _, _, _, rv @ ValueNode(_, _, _, _, _)) =>
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
        result.right = result.right.asInstanceOf[ValueNode[A]].skew()
        if (!isLeafNode(result.right.asInstanceOf[ValueNode[A]].right))
          result.right.asInstanceOf[ValueNode[A]].right =
            result.right.asInstanceOf[ValueNode[A]].right.asInstanceOf[ValueNode[A]].skew()
        result = result.split()
        result.right = result.right.asInstanceOf[ValueNode[A]].split()
        result.updateMaxValues()
        updateMaxValues()
        result
      }
    }


    @tailrec
    private def deepestRight(node: ValueNode[A]): ValueNode[A] = {
      node.right match {
        case rv: ValueNode[A] => deepestRight(rv)
        case _ => this
      }
    }

    @tailrec
    private def deepestLeft(node: ValueNode[A]): ValueNode[A] = {
      node.left match {
        case lv: ValueNode[A] => deepestLeft(lv)
        case _ => this
      }
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

        result.updateMaxValues()

        result
      }
    }

    override def toString: String = mkStr("")

    override def mkStr(indent: String, short: Boolean): String = {
      def descr(node: Node[A]) = node match {
        case vn: ValueNode[A] => s"ValueNode(${vn.interval})"
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

  def isLeafNode[A](node: Node[A]): Boolean = node.isInstanceOf[LeafNode[A]]
  def isNotLeafNode[A, B](node: Node[A]): Boolean = !isLeafNode(node)

  def mkLeafNode[A, B](): LeafNode[A] = LeafNode()

  def mkValueNode[A](interval: IntervalLike[A])(implicit ord: Ordering[A], iord: Ordering[IntervalLike[A]]): ValueNode[A] =
    ValueNode(interval, interval.end, 1, mkLeafNode(), mkLeafNode())


  class Iter[A](root: Node[A]) extends Iterator[IntervalLike[A]] {
    private var stack: List[ValueNode[A]] = pushLeft(root, List())

    @tailrec
    private def pushLeft(node: Node[A], st: List[ValueNode[A]]): List[ValueNode[A]] =
      node match {
        case _ : LeafNode[A] => st
        case n: ValueNode[A] => pushLeft(n.left, n :: st)
      }

    override def hasNext: Boolean = stack.nonEmpty

    override def next(): IntervalLike[A] = {
      if (!hasNext) throw new NoSuchElementException
      else {
        val node = stack.head
        stack = stack.tail
        val result = node
        stack = pushLeft(node.right, stack)
        result.interval
      }
    }
  }
}

class IntervalTreeSet[A, B](implicit ord: Ordering[A], iord: Ordering[IntervalLike[A]])
    extends mutable.Set[IntervalLike[A]] {

  import IntervalTreeSet._

  var root: Node[A] = LeafNode()
  var size0 = 0

  override def clear(): Unit = {
    root = LeafNode()
    size0 = 0
  }

  override def size: Int = size0

  def find(intervalToFind: IntervalLike[A]): Option[ValueNode[A]] =
    findInternal(intervalToFind) match {
      case vn: ValueNode[A] => Some(vn)
      case _ => None
    }

  def contains(intervalToFind: IntervalLike[A]): Boolean =
    findInternal(intervalToFind) match {
      case vn: ValueNode[A] => true
      case _ => false
    }

  private def findInternal(intervalToFind: IntervalLike[A]): Node[A] = {
    assume(intervalToFind != null)

    var node = root
    var found = false
    while (!node.isInstanceOf[LeafNode[A]] && !found) {
      node match {
        case ValueNode(nodeInterval, maxEnd, level, left, right) =>
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

  override def +=(elem: IntervalLike[A]): IntervalTreeSet.this.type = {
    assume(elem != null)

    if (size0 == Int.MaxValue) throw new IllegalStateException("Maximum size reached")
    else {
      if (!contains(elem)) {
        root = addTo(root, elem)
        size0 += 1
      }
    }
    this
  }

  override def -=(elem: IntervalLike[A]): IntervalTreeSet.this.type = {
    assume(elem != null)

    if (contains(elem)) {
      root = root.asInstanceOf[ValueNode[A]].remove(elem)
      size0 -= 1
    }

    this
  }

  override def iterator: Iterator[IntervalLike[A]] = new Iter(root)

  override def toString = {
    s"IntervalTreeSet($size):\nroot=$root"
  }
}

