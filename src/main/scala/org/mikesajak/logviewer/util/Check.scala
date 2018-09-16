package org.mikesajak.logviewer.util

object Check {
  def notNull[A](arg: A): A =
    if (arg == null) throw new NullPointerException()
    else arg

  def notNull[A](arg: A, errorMsg: => String): A =
    if (arg == null) throw new NullPointerException(errorMsg)
    else arg

  def argument(expr: Boolean): Unit =
    if (!expr) throw new IllegalArgumentException()

  def argument(expr: Boolean, errorMsg: => String): Unit =
    if (!expr) throw new IllegalArgumentException(errorMsg)

  def state(expr: Boolean): Unit =
    if (!expr) throw new IllegalStateException()

  def state(expr: Boolean, errorMsg: => String): Unit =
    if (!expr) throw new IllegalStateException(errorMsg)
}
