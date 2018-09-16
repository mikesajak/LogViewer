package org.mikesajak.logviewer

import java.io.File

import com.typesafe.scalalogging.Logger
import org.mikesajak.logviewer.util.EventBus
import scalafx.stage.FileChooser

class OperationMgr(appController: AppController, eventBus: EventBus) {
  private val logger = Logger[OperationMgr]

  def handleRefreshAction(): Unit = {
    logger.warn("Refresh not implemented yet.")
  }

  def handleCloseApplication(): Unit = {
    appController.exitApplication()
  }

  def handleOpenLog(): Unit = {
    val fileChooser = new FileChooser()
    val result = Option(fileChooser.showOpenDialog(appController.mainStage))
    result.foreach(file => eventBus.publish(OpenLogRequest(file)))
  }
}

case class OpenLogRequest(file: File)
