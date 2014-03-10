#!/bin/sh
exec scala -savecompiled "$0" "$@"
!#

import java.io.File

sealed trait EditCommand
case class AppendCommand(line: Int, lines: List[String]) extends EditCommand
case class InsertCommand(line: Int, lines: List[String]) extends EditCommand
case class DeleteCommand(start: Int, end: Int) extends EditCommand
case class ChangeCommand(start: Int, end: Int, lines: List[String]) extends EditCommand

val Append = """(\d+)a""".r
val Insert = """(\d+)i""".r
val Delete = """(\d+)(?:,(\d+))?d""".r
val Change = """(\d+)(?:,(\d+))?c""".r

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

sealed trait TutorialPart
case class EditScript(filename: String, script: List[EditCommand]) extends TutorialPart
case class ShellCommand(command: String) extends TutorialPart
case class Raw(content: String) extends TutorialPart

val EditScriptStart = """^<<(.*)>>=\s*$""".r
val Shell = """^@@\s*(.*)$""".r

def parseTutorial(tutorial: List[String]): List[TutorialPart] = {
  tutorial match {
    case Nil => Nil
    case EditScriptStart(filename) :: tail =>
      val (script, remaining) = tail.span(_ != "@")
      EditScript(filename, parseEditScript(script)) :: parseTutorial(remaining.drop(1))
    case Shell(command) :: tail =>
      ShellCommand(command) :: parseTutorial(tail)
    case other :: tail =>
      Raw(other) :: parseTutorial(tail)
  }
}

def readFile(file: File): List[String] = {
  import java.io._
  val is = new FileInputStream(file)
  try {
    val reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))
    var lines = List.empty[String]
    var line = reader.readLine()
    while (line != null) {
      lines = line :: lines
      line = reader.readLine()
    }
    lines.reverse
  } finally {
    is.close()
  }
}

def writeFile(file: File, content: String): Unit = {
  import java.io._
  val os = new FileOutputStream(file)
  try {
    val writer = new OutputStreamWriter(os)
    writer.write(content)
    writer.flush()
  } finally {
    os.close()
  }
}

def insertAt[A](list: List[A], index: Int, toInsert: List[A]): List[A] = {
  index match {
    case i if i <= 0 => toInsert ::: list
    case i if list.size < i =>
      throw new IndexOutOfBoundsException("Cannot insert at " + index + " in list of size " + list.size)
    case _ =>
      list.head :: insertAt(list.tail, index - 1, toInsert)
  }
}

def deleteAt[A](list: List[A], start: Int, length: Int): List[A] = {
  start match {
    case i if i <= 0 => list.drop(length)
    case i if list.size < i =>
      throw new IndexOutOfBoundsException("Cannot delete at " + start + " in list of size " + list.size)
    case _ =>
      list.head :: deleteAt(list.tail, start - 1, length)
  }
}

def changeAt[A](list: List[A], start: Int, length: Int, toReplace: List[A]): List[A] = {
  start match {
    case i if i <= 0 => toReplace ::: list.drop(length)
    case i if list.size < i =>
      throw new IndexOutOfBoundsException("Cannot change at " + start + " in list of size " + list.size)
    case _ =>
      list.head :: changeAt(list.tail, start - 1, length, toReplace)
  }
}

def runEditScript(editScript: EditScript, baseDir: File) = {
  println("Running edit script on file: " + editScript.filename)
  def runScript(commands: List[EditCommand], fileLines: List[String]): List[String]  = {
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
        runScript(commands.tail, newFileLines)
      case None => fileLines
    }
  }

  val file = new File(baseDir, editScript.filename)
  val lines = if (file.exists()) {
    readFile(file)
  } else Nil
  writeFile(file, runScript(editScript.script, lines).mkString("\n"))
}

def runShellCommand(command: ShellCommand, baseDir: File) = {
  import scala.sys.process.Process

  println("Running command: " + command.command)
  val rc = Process(command.command, baseDir).!

  if (rc != 0) throw new RuntimeException("Command " + command.command + " failed with return code " + rc)
}

def runTutorial(tutorial: List[TutorialPart], baseDir: File) = {
  tutorial.foreach {
    case edit: EditScript => runEditScript(edit, baseDir)
    case shell: ShellCommand => runShellCommand(shell, baseDir)
    case _ => ()
  }
}

def htmlEscape(text: String): String = {
  text.replaceAll("&", "&amp;")
    .replaceAll(">", "&gt;")
    .replaceAll("<", "&lt;")
}

def formatEditScript(out: StringBuilder, editScript: EditScript) = {
  val filename = editScript.filename.split("/").last

  def formatCommand(line: Int, code: List[String]) = {
    s"""<div class="codeSnippet">
  <div class="location">
    <a href="#code/${editScript.filename}:$line">$filename:$line</a>
  </div>
  <pre><code>${htmlEscape(code.mkString("\n"))}</code></pre>
</div>
"""
  }

  editScript.script.foreach {
    case InsertCommand(index, lines) => out.append(formatCommand(index, lines))
    case AppendCommand(index, lines) => out.append(formatCommand(index + 1, lines))
    case ChangeCommand(start, end, lines) => out.append(formatCommand(start, lines))
    case _ => ()
  }
}

def formatTutorial(tutorial: List[TutorialPart], source: File): String = {
  val out = new StringBuilder()
  out.append(s"<!-- DO NOT EDIT THIS FILE, IT IS GENERATED FROM ${source.getName} -->\n")
  tutorial.foreach {
    case edit: EditScript => formatEditScript(out, edit)
    case Raw(content) => out.append(content).append("\n")
    case _ => ()
  }
  out.toString()
}


var argv = args
def shift(by: Int = 1) = {
  argv = argv.drop(by)
}

def usage(error: String) = {
  def p(m: String) = System.err.println(m)
  p("Error: " + error)
  p("Usage:")
  p("  runTutorial.scala -i <file> [-g <out>] [-r <projectDir>] [-w <workDir>] [-c]")
  System.exit(1)
}

def nextArgument(desc: String): String = {
  shift()
  if (argv.isEmpty) {
    usage("Expected argument for option " + argv)
  }
  argv.head
}
var inFile: Option[File] = None
var generate: Option[File] = None
var workDir = new File("./target/tutorial")
var run: Option[File] = None
var clean = false


while (!argv.isEmpty) {
  argv.head match {
    case "-w" =>
      workDir = new File(nextArgument("-w"))
    case "-i" =>
      inFile = Some(new File(nextArgument("-i")))
    case "-g" =>
      generate = Some(new File(nextArgument("-g")))
    case "-r" =>
      run = Some(new File(nextArgument("-r")))
    case "-c" =>
      clean = true
  }
  shift()
}

if (inFile.isEmpty) {
  usage("Expected input file")
}

val tutorial = parseTutorial(readFile(inFile.get))

def delete(file: File): Unit = {
  if (file.exists()) {
    if (file.isDirectory) {
      file.listFiles().foreach(delete)
    }
    file.delete()
  }
}

def copy(from: File, to: File): Unit = {
  if (from.getCanonicalPath == workDir.getCanonicalPath || from.getName == "target") {
    // skip
  } else if (from.isDirectory) {
    to.mkdirs()
    from.listFiles().filterNot(_.getName.startsWith(".")).foreach { file =>
      copy(file, new File(to, file.getName))
    }
  } else {
    if (from.lastModified() > to.lastModified()) {
      writeFile(to, readFile(from).mkString("\n"))
    }
  }
}

run.foreach { runDir =>
  if (clean) {
    println("Cleaning working directory...")
    delete(workDir)
  }

  println("Copying run dir to working directory...")
  workDir.mkdirs()
  copy(runDir, workDir)

  println("Running tutorial...")
  runTutorial(tutorial, workDir)

  println("Tutorial successfully run!")
}

generate.foreach { outFile =>
  println("Generating tutorial file: " + outFile)
  writeFile(outFile, formatTutorial(tutorial, inFile.get))
  println("Tutorial generated!")
}




