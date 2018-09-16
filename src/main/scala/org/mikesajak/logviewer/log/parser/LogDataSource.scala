package org.mikesajak.logviewer.log.parser

import java.io.File

import scala.io.Source

trait LogDataSource {
  def lines: Iterator[String]
}

class SimpleFileLogDataSource(file: File) extends LogDataSource {
  def this(filename: String) = this(new File(filename))

  override def lines: Iterator[String] =
    Source.fromFile(file).getLines()
}
