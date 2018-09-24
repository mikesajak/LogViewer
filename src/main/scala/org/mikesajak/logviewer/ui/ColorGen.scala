package org.mikesajak.logviewer.ui

import java.text.MessageFormat

import org.mikesajak.logviewer.util.Check

class ColorGen {
  private var index = 0
  private val intensityGen = new IntensityGenerator()

  def nextColor(): String = {
    val color = MessageFormat.format(PatternGenerator.nextPattern(index),
                                     intensityGen.nextIntensity(index))
    index += 1
    color
  }

}

object PatternGenerator {
  def nextPattern(index: Int): String = index % 7 match {
    case 0 => "{0}CCCC"
    case 1 => "CC{0}CC"
    case 2 => "CCCC{0}"
    case 3 => "{0}{0}CC"
    case 4 => "{0}CC{0}"
    case 5 => "CC{0}{0}"
    case 6 => "{0}{0}{0}"
  }
}

class IntensityGenerator {
  private var walker: IntensityValueWalker= _
  private var current = 0

  def nextIntensity(index: Int): String = {
    if (index == 0)
      current = 255
    else if (index % 7 == 0) {
      if (walker == null)
        walker = new IntensityValueWalker()
      else
        walker.moveNext()

      current = walker.currentValue.value
    }

    var currentText = current.toHexString
    if (currentText.length == 1)
      currentText = "0" + currentText
    currentText
  }

}

class IntensityValueWalker {
  private var current = new IntensityValue(null, 1 << 7, 1)

  def currentValue: IntensityValue = current

  def moveNext(): Unit = {
    if (current.parent == null)
      current = current.childA
    else if (current.parent.childA == current)
      current = current.parent.childB
    else {
      var levelsUp = 1
      current = current.parent
      while (current.parent != null && current == current.parent.childB) {
        current = current.parent
        levelsUp += 1
      }
      if (current.parent != null)
        current = current.parent.childB
      else
        levelsUp += 1

      for (i <- 0 until levelsUp)
        current = current.childA
    }

  }

}

case class IntensityValue(parent: IntensityValue, value: Int, level: Int) {
  Check.argument(level <= 7, "There are no more colors left")

  lazy val childA = IntensityValue(this, this.value - (1 << (7 - level)), level + 1)
  lazy val childB = IntensityValue(this, this.value + (1 << (7 - level)), level + 1)
}
