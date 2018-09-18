package org.mikesajak.logviewer

import com.google.inject.Key
import com.typesafe.scalalogging.Logger
import context.AppContext
import org.mikesajak.logviewer.config.Configuration
import org.mikesajak.logviewer.log.LogParserMgr
import org.mikesajak.logviewer.ui.ConsoleProgressHandler
import org.mikesajak.logviewer.util.{ResourceManager, UILoader}
import scalafx.application.{JFXApp, Platform}
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.Includes._
import scalafx.animation.{KeyFrame, KeyValue, Timeline}
import scalafx.util.Duration

object Main extends JFXApp {
  private val logger = Logger("Main")
  private val mainPanelDef: String = "/layout/main-panel.fxml"

  logger.info(s"AfternoonCommander starting")

  private val injector = AppContext.globalInjector.createChildInjector()
  private val config = injector.getInstance(classOf[Configuration])
  private val appController= injector.getInstance(classOf[AppController])

  private val resourceMgr: ResourceManager = injector.getInstance(Key.get(classOf[ResourceManager]))

  val (root, _) = UILoader.loadScene(mainPanelDef)

  stage = new PrimaryStage() {
    title = resourceMgr.getMessage("app.name")
//    icons += resourceMgr.getIcon("internal_drive.png")
    scene = new Scene(root)
  }

  appController.init(stage, this)

  // instantiate global standalone beans
  injector.getInstance(classOf[LogParserMgr])
  injector.getInstance(classOf[ConsoleProgressHandler])

  stage.onCloseRequest = we => {
    we.consume()
    appController.exitApplication { () =>
      new Timeline {
        keyFrames.add(KeyFrame(Duration(800), "fadeOut", null, Set(KeyValue(stage.opacity, 0))))
        onFinished = () => Platform.exit
      }.play()
      true
    }
  }

  stage.width = config.getIntProperty("window.width").getOrElse(1000): Int
  stage.height = config.getIntProperty("window.height").getOrElse(600): Int

  stage.toFront()
  stage.opacity.value = 0
  stage.show()

  new Timeline {
    keyFrames.add(KeyFrame(Duration(800), "fadeIn", null, Set(KeyValue(stage.opacity, 1))))
  }.play()


  override def main(args: Array[String]): Unit = {
    super.main(args)
  }
}


