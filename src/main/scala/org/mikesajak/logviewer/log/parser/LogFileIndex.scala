package org.mikesajak.logviewer.log.parser

import java.io.{BufferedReader, FileReader, Reader}

import scala.io.Source

object LogFileIndexParser {
  def main(args: Array[String]): Unit = {
    val file = "../LogViewer.bak/test/aircrews.log"
    val parser = new LogFileIndexParser()
    val lines = parser.scanLines(new FileReader(file))
                .toList

    val lines2 = parser.scanLines2(lines.iterator).toList

    lines2.foreach(println)
  }
}

class LogFileIndexParser {
  def parse(filename: String) = {
    val linePositions = scanLines(Source.fromFile(filename).getLines).toSeq
  }

  def scanLines2(lines: Iterator[(Int, String)]): Iterator[(Int, String)] = {
    (Iterator((0, "")) ++ lines ++ Iterator((0, "")))
      .sliding(2)
      .map(l => l.head._1 -> l.last._2)
      .scanLeft((0, ""))((acc, elem) => acc._1 + elem._1 -> elem._2)
      .drop(1)
  }

  def scanLines(lines: Iterator[String]): Iterator[(Int, String)] = {
    (Iterator("") ++ lines ++ Iterator(""))
      .map(l => l.length -> l)
      .sliding(2)
      .map(l => l.head._1 -> l.last._2)
      .scanLeft((0, ""))((acc, elem) => acc._1 + elem._1 + 1 -> elem._2)
  }

  def scanLines(reader: Reader) = {
    new LineReaderIterator(reader)
  }

  class LineReaderIterator(reader: Reader) extends Iterator[(Int, String)] {
    private val br = new BufferedReader(reader)

    override def hasNext: Boolean = {
      br.mark(10)
      val char = br.read()
      if (char >= 0) {
        br.reset()
        true
      } else false
    }

    override def next(): (Int, String) = {
      readLine(br)
    }

    private def readLine(reader: Reader) = {
      val sb = new StringBuilder
      var end = false
      var charsRead = -1
      do {
        reader.mark(10000)
        end = reader.read() match {
          case '\n' =>
            charsRead += 1
            true
          case '\r' =>
            reader.mark(10)
            reader.read() match {
              case '\n' =>
                charsRead += 1
                true
              case _ =>
                reader.reset()
                true
            }
          case char@_ =>
            if (char >= 0) {
              charsRead += 1
              sb.append(char.toChar)
              false
            } else true
        }
      } while (!end)

      if (sb.nonEmpty) (charsRead, sb.toString)
      else (-1, null)
    }
  }

  private def skipLineEnd(reader: Reader) = {
    var count = 0
    var prevChar = -1
    var end = false
    do {
      reader.mark(10)
      val char = reader.read
      if (prevChar != '\r' && char == '\n' || char == '\r' || prevChar == '\r' && char == '\n')
        count += 1

      if (char != '\r' && char != '\n') {
        reader.reset()
        end = true
      } else prevChar = char
    } while (!end)

    count
  }
}