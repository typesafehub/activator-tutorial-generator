package com.typesafe.sbt.activator.tutorial

import sbt._
import sbt.Keys._
import com.typesafe.activator.tutorial.{EditScript, Tutorial}

object ActivatorTutorialGeneratorKeys {
  val workingDirectory = SettingKey[File]("activatorTutorialWorkingDirectory")
  val tutorialScript = SettingKey[File]("activatorTutorialScript")
  val tutorialOutput = SettingKey[File]("activatorTutorialOutput")
  val parseTutorial = TaskKey[List[Tutorial.TutorialPart]]("activatorParseTutorial")
  val stageTutorial = TaskKey[File]("activatorStageTutorial")
  val runTutorial = InputKey[File]("activatorRunTutorial")
  val generateGitRepo = InputKey[File]("activatorGenerateGitRepo")
  val generateTutorial = TaskKey[File]("activatorGenerateTutorial")
}

object ActivatorTutorialGenerator extends Plugin {

  import ActivatorTutorialGeneratorKeys._

  override def projectSettings = Seq(
    workingDirectory := target.value / "tutorial-generator",
    tutorialScript := baseDirectory.value / "tutorial" / "index.html.script",
    tutorialOutput := baseDirectory.value / "tutorial" / "index.html",

    parseTutorial := {
      val lines = IO.readLines(tutorialScript.value)
      Tutorial.parseTutorial(lines)
    },

    stageTutorial := {
      val workDir: File = workingDirectory.value

      if (workDir.exists()) {
        streams.value.log.info("Cleaning working directory...")
        IO.delete(workDir)
      }

      streams.value.log.info("Copying project to working directory...")
      val base: File = baseDirectory.value
      stageToDir(base, workDir)

      workDir
    },

    runTutorial := {
      val args: Seq[String] = Def.spaceDelimited("<start-from>").parsed

      val workDir: File = stageTutorial.value

      var startFrom = args.headOption.map(_.toInt).getOrElse(0)
      def runCommand(command: String) = {
        if (startFrom == 0) {
          streams.value.log.info("Running command: " + command)
          val rc = Process(command, workDir).!
          if (rc != 0) throw new RuntimeException("Command " + command + " failed with return code " + rc)
        } else {
          startFrom -= 1
          streams.value.log.info("Skipping command: " + command)
        }
      }

      streams.value.log.info("Running tutorial...")

      Tutorial.runTutorial(parseTutorial.value, workDir, runCommand, m => streams.value.log.info(m))

      streams.value.log.success("Tutorial successfully run!")
      workDir
    },

    generateTutorial := {
      val output = Tutorial.formatTutorial(parseTutorial.value, tutorialScript.value)
      val dest: File = tutorialOutput.value
      IO.write(dest, output)
      dest
    },

    generateGitRepo := {

      val args: Seq[String] = Def.spaceDelimited("<git-repo>").parsed

      val gitRepo = args.headOption.map(new File(_)).getOrElse(stageTutorial.value)
      stageToDir(baseDirectory.value, gitRepo)

      def log(m: String) = streams.value.log.info(m)

      log("Initialising git repository")
      Process("git init", gitRepo).!!

      val tutorial: List[Tutorial.TutorialPart] = parseTutorial.value

      tutorial.collect {
        case edit: Tutorial.EditScriptPart => edit
      }.zipWithIndex.foreach {
        case (Tutorial.EditScriptPart(filename, script), idx) =>
          log("Running edit script on file: " + filename)
          val file = new File(gitRepo, filename)
          val lines = if (file.exists()) {
            IO.readLines(file)
          } else Nil
          IO.write(file, EditScript.runEditScript(script, lines, filename).mkString("\n"))

          Process("git" :: "add" :: "--all" :: Nil, gitRepo).!!
          Process("git" :: "commit" :: "-m" :: s"Running edit script number $idx" :: Nil, gitRepo).!!
          log("Committed: " + "git rev-parse HEAD".!!)
      }

      log("Converted tutorial script into git repository at: " + gitRepo.getCanonicalPath)
      gitRepo
    }
  )

  private def stageToDir(base: File, dir: File) = {
    val allFiles = base.***.filter(f => f.getName != "target" && f.getName != ".git")
    IO.copy(allFiles pair Path.rebase(base, dir))
  }
}
