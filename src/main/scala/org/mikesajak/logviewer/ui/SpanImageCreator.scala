package org.mikesajak.logviewer.ui

import org.mikesajak.logviewer.log.LogId
import org.mikesajak.logviewer.log.span.Span
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

  def drawSpans2(spans: Seq[Span], curSpanId: String, curEntryid: LogId): Canvas = {
    val canvas = new Canvas(spans.size * spanWidth, spanHeight)
    val gc = canvas.graphicsContext2D
    gc.lineWidth = spanLineWidth

    for ((span, pos) <- spans.zipWithIndex; if span != null) {
      val spanColor = getColor(span.id)
      gc.stroke = spanColor
      gc.fill = spanColor
      val spanPos = pos * spanWidth
      val spanCenter = spanPos + spanWidth /2 - spanLineWidth /2
      gc.strokeLine(spanCenter, 0, spanCenter, canvas.height.value)

      if (span.id == curSpanId)
        gc.fillRect(spanCenter - spanBubbleSize/2, spanHeight/2 - spanBubbleSize/2,
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

  def getSpanIcon(span: Span, id: LogId): Canvas = {
    val spanId = span.id
    if (span.logIds.size == 1)            getOneElementSpanIcon(spanId, 16)
    else if (span.logIds.head == id)      getBeginSpanIcon(spanId, 16)
    else if (span.logIds.last == id)      getEndSpanIcon(spanId, 16)
    else if (span.contains(id.timestamp)) getMiddleSpanIcon(spanId, 16)
    else null
  }

  def drawSpanIcon(span: Span, id: LogId, size: Double, gc: GraphicsContext): Unit = {
    val spanId = span.id
    if (span.logIds.size == 1)            drawOneElementSpanIcon(spanId, size, gc)
    else if (span.logIds.head == id)      drawBeginSpanIcon(spanId, size, gc)
    else if (span.logIds.last == id)      drawEndSpanIcon(spanId, size, gc)
    else if (span.contains(id.timestamp)) drawMiddleSpanIcon(spanId, size, gc)
  }

  private def drawSpanLineIcon(id: String, size: Double, gc: GraphicsContext): Unit = {
    val color = getColor(id)
    drawSpanLine(gc, color, size)
  }

  def getBeginSpanIcon(id: String, size: Int): Canvas = {
    val canvas = new Canvas(size, size)
    val gc = canvas.graphicsContext2D
    drawBeginSpanIcon(id, size, gc)
    canvas
  }

  private def drawBeginSpanIcon(id: String, size: Double, gc: GraphicsContext): Unit = {
    val color = getColor(id)
    drawBubble(gc, color, spanBubbleSize, size)
    drawBeginLine(gc, color, size)
  }

  def getMiddleSpanIcon(id: String, size: Double): Canvas = {
    val canvas = new Canvas(size, size)
    val gc = canvas.graphicsContext2D
    drawMiddleSpanIcon(id, size, gc)
    canvas
  }

  private def drawMiddleSpanIcon(id: String, size: Double, gc: GraphicsContext): Unit = {
    val color = getColor(id)
    drawBubble(gc, color, spanBubbleSize, size)
    drawMiddleLine(gc, color, size)
  }

  def getEndSpanIcon(id: String, size: Double): Canvas = {
    val canvas = new Canvas(size, size)
    val gc = canvas.graphicsContext2D
    drawEndSpanIcon(id, size, gc)
    canvas
  }

  private def drawEndSpanIcon(id: String, size: Double, gc: GraphicsContext): Unit = {
    val color = getColor(id)
    drawBubble(gc, color, spanBubbleSize, size)
    drawEndLine(gc, color, size)
  }

  def getOneElementSpanIcon(id: String, size: Double): Canvas = {
    val canvas = new Canvas(size, size)
    val gc = canvas.graphicsContext2D
    drawOneElementSpanIcon(id, size, gc)
    canvas
  }

  private def drawOneElementSpanIcon(id: String, size: Double, gc: GraphicsContext): Unit = {
    val color = getColor(id)
    drawBubble(gc, color, spanBubbleSize, size)
  }

  private def drawSpanLine(gc: GraphicsContext, color: Color, size: Double): Unit = {
    gc.stroke = color
    gc.lineWidth = spanLineWidth
    val center = size / 2
    gc.strokeLine(center, 0, center, size)
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
