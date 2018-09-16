package org.mikesajak.logviewer.util

import java.util.{Locale, ResourceBundle}

import com.ibm.icu.text.MessageFormat
import com.typesafe.scalalogging.Logger
import scalafx.scene.image.Image

class ResourceManager(defaultBundle: String) {

  private case class CacheKey(path: String, size: Option[(Double, Double)])
  private object CacheKey {
    def apply(path: String): CacheKey = new CacheKey(path, None)
  }
  private var cache: Map[CacheKey, Image] = Map()

  private def getImage(key: CacheKey) = {
    if (!cache.contains(key)) {
      val image = mkImage(key.path, key.size)
      cache += key -> image
    }
    cache(key)
  }

  private def mkImage(path: String, size: Option[(Double, Double)]) =
    size.map(s => new Image(path, s._1, s._2, true, true))
    .getOrElse(new Image(path))

  def getIcon(name: String): Image = {
    val imagePath = s"/images/$name"
    try {
      getImage(CacheKey(imagePath))
    } catch {
      case e: Exception =>
        Logger[ResourceManager].warn(s"Exception thrown during getting icon $imagePath", e)
        throw e
    }
  }

  def getIcon(name: String, width: Double, height: Double): Image = {
    val imagePath = s"/images/$name"
    try {
      getImage(CacheKey(imagePath, Some((width, height))))
    } catch {
      case e: Exception =>
        Logger[ResourceManager].warn(s"Exception thrown during getting icon $imagePath, width=$width, height=$height", e)
        throw e
    }
  }

  def getMessage(key: String, resourceFile: String = defaultBundle, locale: Locale = Locale.getDefault()): String =
    ResourceBundle.getBundle(resourceFile).getString(key)

  def getMessageOpt(key: String, resourceFile: String = defaultBundle, locale: Locale = Locale.getDefault()): Option[String] =
    if (ResourceBundle.getBundle(resourceFile).containsKey(key))
      Some(getMessage(key, resourceFile, locale))
    else None


  def getMessageWithArgs(key: String, args: Seq[Any],
                         resourceFile: String = defaultBundle, locale: Locale = Locale.getDefault): String = {
    val pattern = getMessage(key, resourceFile)
    val formatter = new MessageFormat("")
    formatter.setLocale(locale)
    formatter.applyPattern(pattern)
    formatter.format(args.toArray)
  }
}
