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

  def handleOpenLogDir(append: Boolean): Unit = {
    val dirChooser = new DirectoryChooser()
    dirChooser.initialDirectory = new File(System.getProperty("user.dir"))
    val result = Option(dirChooser.showDialog(appController.mainStage))

    result.foreach(dir => eventBus.publish(if (!append) OpenLogRequest(List(dir)) else AppendLogRequest(List(dir))))
  }

  def handleOpenLogFiles(append: Boolean): Unit = {
    val fileChooser = new FileChooser()
    fileChooser.initialDirectory = new File(System.getProperty("user.dir"))
    val result = Option(fileChooser.showOpenMultipleDialog(appController.mainStage))

    result.foreach(files => eventBus.publish(if (!append) OpenLogRequest(files) else AppendLogRequest(files)))
  }

}

case class OpenLogRequest(files: Seq[File])
case class AppendLogRequest(files: Seq[File])
