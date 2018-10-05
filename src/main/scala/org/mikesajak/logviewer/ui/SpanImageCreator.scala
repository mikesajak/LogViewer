package org.mikesajak.logviewer.ui

import org.mikesajak.logviewer.log.LogId
import org.mikesajak.logviewer.log.span.Span
import scalafx.scene.canvas.{Canvas, GraphicsContext}
import scalafx.scene.paint.Color

class SpanImageCreator {

  val colorGenerator = new SimpleColorGenerator

  val spanLineWidth = 2.0
  val spanWidth = 7.0
  val spanHeight = 16.0
  val spanBubbleSize = 6.0

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

  def drawSpans2(spans: Seq[Span], curSpanId: String, curEntryid: LogId, width: Double = 8, height: Double = 16): Canvas = {
    val canvas = new Canvas(spans.size * width, height)
//    val canvas = new Canvas(spans.size * spanWidth, spanHeight)
    val gc = canvas.graphicsContext2D
//    gc.lineWidth = spanLineWidth

    for ((span, pos) <- spans.zipWithIndex; if span != null) {
//      val spanColor = getColor(span.id)
//      gc.stroke = spanColor
//      gc.fill = spanColor
//      val spanPos = pos * spanWidth
      val spanPos = pos * width
//      val spanCenter = spanPos + spanWidth /2 - spanLineWidth /2
//      gc.strokeLine(spanCenter, 0, spanCenter, canvas.height.value)

      if (span.id == curSpanId) drawSpanIcon(gc, span, curEntryid, spanPos, width, height)
      else                      drawSpanLineIcon(gc, span.id, spanPos, width, height)
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

  def getSpanIcon(span: Span, id: LogId, width: Double = 8, height: Double = 16): Canvas = {
    val spanId = span.id
    if (span.logIds.size == 1)            getOneElementSpanIcon(spanId, width, height)
    else if (span.logIds.head == id)      getBeginSpanIcon(spanId, width, height)
    else if (span.logIds.last == id)      getEndSpanIcon(spanId, width, height)
    else if (span.contains(id.timestamp)) getMiddleSpanIcon(spanId, width, height)
    else null
  }

  def drawSpanIcon(gc: GraphicsContext, span: Span, id: LogId, posX: Double,
                   width: Double, height: Double ): Unit = {
    val spanId = span.id
    if (span.logIds.size == 1)            drawOneElementSpanIcon(gc, spanId, posX, width, height)
    else if (span.logIds.head == id)      drawBeginSpanIcon(gc, spanId, posX, width, height)
    else if (span.logIds.last == id)      drawEndSpanIcon(gc, spanId, posX, width, height)
    else if (span.contains(id.timestamp)) drawMiddleSpanIcon(gc, spanId, posX, width, height)
  }

  private def drawSpanLineIcon(gc: GraphicsContext, id: String, posX: Double, width: Double, height: Double): Unit = {
    val color = getColor(id)
    drawSpanLine(gc, color, posX, width, height)
  }

  def getBeginSpanIcon(id: String, width: Double, height: Double): Canvas = {
    val canvas = new Canvas(width, height)
    val gc = canvas.graphicsContext2D
    drawBeginSpanIcon(gc, id, 0, width, height)
    canvas
  }

  private def drawBeginSpanIcon(gc: GraphicsContext, id: String, posX: Double, width: Double, height: Double): Unit = {
    val color = getColor(id)
    drawBubble(gc, color, posX, spanBubbleSize, width, height)
    drawBeginLine(gc, color, posX, width, height)
  }

  def getMiddleSpanIcon(id: String, width: Double, height: Double): Canvas = {
    val canvas = new Canvas(width, height)
    val gc = canvas.graphicsContext2D
    drawMiddleSpanIcon(gc, id, 0, width, height)
    canvas
  }

  private def drawMiddleSpanIcon(gc: GraphicsContext, id: String, posX: Double, width: Double, height: Double): Unit = {
    val color = getColor(id)
    drawBubble(gc, color, posX, spanBubbleSize, width, height)
    drawMiddleLine(gc, color, posX, width, height)
  }

  def getEndSpanIcon(id: String, width: Double, height: Double): Canvas = {
    val canvas = new Canvas(width, height)
    val gc = canvas.graphicsContext2D
    drawEndSpanIcon(gc, id, 0, width, height)
    canvas
  }

  private def drawEndSpanIcon(gc: GraphicsContext, id: String, posX: Double, width: Double, height: Double): Unit = {
    val color = getColor(id)
    drawBubble(gc, color, posX, spanBubbleSize, width, height)
    drawEndLine(gc, color, posX, width, height)
  }

  def getOneElementSpanIcon(id: String, width: Double, height: Double): Canvas = {
    val canvas = new Canvas(width, height)
    val gc = canvas.graphicsContext2D
    drawOneElementSpanIcon(gc, id, 0, width, height)
    canvas
  }

  private def drawOneElementSpanIcon(gc: GraphicsContext, id: String, posX: Double, width: Double, height: Double): Unit = {
    val color = getColor(id)
    drawBubble(gc, color, posX, spanBubbleSize, width, height)
  }

  private def drawSpanLine(gc: GraphicsContext, color: Color, posX: Double, width: Double, height: Double): Unit = {
    gc.stroke = color
    gc.lineWidth = spanLineWidth
    val centerX = posX + width / 2
    gc.strokeLine(centerX, 0, centerX, height)
  }

  private def drawBubble(gc: GraphicsContext, color: Color, posX: Double, bubbleSize: Double, width: Double, height: Double): Unit = {
    gc.fill = color
    val centerX = width / 2
    val centerY = height / 2
    gc.fillRect(posX + centerX - bubbleSize /2, centerY - bubbleSize /2, bubbleSize, bubbleSize)
  }

  private def drawBeginLine(gc: GraphicsContext, color: Color, posX: Double, width: Double, height: Double): Unit = {
    gc.stroke = color
    gc.lineWidth = spanLineWidth
    val centerX = width / 2
    var centerY = height / 2
    gc.strokeLine(posX + centerX, centerY, posX + centerX, height)
  }

  private def drawEndLine(gc: GraphicsContext, color: Color, posX: Double, width: Double, height: Double): Unit = {
    gc.stroke = color
    gc.lineWidth = spanLineWidth
    val centerX = width / 2
    val centerY = height / 2
    gc.strokeLine(posX + centerX, 0, posX + centerX, centerY)
  }

  private def drawMiddleLine(gc: GraphicsContext, color: Color, posX: Double, width: Double, height: Double): Unit = {
    gc.stroke = color
    gc.lineWidth = spanLineWidth
    val centerX = posX + width / 2
    gc.strokeLine(centerX, 0, centerX, height)
  }


  private def getColor(id: String) =
    darken(colorGenerator.getColor(id))

  private def darken(col: Color, maxBrightness: Double = 0.7): Color =
    if (col.brightness > maxBrightness) darken(col.darker)
    else col

}
