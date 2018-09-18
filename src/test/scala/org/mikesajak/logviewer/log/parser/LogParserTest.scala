package org.mikesajak.logviewer.log.parser

import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source

class LogParserTest extends FlatSpec with Matchers {

  def dataSource(log: String) =
    new LogDataSource {
      override def lines: Iterator[String] = Source.fromString(log).getLines()
    }

  "Log parser" should "parse simple oneliner log entries" in {
    val parser = new LogParser

    val log =
      """2015-10-23 16:12:21,930 DEBUG [test1234] [1@abc.def.ghi.com,] one two three four five six seven eight night ten
        |2015-10-23 16:12:21,820 DEBUG [test4321-1] [1@def.ghi.com] one two three four five six seven eight night ten
        |2015-10-23 16:12:21,930 DEBUG [test2314-1] [1@aoeu.sth.com] one two three four five six seven eight night ten
        |2015-10-23 16:12:46,681 DEBUG [Qaka-1] [2@abc] one two three four five six seven eight night ten
        |2015-10-23 16:12:46,705 DEBUG [Qaka-1] [2@def] one two three four five six seven eight night ten
        |2015-10-23 16:13:45,762 DEBUG [Qbaba-3] [2@aaa.bbb.ccc] one two three four five six seven eight night ten
        |2015-10-23 16:13:45,770 DEBUG [Qbaba-3] [2@ddd.eee.fff] one two three four five six seven eight night ten
      """.stripMargin

    val entries = parser.parse(dataSource(log),
                               new SimpleLogEntryParser(new SimpleLogIdGenerator("testDir", "testFile")))
        .toIndexedSeq

    entries.size shouldBe 7
  }

  it should "parse mixed oneliner and multiline log entries" in {
    val parser = new LogParser

    val log =
      """2015-10-23 16:12:21,930 DEBUG [test1234] [1@abc.def.ghi.com,] one two three four five six seven eight night ten
        |2015-10-23 16:12:21,820 DEBUG [test4321-1] [1@def.ghi.com] one two three four five six seven eight night ten
        |2015-10-23 16:12:21,930 DEBUG [test2314-1] [1@aoeu.sth.com] one two three four five six seven eight night ten
        |one
        |two
        |three
        |2015-10-23 16:12:46,681 DEBUG [Qaka-1] [2@abc] one two three four five six seven eight night ten
        |2015-10-23 16:12:46,705 DEBUG [Qaka-1] [2@def] one two three four five six seven eight night ten
        |four
        |five
        |six
        |seven
        |2015-10-23 16:13:45,762 DEBUG [Qbaba-3] [2@aaa.bbb.ccc] one two three four five six seven eight night ten
        |2015-10-23 16:13:45,770 DEBUG [Qbaba-3] [2@ddd.eee.fff] one two three four five six seven eight night ten
      """.stripMargin

    val entries = parser.parse(dataSource(log),
                               new SimpleLogEntryParser(new SimpleLogIdGenerator("testDir", "testFile")))
        .toIndexedSeq

    entries.size shouldBe 7
  }

  it should "parse optional fields" in {
    val parser = new LogParser

    val log =
      """2015-10-23 16:12:21,930 DEBUG [test1234] [1@abc.def.ghi.com,requestid-1234,by,user] one two three four five six seven eight night ten
        |2015-10-23 16:12:21,820 DEBUG [test4321-1] [] one two three four five six seven eight night ten
      """.stripMargin

    val entries = parser.parse(dataSource(log),
      new SimpleLogEntryParser(new SimpleLogIdGenerator("testDir", "testFile")))
                  .toIndexedSeq

    entries.size shouldBe 2
  }
}

