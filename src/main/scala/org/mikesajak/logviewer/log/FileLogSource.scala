package org.mikesajak.logviewer.log

case class FileLogSource(directory: String, file: String) extends LogSource
