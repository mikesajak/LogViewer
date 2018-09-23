package org.mikesajak.logviewer.util

import com.google.common.eventbus.{DeadEvent, Subscribe}
import com.typesafe.scalalogging.Logger

class EventBus {
  private val eventBus = new com.google.common.eventbus.EventBus("App event bus")
  private val logger = Logger[EventBus]
  eventBus.register(new DeadEventHandler)

  def logScope[A](name: => String)(code: () => A): A = {
    logger.trace(s"Start: $name")
    val r = code()
    logger.trace(s"End: $name")
    r
  }

  def publish[A](event: A): Unit = {
    eventBus.post(event)
  }

  def register(subscriber: AnyRef): Unit = eventBus.register(subscriber)
  def unregister(subscriber: AnyRef): Unit = eventBus.unregister(subscriber)

}

class DeadEventHandler {
  private val logger = Logger[DeadEventHandler]
  @Subscribe
  def handleDeadEvent(de: DeadEvent): Unit = {
    logger.debug(s"$de")
  }
}
