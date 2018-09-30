package org.mikesajak.logviewer.ui

import java.time.LocalDateTime

import com.typesafe.scalalogging.Logger
import groovy.lang.GroovyShell
import org.mikesajak.logviewer.log._
import org.mikesajak.logviewer.ui.controller.LogRow
import org.mikesajak.logviewer.util.ResourceManager

import scala.util.{Success, Try}

object FilterExpressionParser {
  type FilterPredicate = LogRow => Boolean
}

class FilterExpressionParser(resourceMgr: ResourceManager) {
  import FilterExpressionParser._

  private val logger = Logger[FilterExpressionParser]

  def buildPredicate(expressionString: String, logLevelFilterSelection: Map[LogLevel, Boolean]): Try[Option[FilterPredicate]] = {
    val toggleButtonPredicate =
      if (logLevelFilterSelection.forall(_._2 == false)) None
      else logLevelPred(logLevelFilterSelection)

    val expressionPredicate = buildFilterExprPredicate2(expressionString)

    expressionPredicate.map { exprPred =>
      Seq(toggleButtonPredicate, exprPred)
      .flatten
      .reduceLeftOption((resultPred, curPred) => (logRow: LogRow) => resultPred(logRow) && curPred(logRow))
    }
  }

  private def buildFilterExprPredicate2(filterExpression: String): Try[Option[FilterPredicate]] = {
    if (filterExpression.nonEmpty) {
      Try {
        val expressionPredicateTest = parseFilter(filterExpression, suppressExceptions = false)
        // test predicate on some basic data
        testPredicate(expressionPredicateTest)

        parseFilter(filterExpression, suppressExceptions = true)
      }.map(Some(_))
    } else Success(None)
  }

  private def exceptionMessage(ex: Throwable): String = {
    if (ex.getCause == null || ex.getCause == ex) ex.getLocalizedMessage
    else ex.getLocalizedMessage + "\n" + exceptionMessage(ex.getCause)
  }

  private def testPredicate(pred: FilterPredicate) = {
    val time = Timestamp(LocalDateTime.now())
    val logEntry = new SimpleLogEntry(LogId(LogSource("testDir", "testFile"), time, 0),
                                       LogLevel.Info, "thread-1", "session-1234","reqest-1234", "testuser",
                                       "Message 12341234123412341234123412341342", 0)
    val logRow = new LogRow(0, logEntry, resourceMgr)

    pred(logRow)
  }

  private def logLevelPred(selectedLogLevels: Map[LogLevel, Boolean]): Option[FilterPredicate] = {
    selectedLogLevels.flatMap { case (level, selected) => logLevelPred(level, selected) }
    .reduceLeftOption((p1, p2) => (logRow: LogRow) => p1(logRow) || p2(logRow))
  }

  private def logLevelPred(level: LogLevel, selected: Boolean): Option[FilterPredicate] =
    if (selected) Some(logRow => logRow.logEntry.level == level)
    else None

  class InvalidFilterExpression(message: String) extends Exception(message)
  class FilterExpressionError(message: String, cause: Exception) extends Exception(message, cause)

  private def parseFilter(text: String, suppressExceptions: Boolean): FilterPredicate = {
    val shell = new GroovyShell()
    val script = shell.parse(text)
    logRow: LogRow => {
      shell.setVariable("entry", logRow.logEntry)
      try {
        val result: AnyRef = script.run()
        //noinspection ComparingUnrelatedTypes
        if (!result.isInstanceOf[Boolean])
          handleException(text, suppressExceptions, new InvalidFilterExpression(s"Filter expression must return Boolean value.\nExpression:\n$text"))
        result.asInstanceOf[Boolean]
      } catch {
        case e: Exception =>
          handleException(text, suppressExceptions, new FilterExpressionError(s"Exception occurred during evaluation of filter expression.\nExpression:\n$text\n${e.getLocalizedMessage}", e))
      }
    }

  }

  private def handleException(expr: String, suppress: Boolean, ex: Exception): Boolean = {
    if (suppress) {
      logger.warn(s"An error occurred during preparing filter expression:\n$expr", ex)
      true
    } else throw ex
  }
}
