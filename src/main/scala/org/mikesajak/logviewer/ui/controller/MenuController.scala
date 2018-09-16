package org.mikesajak.logviewer.ui.controller

import org.mikesajak.logviewer.OperationMgr
import scalafxml.core.macros.sfxml

@sfxml
class MenuController(opsManager: OperationMgr) {
  def onFileOpen(): Unit = {
    opsManager.handleOpenLog()
  }

  def onFileClose(): Unit = {
    opsManager.handleCloseApplication()
  }
}
