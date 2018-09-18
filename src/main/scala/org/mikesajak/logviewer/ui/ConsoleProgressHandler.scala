package org.mikesajak.logviewer.ui

import com.google.common.eventbus.Subscribe
import com.typesafe.scalalogging.Logger
import org.mikesajak.logviewer.log.ParseProgress
import org.mikesajak.logviewer.util.EventBus

class ConsoleProgressHandler(eventBus: EventBus) {
  private val logger = Logger[ConsoleProgressHandler]

  eventBus.register(this)

  @Subscribe
  def handleProgress(event: ParseProgress): Unit = {
    logger.debug(s"Progress: ${(100*event.progress).toInt}%, ${event.message}")
  }
}
