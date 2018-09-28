package org.mikesajak.logviewer.ui.controller

import scalafx.Includes._
import scalafx.collections.ObservableBuffer
import scalafx.scene.control._
import scalafxml.core.macros.sfxml

trait TimeShiftPanelController {
  def init(sources: Seq[String], dialog: Dialog[(String, Long)])
}

@sfxml
class TimeShiftPanelControllerImpl(sourcesComboBox: ComboBox[String],
                                   offsetTextField: TextField)
  extends TimeShiftPanelController {
  override def init(sources: Seq[String], dialog: Dialog[(String, Long)]): Unit = {
    sourcesComboBox.items = ObservableBuffer(sources)
    dialog.title = "Time shift"
    val okButton = dialog.dialogPane().lookupButton(ButtonType.OK)
    okButton.disable = true

    offsetTextField.text.onChange { (_, _, offsTxt) =>
      okButton.disable = try {
        offsTxt.toLong
        offsetTextField.styleClass -= "validation-error"
        false
      } catch {
        case e: NumberFormatException =>
          offsetTextField.styleClass += "validation-error"
          true
      }
    }

    dialog.resultConverter = {
      case ButtonType.OK => (sourcesComboBox.selectionModel.value.getSelectedItem, offsetTextField.text.value.toLong)
      case _ => null
    }
  }

}
