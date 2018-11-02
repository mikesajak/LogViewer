package org.mikesajak.logviewer.util.collection

//object AATree2 {
//  sealed abstract class Node[A <: Ordered[A]](var level: Int)
//  case class LeafNode[A <: Ordered[A]]() extends Node[A](0)
//
//  case class ValueNode[A <: Ordered[A]](var value: A, override var level: Int, var left: Node[A], var right: Node[A])
//    extends Node[A](level)
//}
//
//class AATree2[A <: Ordered[A]] {
//  import AATree2._
//
//  private var root: AATree2.Node[A] = LeafNode[A]()
//
//  def insert(x: A) = {
//    root = insert(x, root)
//  }
//
//  private def insert(x: A, n: AATree2.Node[A]): ValueNode[A] = {
//    n match {
//      case ln: LeafNode[A] => ValueNode(x, 1, LeafNode(), LeafNode())
//      case vn @ ValueNode(v, _, _, _) if v < x =>
//        val l = insert(x, vn.left)
//        vn.left = l
//        l
//      case vn @ValueNode(v, _, _, _) if v > x =>
//        val r = insert(x, vn.right)
//        vn.right = r
//        r
//
//      case _ => throw new IllegalStateException(s"Duplicate item: $x")
//    }
//  }
//}
