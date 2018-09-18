package org.mikesajak.logviewer.ui.controller

import org.mikesajak.logviewer.OperationMgr
import scalafxml.core.macros.sfxml

@sfxml
class MenuController(opsManager: OperationMgr) {
  def onDirOpen(): Unit = opsManager.handleOpenLogDir()
  def onFilesOpen(): Unit = opsManager.handleOpenLogFiles()

  def onFileClose(): Unit = opsManager.handleCloseApplication()
}
