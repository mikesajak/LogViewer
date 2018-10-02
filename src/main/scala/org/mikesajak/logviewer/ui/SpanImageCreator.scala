package org.mikesajak.logviewer.ui

import scalafx.scene.canvas.Canvas

class SpanImageCreator {

  val colorGenerator = new SimpleColorGenerator

  val spanLineWidth = 5
  val spanWidth = 6
  val spanHeight = 16

  
  def drawSpans(numAllSpans: Int, spanPositions: Seq[Int]): Canvas = {
    val canvas = new Canvas(numAllSpans * spanWidth, spanHeight)
    val gc = canvas.graphicsContext2D

//    gc.fill = Color.Transparent
//    gc.fillRect(0, 0, canvas.width.value, canvas.height.value)

    gc.lineWidth = spanLineWidth
    for (spanPos <- spanPositions) {
      val spanColor = colorGenerator.getColor(spanPos + 10)
      gc.stroke = spanColor
      gc.fill = spanColor
      val spanImagePos = spanPos * spanWidth + spanLineWidth/2
      gc.strokeLine(spanImagePos, 0, spanImagePos, canvas.height.value)
    }

    canvas
  }

  def getColorBoxFor(id: String, size: Int): Canvas = {
    val canvas = new Canvas(size, size)
    val gc = canvas.graphicsContext2D
    gc.fill = colorGenerator.getColor(id)
    gc.fillRect(0, 0, size, size)
    canvas
  }

}
