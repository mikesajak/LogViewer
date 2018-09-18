package org.mikesajak.logviewer

import java.io.File

import com.typesafe.scalalogging.Logger
import org.mikesajak.logviewer.config.Configuration
import org.mikesajak.logviewer.util.EventBus
import scalafx.stage.{DirectoryChooser, FileChooser}

class OperationMgr(appController: AppController, eventBus: EventBus, config: Configuration) {
  private val logger = Logger[OperationMgr]

  def handleRefreshAction(): Unit = {
    logger.warn("Refresh not implemented yet.")
  }

  def handleCloseApplication(): Unit = {
    appController.exitApplication()
  }

  def handleOpenLogDir(): Unit = {
    val dirChooser = new DirectoryChooser()
    dirChooser.initialDirectory = new File(System.getProperty("user.dir"))
    val result = Option(dirChooser.showDialog(appController.mainStage))

    result.foreach(files => eventBus.publish(OpenLogRequest(List(files))))
  }

  def handleOpenLogFiles(): Unit = {
    val fileChooser = new FileChooser()
    fileChooser.initialDirectory = new File(System.getProperty("user.dir"))
    val result = Option(fileChooser.showOpenMultipleDialog(appController.mainStage))

    result.foreach(files => eventBus.publish(OpenLogRequest(files)))
  }
}

case class OpenLogRequest(files: Seq[File])
