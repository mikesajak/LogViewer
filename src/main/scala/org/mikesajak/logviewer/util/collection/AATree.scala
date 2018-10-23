//package org.mikesajak.logviewer.util.collection
//
//import org.mikesajak.logviewer.util.collection.AATree.{DefaultNodeFactory, NodeFactory}
//
//import scala.annotation.tailrec
//import scala.collection.mutable
//
//object AATree {
//  case class Node[A >: Null <: Ordered[A]](var value: A, var level: Int, var left: Node[A], var right: Node[A]) {
//    def isEmptyLeaf: Boolean = value == null
//    def isNotEmptyLeaf: Boolean = value != null
//  }
//
//  trait NodeFactory[A >: Null <: Ordered[A]] {
//    def mkLeafNode(): Node[A]
//    def mkValueNode(value: A): Node[A]
//  }
//
//  class DefaultNodeFactory[A >: Null <: Ordered[A]] extends NodeFactory[A] {
//    override def mkLeafNode(): Node[A] = Node(null, -1, null, null)
//    override def mkValueNode(value: A): Node[A] = Node[A](value, 1, mkLeafNode(), mkLeafNode())
//  }
//
//  class Iter[A >: Null <: Ordered[A]](root: Node[A]) extends Iterator[A] {
//    private var stack = pushLeft(root, List())
//
//    @tailrec
//    private def pushLeft(n: Node[A], st: List[Node[A]]): List[Node[A]] =
//      if (n.isEmptyLeaf) st
//      else pushLeft(n, n.left :: st)
//
//    override def hasNext: Boolean = stack.nonEmpty
//
//    override def next(): A = {
//      if (!hasNext) throw new NoSuchElementException
//      else {
//        val node = stack.head
//        stack = stack.tail
//        val result = node.value
//        stack = pushLeft(node.right, stack)
//        result
//      }
//    }
//  }
//}
//
//class AATree[A >: Null <: Ordered[A]](nodeFactory: NodeFactory[A] = new DefaultNodeFactory[A]())
//    extends mutable.Set[A] {
//  import AATree._
//
//  private var root: Node[A] = nodeFactory.mkLeafNode()
//  private var size0: Int = 0
//
//  override def clear(): Unit = {
//    root = nodeFactory.mkLeafNode()
//    size0 = 0
//  }
//
//  override def size: Int = size0
//
//
//  override def contains(elem: A): Boolean = {
//    assume(elem != null)
//
//    @tailrec
//    def contains(node: Node[A]): Boolean =
//      if (node.isEmptyLeaf) false
//      else {
//        if (elem < node.value) contains(node.left)
//        else if (elem > node.value) contains(node.right)
//        else true
//      }
//
//    contains(root)
//  }
//
//  override def add(elem: A): Boolean = {
//    assume(elem != null)
//    if (size == Int.MaxValue) throw new IllegalStateException("Maximum size reached")
//    if (contains(elem)) false
//    else {
//      root = addTo(root, elem)
//      size0 += 1
//      true
//    }
//  }
//
//  override def remove(elem: A): Boolean = {
//    assume(elem != null)
//    if (!contains(elem)) false
//    else {
//      root = removeFrom(root, elem)
//      size0 -= 1
//      true
//    }
//  }
//
//  def iterator = new Iter[A](root)
//
//  override def +=(elem: A): AATree.this.type = {
//    add(elem)
//    this
//  }
//
//  override def -=(elem: A): AATree.this.type = {
//    remove(elem)
//    this
//  }
//
//
//  protected def addTo(node: Node[A], v: A): Node[A] = {
//    assume(v != null)
//    if (node.isEmptyLeaf) nodeFactory.mkValueNode(v)
//    else {
//      if (v < node.value)      node.left = addTo(node.left, v)
//      else if (v > node.value) node.right = addTo(node.right, v)
//      else throw new IllegalArgumentException(s"""Value \"$v\" is already in a tree""""")
//
//      split(skew(node)) // rebalance this node
//    }
//  }
//
//  protected def removeFrom(node: Node[A], v: A): Node[A] = {
//    if (node.isEmptyLeaf) throw new IllegalArgumentException("""Value \"$v\" is not in a tree""")
//    else {
//      if (v < node.value) {
//        node.left = removeFrom(node.left, v)
//        rebalance(node)
//      } else if (v > node.value) {
//        node.right = removeFrom(node.right, v)
//        rebalance(node)
//      } else {
//        if (node.left.isNotEmptyLeaf) { // find predecessor node
//          var tmp = node.left
//          while (tmp.right.isNotEmptyLeaf)
//            tmp = tmp.right
//
//          node.value = tmp.value // replace value with predecessor
//          node.left = removeFrom(node.left, node.value) // remove predecessor node
//          rebalance(node)
//        } else if (node.right.isNotEmptyLeaf) { // find successor node
//          var tmp = node.right
//          while (tmp.left.isNotEmptyLeaf)
//            tmp = tmp.left
//          node.value = tmp.value // replace value with successor
//          node.right = removeFrom(node.right, node.value) // remove successor node
//          rebalance(node)
//        } else {
//          assume(node.level == 1)
//          nodeFactory.mkLeafNode()
//        }
//      }
//    }
//  }
//
//  private def rebalance(node: Node[A]): Node[A] = {
//    if (node.level == math.min(node.left.level, node.right.level) + 1) node
//    else {
//      if (node.right.level == node.level)
//        node.right.level -= 1
//      node.level -= 1
//      var result = skew(node)
//      if (result.right.right.isNotEmptyLeaf)
//        result.right.right = skew(result.right.right)
//      result = split(result)
//      result.right = split(result.right)
//      result
//    }
//  }
//
//  /*
//   *       |          |
//   *   A - B    ->    A - B
//   *  /   / \        /   / \
//   * 0   1   2      0   1   2
//   */
//  private def skew(node: Node[A]): Node[A] = {
//    assume(node.isNotEmptyLeaf)
//    if (node.left.level < node.level) node
//    else {
//      val result = node.left
//      node.left = result.right
//      result.right = node
//      result
//    }
//  }
//
//  /*
//   *   |                      |
//   *   |                    - B -
//   *   |                   /     \
//   *   A - B - C    ->    A       C
//   *  /   /   / \        / \     / \
//   * 0   1   2   3      0   1   2   3
//   */
//  private def split(node: Node[A]): Node[A] = {
//    assume(node.isNotEmptyLeaf)
//    // must short-circuit because if right.level < self.level, then right.right might be null
//    if (node.right.level < node.level || node.right.right.level < node.level) node
//    else {
//      val result = node.right
//      node.right = result.left
//      result.left = node
//      result.level += 1
//      result
//    }
//  }
//
//  def checkStructure(node: Node[A], visitedNodes: mutable.Set[Node[A]]): Int = {
//    if (node.isEmptyLeaf) 0
//    else {
//      assume(visitedNodes.add(node))
//      assume(!(node.value == null || node.left == null || node.right == null))
//      assume(node.level > 0 && node.level == node.left.level + 1 && (node.level == node.right.level + 1 || node.level == node.right.level))
//      assume(!(node.left.isNotEmptyLeaf && !(node.left.value < node.value)))
//      assume(!(node.right.isNotEmptyLeaf && !(node.right.value > node.value)))
//
//      val size = 1 + checkStructure(node.left, visitedNodes) + checkStructure(node.right, visitedNodes)
//      assume(!(size < (1 << node.level) - 1))
//      // not checked, but (size <= 3 ^ level - 1) is also true
//      size
//    }
//  }
//}
