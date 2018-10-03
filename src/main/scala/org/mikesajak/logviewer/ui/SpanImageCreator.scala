package org.mikesajak.logviewer.ui

import scalafx.scene.canvas.{Canvas, GraphicsContext}
import scalafx.scene.paint.Color

class SpanImageCreator {

  val colorGenerator = new SimpleColorGenerator

  val spanLineWidth = 2.0
  val spanWidth = 6.0
  val spanHeight = 16.0
  val spanBubbleSize = 5.0

  def drawSpans(spans: Seq[(String, Boolean)], curSpanId: String): Canvas = {
    val canvas = new Canvas(spans.size * spanWidth, spanHeight)
    val gc = canvas.graphicsContext2D
    gc.lineWidth = spanLineWidth

    for (((spanId, show), pos) <- spans.zipWithIndex; if show) {
      val spanColor = colorGenerator.getColor(spanId)
      gc.stroke = spanColor
      gc.fill = spanColor
      val spanPos = pos * spanWidth + spanWidth/2 - spanLineWidth/2
      gc.strokeLine(spanPos, 0, spanPos, canvas.height.value)

      if (spanId == curSpanId)
        gc.fillRect(spanPos - spanBubbleSize/2, spanHeight/2 - spanBubbleSize/2,
                    spanBubbleSize, spanBubbleSize)
    }

    canvas
  }
  
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
    gc.fill = getColor(id)
    gc.fillRect(0, 0, size, size)
    canvas
  }

  def getBeginSpanIcon(id: String, size: Int): Canvas = {
    val canvas = new Canvas(size, size)
    val gc = canvas.graphicsContext2D
    val color = getColor(id)
    drawBubble(gc, color, spanBubbleSize, size)
    drawBeginLine(gc, color, size)
    canvas
  }

  def getMiddleSpanIcon(id: String, size: Int): Canvas = {
    val canvas = new Canvas(size, size)
    val gc = canvas.graphicsContext2D
    val color = getColor(id)
    drawBubble(gc, color, spanBubbleSize, size)
    drawMiddleLine(gc, color, size)
    canvas
  }

  def getEndSpanIcon(id: String, size: Int): Canvas = {
    val canvas = new Canvas(size, size)
    val gc = canvas.graphicsContext2D
    val color = getColor(id)
    drawBubble(gc, color, spanBubbleSize, size)
    drawEndLine(gc, color, size)
    canvas
  }

  def getOneElementSpanIcon(id: String, size: Int): Canvas = {
    val canvas = new Canvas(size, size)
    val gc = canvas.graphicsContext2D
    val color = getColor(id)
    drawBubble(gc, color, spanBubbleSize, size)
    canvas
  }

  private def drawBubble(gc: GraphicsContext, color: Color, size: Double, totalSize: Double): Unit = {
    gc.fill = color
    val center = totalSize / 2
    gc.fillRect(center - size/2, center - size/2, size, size)
  }

  private def drawBeginLine(gc: GraphicsContext, color: Color, size: Double): Unit = {
    gc.stroke = color
    gc.lineWidth = spanLineWidth
    val center = size / 2
    gc.strokeLine(center, center, center, size)
  }

  private def drawEndLine(gc: GraphicsContext, color: Color, size: Double): Unit = {
    gc.stroke = color
    gc.lineWidth = spanLineWidth
    val center = size / 2
    gc.strokeLine(center, 0, center, center)
  }

  private def drawMiddleLine(gc: GraphicsContext, color: Color, size: Double): Unit = {
    gc.stroke = color
    gc.lineWidth = spanLineWidth
    val center = size / 2
    gc.strokeLine(center, 0, center, size)
  }


  private def getColor(id: String) =
    darken(colorGenerator.getColor(id))

  private def darken(col: Color, maxBrightness: Double = 0.7): Color =
    if (col.brightness > maxBrightness) darken(col.darker)
    else col

}
