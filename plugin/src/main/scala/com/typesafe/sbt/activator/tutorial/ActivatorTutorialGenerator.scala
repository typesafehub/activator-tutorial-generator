package com.typesafe.sbt.activator.tutorial

import sbt._
import sbt.Keys._
import com.typesafe.activator.tutorial.Tutorial

object ActivatorTutorialGeneratorKeys {
  val workingDirectory = SettingKey[File]("activatorTutorialWorkingDirectory")
  val tutorialScript = SettingKey[File]("activatorTutorialScript")
  val tutorialOutput = SettingKey[File]("activatorTutorialOutput")
  val parseTutorial = TaskKey[List[Tutorial.TutorialPart]]("activatorParseTutorial")
  val runTutorial = TaskKey[File]("activatorRunTutorial")
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

    runTutorial := {
      val workDir: File = workingDirectory.value

      if (workDir.exists()) {
        streams.value.log.info("Cleaning working directory...")
        IO.delete(workDir)
      }

      streams.value.log.info("Copying project to working directory...")
      val base: File = baseDirectory.value
      val allFiles = base.***.filter(f => f.getName != "target" && !f.getName.startsWith("."))
      IO.copy(allFiles x Path.rebase(base, workDir))

      streams.value.log.info("Running tutorial...")
      Tutorial.runTutorial(parseTutorial.value, workDir, m => streams.value.log.info(m))

      streams.value.log.success("Tutorial successfully run!")
      workDir
    },

    generateTutorial := {
      val output = Tutorial.formatTutorial(parseTutorial.value, tutorialScript.value)
      val dest: File = tutorialOutput.value
      IO.write(dest, output)
      dest
    }
  )
}
