//package org.mikesajak.logviewer.util.collection
//
//import org.mikesajak.logviewer.util.collection.AATree.Node
//
//
//class IntervalOrdering[T >: Null <: Ordered[T]] extends Ordering[Interval[T]] {
//  override def compare(x: Interval[T], y: Interval[T]): Int = x.start compare y.start
//}
//
//case class Interval[A >: Null <: Ordered[A]](start: A, end: A)
//
//class IntervalNode[A >: Null <: Ordered[A]](v: Interval[A], var maxRightValue: A,
//                                            lev: Int,
//                                            l: IntervalNode[A], r: IntervalNode[A])
//    extends AATree.Node[Interval[A]](v, lev, l, r)
//
//class IntervalNodeFactory[A >: Null <: Ordered[A]] extends AATree.NodeFactory[Interval[A]] {
//  override def mkLeafNode(): IntervalNode[A] =
//    new IntervalNode[A](null, null, -1, null, null)
//
//  override def mkValueNode(value: Interval[A]): IntervalNode[A] =
//    new IntervalNode[A](value, value.end, 1, mkLeafNode(), mkLeafNode())
//}
//
//class IntervalTree[A >: Null <: Ordered[A]] extends AATree[Interval[A]](new IntervalNodeFactory[A]()) {
//  override protected def addTo(node: Node[Interval[A]], v: Interval[A]): Node[Interval[A]] = {
//    val resultNode = super.addTo(node, v).asInstanceOf[IntervalNode[A]] // FIXME: hack, instanceof...
//
//    resultNode.maxRightValue = null // TODO: calculate
//    resultNode
//  }
//}