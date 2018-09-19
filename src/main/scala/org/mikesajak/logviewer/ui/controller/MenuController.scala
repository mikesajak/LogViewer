package org.mikesajak.logviewer.ui.controller

import org.mikesajak.logviewer.OperationMgr
import scalafxml.core.macros.sfxml

@sfxml
class MenuController(opsManager: OperationMgr) {
  def onDirOpen(): Unit = opsManager.handleOpenLogDir(false)
  def onFilesOpen(): Unit = opsManager.handleOpenLogFiles(false)

  def onDirAppend(): Unit = opsManager.handleOpenLogDir(true)
  def onFilesAppend(): Unit = opsManager.handleOpenLogFiles(true)

  def onFileClose(): Unit = opsManager.handleCloseApplication()
}
