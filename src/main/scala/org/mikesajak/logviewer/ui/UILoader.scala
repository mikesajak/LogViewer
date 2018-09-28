package org.mikesajak.logviewer.ui

import java.io.IOException
import java.util.ResourceBundle

import com.google.inject.{Injector, Module}
import javafx.scene.Parent
import org.mikesajak.logviewer.context.AppContext
import scalafxml.core.FXMLLoader
import scalafxml.guice.GuiceDependencyResolver

object UILoader {

  def loadScene[CtrlType](layout: String, additionalContexts: Module*): (Parent, CtrlType) =
    loadScene[CtrlType](layout, "logviewer.css", "logviewer", additionalContexts: _*)

  def loadScene[CtrlType](layout: String, css: String, resourceBundle: String, additionalContexts: Module*): (Parent, CtrlType) = {
    val (root, loader)= loadSceneImpl(layout, css, resourceBundle, additionalContexts: _*)
    val controller = loader.getController[CtrlType]()
    (root, controller)
  }

  def loadScene2(layout: String, additionalContexts: Module*): Parent =
    loadScene2(layout, "logviewer.css", "logviewer", additionalContexts: _*)

  def loadScene2(layout: String, css: String, resourceBundle: String, additionalContexts: Module*): Parent = {
    val (root, _) = loadSceneImpl(layout, css, resourceBundle, additionalContexts: _*)
    root
  }

  private def loadSceneImpl(layout: String, css: String, resourceBundle: String, additionalContexts: Module*) = {
    implicit val injector: Injector = AppContext.globalInjector.createChildInjector(additionalContexts: _*)

    val resource = getClass.getResource(layout)
    if (resource == null)
      throw new IOException(s"Cannot load resource: $layout")

    val loader = new FXMLLoader(resource, new GuiceDependencyResolver())
    loader.setResources(ResourceBundle.getBundle(resourceBundle))
    loader.load()

    val root = loader.getRoot[Parent]()
    root.getStylesheets.add(getClass.getResource(s"/$css").toExternalForm)

    (root,loader)
  }
}
