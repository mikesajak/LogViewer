package org.mikesajak.logviewer.ui

import javafx.event.EventHandler
import javafx.stage.WindowEvent
import org.controlsfx.control.PopOver

class PopOverEx extends PopOver {
  def animated: Boolean = isAnimated
  def animated_=(a: Boolean): Unit = setAnimated(a)

  def arrowLocation: PopOver.ArrowLocation = getArrowLocation
  def arrowLocation_=(al: PopOver.ArrowLocation): Unit = setArrowLocation(al)

  def autoHide: Boolean = isAutoHide
  def autoHide_=(a: Boolean): Unit = setAutoHide(a)

  def closeButtonEnabled: Boolean = isCloseButtonEnabled
  def closeButtonEnabled_=(en: Boolean): Unit = setCloseButtonEnabled(en)

  def detached: Boolean = isDetached
  def detached_=(d: Boolean): Unit = setDetached(d)

  def detachable: Boolean = isDetachable
  def detachable_=(d: Boolean): Unit = setDetachable(d)

  def focused: Boolean = isFocused

  def headerAlwaysVisible: Boolean = isHeaderAlwaysVisible
  def headerAlwaysVisible_=(v: Boolean): Unit = setHeaderAlwaysVisible(v)

  def hideOnEscape: Boolean = isHideOnEscape
  def hideOnEscape_=(h: Boolean): Unit = setHideOnEscape(h)

  def showing: Boolean = isShowing

  def title: String = getTitle
  def title_=(t: String): Unit = setTitle(t)

  def onHidden: EventHandler[WindowEvent] = getOnHidden
  def onHidden_=(eh: EventHandler[WindowEvent]): Unit = setOnHidden(eh)
}