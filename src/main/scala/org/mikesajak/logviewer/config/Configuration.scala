package org.mikesajak.logviewer.config

import java.io.{File, FileWriter}

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions, ConfigValueFactory}
import com.typesafe.scalalogging.Logger
import org.mikesajak.logviewer.util.EventBus

import scala.collection.JavaConverters._

class Configuration(filename: String, eventBus: EventBus) {
  private val logger = Logger[Configuration]
  private var config: Config = ConfigFactory.empty("LogViewer settings")

  private val appName = "logviewer"

  def load(): Unit = {
    logger.info(s"Loading settings from $filename")
    config = ConfigFactory.parseFile(new File(filename))
    config = config.withOnlyPath(appName)
    logger.debug(s"Current config:\n${renderConfig()}")
  }

  def renderConfig(): String = {
    val opts = ConfigRenderOptions.defaults()
               .setOriginComments(false)
               .setFormatted(true)
               .setJson(false)
    config.withOnlyPath(appName).root.render(opts)
  }

  def save(): Unit = {
    logger.info(s"Saving settings to $filename")
    val contents = renderConfig()
    val writer = new FileWriter(filename)
    writer.write(contents)
    writer.close()
  }

  def getBoolProperty(key: String): Option[Boolean] = {
    val path = s"$appName.$key"
    if (config.hasPath(path)) Some(config.getBoolean(path)) else None
  }

  def setBoolProperty(key: String, value: Boolean): Unit = {
    config = config.withValue(s"$appName.$key", ConfigValueFactory.fromAnyRef(value))
    notifyConfigChanged(key)
  }

  def getIntProperty(key: String): Option[Int] = {
    val path = s"$appName.$key"
    if (config.hasPath(path)) Some(config.getInt(path)) else None
  }

  def setIntProperty(key: String, value: Int): Unit = {
    config = config.withValue(s"$appName.$key", ConfigValueFactory.fromAnyRef(value))
    notifyConfigChanged(key)
  }

  def getStringProperty(key: String): Option[String] = {
    val path = s"$appName.$key"
    if (config.hasPath(path)) Some(config.getString(path)) else None
  }

  def setStringProperty(key: String, value: String): Unit = {
    config = config.withValue(s"$appName.$key", ConfigValueFactory.fromAnyRef(value))
    notifyConfigChanged(key)
  }

  def getStringSeqProperty(key: String): Option[Seq[String]] = {
    val path = s"$appName.$key"
    if (config.hasPath(path)) Some(config.getStringList(path).asScala) else None
  }

  def setStringSeqProperty(key: String, value: Seq[String]): Unit = {
    config = config.withValue(s"$appName.$key", ConfigValueFactory.fromIterable(value.asJava))
    notifyConfigChanged(key)
  }

  private def notifyConfigChanged(key: String): Unit = {
    eventBus.publish(ConfigChange(key))
  }

}

case class ConfigChange(key: String)