package org.mikesajak.logviewer.log.parser

import org.mikesajak.logviewer.log.LogEntry

class LogParserIterator(private var inputData: Iterator[String],
                        rawLogEntryParser: LogEntryParser) extends Iterator[Option[LogEntry]] {
  private var nextEntryData: Seq[String] = _

  private var numLines = 0
  private var numEntries = 0

  findFirstEntry()

  override def hasNext: Boolean =
    nextEntryData.nonEmpty || inputData.nonEmpty

  override def next(): Option[LogEntry] = {
    val entry = rawLogEntryParser.parse(nextEntryData)
    moveToNextEntry()
    entry
  }

  private def findFirstEntry(): Unit = {
    val (entryData, rest) = findNextEntry()
    nextEntryData = entryData.toSeq
    inputData = rest
  }

  private def moveToNextEntry(): Unit = {
    if (inputData.nonEmpty) {
      val firstLine = inputData.next()
      val (entryData, rest) = findNextEntry()

      nextEntryData = Seq(firstLine) ++ entryData.toSeq
      inputData = rest
    } else nextEntryData = Seq.empty
  }


  private def findNextEntry() = {
    inputData.span(line => !rawLogEntryParser.matchBeginning(line))
  }
}

class LogParserIterator2(private var origIt: Iterator[String],
                         rawLogEntryParser: LogEntryParser) extends Iterator[Option[LogEntry]] {
  private val inputIt = new PeekIterator(origIt)

  private var nextEntryData: Seq[String] = _

  findFirstEntry()

  override def hasNext: Boolean =
    nextEntryData.nonEmpty || inputIt.nonEmpty

  override def next(): Option[LogEntry] = {
    val entry = rawLogEntryParser.parse(nextEntryData)
    moveToNextEntry()
    entry
  }

  private def findFirstEntry(): Unit = {
    val entryData = findNextEntry()
    nextEntryData = entryData
  }

  private def moveToNextEntry(): Unit = {
    if (inputIt.nonEmpty) {
      val firstLine = inputIt.next()
      val entryData = findNextEntry()

      nextEntryData = Seq(firstLine) ++ entryData
    } else nextEntryData = Seq.empty
  }

  private def findNextEntry() = {
    var found = false
    var entries = List[String]()
    while (inputIt.hasNext && !found) {
      found = rawLogEntryParser.matchBeginning(inputIt.peek)
      if (!found) {
        entries ::= inputIt.peek
        inputIt.next()
      }
    }
    entries
  }
}


class PeekIterator[A >: Null](backIt: Iterator[A]) extends Iterator[A] {
  private var curValue: A = _

  if (backIt.hasNext)
    curValue = backIt.next()

  def peek: A = curValue

  override def hasNext: Boolean = curValue != null

  override def next(): A = {
    val prev = curValue
    curValue = if (backIt.hasNext) backIt.next() else null
    prev
  }
}
//class NaiveRawEntryIterator(linesIt: Iterator[String], rawEntryParser: LogEntryParser) extends Iterator[LogEntry] {
//  private val logger = Logger[NaiveRawEntryIterator]
//
//  private var nextEntryBeginning: String = _
//
//  def init() {
//    val (startingData, firstLine) = goToStartOfNextEntry()
//    nextEntryBeginning = firstLine
//
//    if (startingData.nonEmpty)
//      logger.info(s"Throwing out ${startingData.size} lines of data.")
//  }
//
//  private def goToStartOfNextEntry(): (List[String], String) = {
//    var hasMatch = false
//    var entryData = List[String]()
//    var curBeginning: String = _
//    while(linesIt.hasNext && !hasMatch) {
//      val nextLine = linesIt.next()
//      hasMatch = rawEntryParser.matchBeginning(nextLine)
//      if (hasMatch)
//        curBeginning = nextLine
//      else entryData ::= nextLine
//    }
//
//    (entryData, curBeginning)
//  }
//
//  override def hasNext: Boolean = nextEntryBeginning != null
//
//  override def next(): LogEntry = {
//    val (collectedData, nextLine) = goToStartOfNextEntry()
//    val entryData = nextEntryBeginning :: collectedData
//    nextEntryBeginning = nextLine
//    rawEntryParser.parse(entryData)
//  }
//}