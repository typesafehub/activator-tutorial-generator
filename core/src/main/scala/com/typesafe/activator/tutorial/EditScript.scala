package com.typesafe.activator.tutorial

import java.io.{FileWriter, File}
import scala.io.Source

/**
 * Edit script handler
 */
object EditScript {

  /**
   * A command in an edit script
   */
  sealed trait EditCommand
  case class AppendCommand(line: Int, lines: List[String]) extends EditCommand
  case class InsertCommand(line: Int, lines: List[String]) extends EditCommand
  case class DeleteCommand(start: Int, end: Int) extends EditCommand
  case class ChangeCommand(start: Int, end: Int, lines: List[String]) extends EditCommand

  private val Append = """(\d+)a""".r
  private val Insert = """(\d+)i""".r
  private val Delete = """(\d+)(?:,(\d+))?d""".r
  private val Change = """(\d+)(?:,(\d+))?c""".r

  /**
   * Parse an edit script.
   *
   * @param script The script to parse.
   * @return The list of edit commands.
   */
  def parseEditScript(script: List[String]): List[EditCommand] = {
    script match {
      case Nil => Nil
      case command :: tail =>
        val (parsed, rest) = command match {
          case Append(line) =>
            val (code, remaining) = tail.span(_ != ".")
            (AppendCommand(line.toInt, code), remaining.drop(1))
          case Insert(line) =>
            val (code, remaining) = tail.span(_ != ".")
            (InsertCommand(line.toInt, code), remaining.drop(1))
          case Change(start, end) =>
            val (code, remaining) = tail.span(_ != ".")
            (ChangeCommand(start.toInt, Option(end).getOrElse(start).toInt, code), remaining.drop(1))
          case Delete(start, end) =>
            (DeleteCommand(start.toInt, Option(end).getOrElse(start).toInt), tail)
          case _ =>
            throw new RuntimeException("Unparsable edit script command: '" + command + "'")
        }
        parsed :: parseEditScript(rest)
    }
  }

  private def insertAt[A](list: List[A], index: Int, toInsert: List[A]): List[A] = {
    index match {
      case i if i <= 0 => toInsert ::: list
      case i if list.size < i =>
        throw new IndexOutOfBoundsException("Cannot insert at " + index + " in list of size " + list.size)
      case _ =>
        list.head :: insertAt(list.tail, index - 1, toInsert)
    }
  }

  private def deleteAt[A](list: List[A], start: Int, length: Int): List[A] = {
    start match {
      case i if i <= 0 => list.drop(length)
      case i if list.size < i =>
        throw new IndexOutOfBoundsException("Cannot delete at " + start + " in list of size " + list.size)
      case _ =>
        list.head :: deleteAt(list.tail, start - 1, length)
    }
  }

  private def changeAt[A](list: List[A], start: Int, length: Int, toReplace: List[A]): List[A] = {
    start match {
      case i if i <= 0 => toReplace ::: list.drop(length)
      case i if list.size < i =>
        throw new IndexOutOfBoundsException("Cannot change at " + start + " in list of size " + list.size)
      case _ =>
        list.head :: changeAt(list.tail, start - 1, length, toReplace)
    }
  }

  /**
   * Run an edit script
   *
   * @param commands The script to run
   * @param fileLines The lines of the file
   */
  def runEditScript(commands: List[EditCommand], fileLines: List[String]): List[String] = {
    commands.headOption match {
      case Some(command) =>
        val newFileLines = command match {
          case AppendCommand(line, toAppend) =>
            insertAt(fileLines, line, toAppend)
          case InsertCommand(line, toInsert) =>
            insertAt(fileLines, line - 1, toInsert)
          case DeleteCommand(start, end) =>
            deleteAt(fileLines, start - 1, end - start + 1)
          case ChangeCommand(start, end, toReplace) =>
            changeAt(fileLines, start - 1, end - start + 1, toReplace)
        }
        runEditScript(commands.tail, newFileLines)
      case None => fileLines
    }
  }

  private def htmlEscape(text: String): String = {
    text.replaceAll("&", "&amp;")
      .replaceAll(">", "&gt;")
      .replaceAll("<", "&lt;")
  }

  /**
   * Render the given edit script as HTML
   */
  def formatEditScript(out: StringBuilder, filename: String, commands: List[EditCommand]) = {
    val name = filename.split("/").last

    def formatCommand(line: Int, code: List[String]) = {
      s"""<div class="codeSnippet">
  <div class="location">
    <a href="#code/$filename:$line">$name:$line</a>
  </div>
  <pre><code>${htmlEscape(code.mkString("\n"))}</code></pre>
</div>
"""
    }

    commands.foreach {
      case InsertCommand(index, lines) => out.append(formatCommand(index, lines))
      case AppendCommand(index, lines) => out.append(formatCommand(index + 1, lines))
      case ChangeCommand(start, end, lines) => out.append(formatCommand(start, lines))
      case _ => ()
    }
  }

}
