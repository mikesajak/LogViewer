package org.mikesajak.logviewer.ui.controller

import org.mikesajak.logviewer.OperationMgr
import scalafx.Includes._
import scalafx.scene.input.{KeyCode, KeyEvent}
import scalafx.scene.layout.Pane
import scalafxml.core.macros.sfxml

@sfxml
class MainPanelController(mainPane: Pane,
                          operationMgr: OperationMgr) {
  mainPane.filterEvent(KeyEvent.KeyPressed) { ke: KeyEvent =>
    ke.code match {
      case KeyCode.R if ke.controlDown => operationMgr.handleRefreshAction()
      case _ => // do nothing
    }
  }
}
