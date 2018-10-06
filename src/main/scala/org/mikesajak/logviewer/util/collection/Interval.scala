package org.mikesajak.logviewer.util.collection

import java.util.Comparator

import enumeratum.Enum
import org.mikesajak.logviewer.util.collection.Interval.{Bounded, Unbounded}

import scala.collection.immutable


/**
  * A representation of a generic interval. The interval can be open or closed (the start
  * and end points may be inclusive or exclusive), as well as bounded and unbounded (it can
  * extend to positive or negative infinity).
  *
  * The class doesn't assume that the intervals are numeric, instead it is generalized to
  * represent a contiguous subset of elements, where contiguity is defined with respect to
  * an arbitrary total order function. These elements can be for example {@link java.util.Date}s
  * or basically any type, the elements of which can be compared to one another. Since the
  * class requires its generic variable to implement the {@link Comparable} interface, all
  * comparisons in the internals of the {@code Interval} class are done via the interface method.
  *
  * When subclassing the {@code Interval} class, note that the start and end points of the
  * interval <strong>can</strong> be {@code null}. A {@code null} start point represents
  * the negative infinity and a {@code null} end point represents positive infinity. This
  * fact needs to be kept in mind in particular when overwriting methods with default
  * implementations in the {@code Interval} class, such as {@link #contains(Comparable)},
  * {@link #isLeftOf(Comparable)}, and in particular {@link #equals(Object)} and
  * {@link #hashCode()}.
  *
  * @param < T> The type that represents a single point from the domain of definition of the
  *          interval.
  */
object Interval {
  /**
    * An enum representing all possible types of bounded intervals.
    */
  sealed abstract class Bounded(val startInclusive: Boolean, val endInclusive: Boolean)
  object Bounded extends Enum[Bounded] {
    val values : immutable.IndexedSeq[Bounded] = findValues

    /**
      * An interval, in which both start and end point are exclusive.
      */
    case object OPEN extends Bounded(false, false)

    /**
      * An interval, in which both start and end point are inclusive.
      */
    case object CLOSED extends Bounded(true, true)

    /**
      * An interval, in which the start is exclusive and the end is inclusive.
      */
    case object CLOSED_RIGHT extends Bounded(false, true)

    /**
      * An interval, in which the start is inclusive and the end is exclusive.
      */
    case object CLOSED_LEFT extends Bounded(true, false)
  }

  sealed abstract class Unbounded(val startInclusive: Boolean, val endInclusive: Boolean) {
    def start[T >: Null <: Ordered[T]](value: T): T
    def end[T >: Null <: Ordered[T]](value: T): T
  }
  object Unbounded extends Enum[Unbounded] {
    val values: immutable.IndexedSeq[Unbounded] = findValues
    /**
      * An interval extending to positive infinity and having an exclusive start
      * point as a lower bound. For example, (5, +inf)
      */
    case object OPEN_LEFT extends Unbounded(false, true) {
      override def start[T >: Null <: Ordered[T]](value: T): T = value
      override def end  [T >: Null <: Ordered[T]](value: T): T = value
    }

    /**
      * An interval extending to positive infinity and having an inclusive start
      * point as a lower bound. For example, [5, +inf)
      */
    case object CLOSED_LEFT extends Unbounded(true, true) {
      override def start[T >: Null <: Ordered[T]](value: T): T = value
      override def end[T >: Null <: Ordered[T]](value: T): T = null
    }

    /**
      * An interval extending to negative infinity and having an exclusive end
      * point as an upper bound. For example, (-inf, 5)
      */
    case object OPEN_RIGHT extends Unbounded(true, false) {
      override def start[T >: Null <: Ordered[T]](value: T): T = null
      override def end[T >: Null <: Ordered[T]](value: T): T = value
    }

    /**
      * An interval extending to negative infinity and having an inclusive end
      * point as an upper bound. For example, (-inf, 5]
      */
    case object CLOSED_RIGHT extends Unbounded(true, true) {
      override def start[T >: Null <: Ordered[T]](value: T): T = null
      override def end[T >: Null <: Ordered[T]](value: T): T = value
    }
  }

  /**
    * A comparator that can be used as a parameter for sorting functions. The start comparator sorts the intervals
    * in <em>ascending</em> order by placing the intervals with a smaller start point before intervals with greater
    * start points. This corresponds to a line sweep from left to right.
    * <p>
    * Intervals with start point null (negative infinity) are considered smaller than all other intervals.
    * If two intervals have the same start point, the closed start point is considered smaller than the open one.
    * For example, [0, 2) is considered smaller than (0, 2).
    * </p>
    * <p>
    * To ensure that this comparator can also be used in sets it considers the end points of the intervals, if the
    * start points are the same. Otherwise the set will not be able to handle two different intervals, sharing
    * the same starting point, and omit one of the intervals.
    * </p>
    * <p>
    * Since this is a static method of a generic class, it involves unchecked calls to class methods. It is left to
    * ths user to ensure that she compares intervals from the same class, otherwise an exception might be thrown.
    * </p>
    */
  val sweepLeftToRight: Comparator[Interval[_ <: Comparable[_]]] = new Comparator[Interval[_ <: Comparable[_]]]() {
    override def compare(a: Interval[_ <: Comparable[_]], b: Interval[_ <: Comparable[_]]): Int = {
      a.compareStarts(b) match {
        case d if d != 0 => d
        case _ =>
          a.compareEnds(b) match {
            case d if d != 0 => d
            case _ => a.compareSpecialization(b)
          }
      }
    }
  }

  /**
    * A comparator that can be used as a parameter for sorting functions. The end comparator sorts the intervals
    * in <em>descending</em> order by placing the intervals with a greater end point before intervals with smaller
    * end points. This corresponds to a line sweep from right to left.
    * <p>
    * Intervals with end point null (positive infinity) are placed before all other intervals. If two intervals
    * have the same end point, the closed end point is placed before the open one. For example,  [0, 10) is placed
    * after (0, 10].
    * </p>
    * <p>
    * To ensure that this comparator can also be used in sets it considers the start points of the intervals, if the
    * end points are the same. Otherwise the set will not be able to handle two different intervals, sharing
    * the same end point, and omit one of the intervals.
    * </p>
    * <p>
    * Since this is a static method of a generic class, it involves unchecked calls to class methods. It is left to
    * ths user to ensure that she compares intervals from the same class, otherwise an exception might be thrown.
    * </p>
    */
  val sweepRightToLeft: Comparator[Interval[_ <: Comparable[_]]] = new Comparator[Interval[_ <: Comparable[_]]]() {
    override def compare(a: Interval[_ <: Comparable[_]], b: Interval[_ <: Comparable[_]]): Int = {
      b.compareEnds(a) match {
        case d if d != 0 => d
        case _ =>
          b.compareStarts(a) match {
            case d if d != 0 => d
            case _ => a.compareSpecialization(b)
          }
      }
    }
  }

}

abstract class Interval[T <: Comparable[_ >: T]](val start: T, val end: T,
                                                  val startInclusive: Boolean = true,
                                                  val endInclusive: Boolean = true) {
  def this(start: T, end: T, boundedType: Bounded) =
    this(start, end, boundedType.startInclusive, boundedType.endInclusive)

  def this(value: T, unboundedType: Unbounded) =
    this(unboundedType.start(value), unboundedType.end(value),
          unboundedType.startInclusive, unboundedType.endInclusive)

  def isEmpty: Boolean = {
    if (start == null || end == null) false
    start.compareTo(end) match {
      case d if d > 0 => true
      case 0 if !endInclusive || !startInclusive => true
      case _ => false
    }
  }

  protected def create: Interval[T]
  def midpoint: T

  protected def create(start: T, startInclusive: Boolean, end: T, endInclusive: Boolean): Interval[T] = {
    ???
  }

  def isPoint: Boolean =
    if (start == null || end == null) false
    else start.compareTo(end) == 0 && startInclusive && endInclusive

  def contains(query: T): Boolean =
    if (isEmpty || query == null) false
    else {
      val startCompare = if (start == null) 1 else query.compareTo(start)
      val endCompare = if (end == null) -1 else query.compareTo(end)

      if (startCompare > 0 && endCompare < 0) true
      else (startCompare == 0 && startInclusive) || (endCompare == 0 && endInclusive)
    }

  def intersection(other: Interval[T]): Interval[T] =
    if (other == null || isEmpty || other.isEmpty) null
    else {
      if ((other.start == null && start != null) || (start != null && start.compareTo(other.start) > 0))
        other.intersection(this)
      else if (end != null && other.start != null && (end.compareTo(other.start) < 0 || (end.compareTo(other.start) == 0 && (!endInclusive || !other.startInclusive))))
        null
      else {
        val (newStart, newStartInclusive) =
          if (other.start == null) (other.start, true)
          else {
            val startInc = if (start != null && other.start.compareTo(start) == 0)
                             other.startInclusive && startInclusive
                           else other.startInclusive
            (other.start, startInc)
          }

        val (newEnd, newEndInclusive) =
          if (end == null) (other.end, other.endInclusive)
          else if (other.end == null) (end, endInclusive)
          else end.compareTo(other.end) match {
            case 0 =>          (end, endInclusive && other.endInclusive)
            case d if d < 0 => (end, endInclusive)
            case _ =>          (other.end, other.endInclusive)
          }

        val intersection = create(newStart, newStartInclusive, newEnd, newEndInclusive)
        if (intersection.isEmpty) null else intersection
      }
    }

  def contains(another: Interval[T]): Boolean =
    if (another == null || isEmpty || another.isEmpty) false
    else {
      val intersect = intersection(another)
      intersect != null && intersect == another
    }

  def intersects(query: Interval[T]): Boolean =
    if (query == null) false
    else {
      val intersect = intersection(query)
      intersect != null
    }

  def isRightOf(point: T, inclusive: Boolean = true): Boolean =
    if (point == null || start == null) false
    else point.compareTo(start) match {
      case d if d != 0 => d < 0
      case _ => startInclusive || inclusive
    }

  def isRightOf(other: Interval[T]): Boolean =
    if (other == null || other.isEmpty) false
    else isRightOf(other.end, other.endInclusive)

  def isLeftOf(point: T, inclusive: Boolean = true): Boolean =
    if (point == null || end == null) false
    else point.compareTo(end) match {
      case d if d != 0 => d > 0
      case _ => !endInclusive || !inclusive

  def isLeftOf(other: Interval[T]): Boolean =
    if (start == null || other.isEmpty) false
    else isLeftOf(other.start, other.startInclusive)

  private def compareStarts(other: Interval[T]) =
    if (start == null && other.start == null) 0
    else if (start == null) -1
    else if (other.start == null) 1
    else start.compareTo(other.start) match {
      case d if d != 0 => d
      case _ =>
        if (startInclusive ^ other.startInclusive) {
          if (startInclusive) - 1 else 1
        } else 0
      }
    }

}
