
val appVersion = "1.0.0-SNAPSHOT"

organization := "com.typesafe.activator"

name := "activator-tutorial"

version := "1.0.0-SNAPSHOT"

lazy val root = project
  .in(file("."))
  .settings(common: _*)
  .settings(noPublish: _*)
  .aggregate(core, plugin)

lazy val core = project
  .in(file("core"))
  .settings(common: _*)
  .settings(publishMaven: _*)
  .settings(
    name := "activator-tutorial-generator"
  )

lazy val plugin = project
  .in(file("plugin"))
  .dependsOn(core)
  .settings(common: _*)
  .settings(scriptedSettings: _*)
  .settings(publishSbtPlugin: _*)
  .settings(
    name := "sbt-activator-tutorial-generator",
    organization := "com.typesafe.sbt",
    sbtPlugin := true,
    scriptedLaunchOpts += ("-Dproject.version=" + version.value),
    scriptedLaunchOpts += "-XX:MaxPermSize=256m"
  )

def common: Seq[Setting[_]] = Seq(
  organization := "com.typesafe.activator",
  version := "1.0.0-SNAPSHOT",
  scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")
)

def publishMaven = Seq(
  publishTo := {
    if (isSnapshot.value) Some(typesafeRepo("snapshots"))
    else Some(typesafeRepo("releases"))
  }
)

def typesafeRepo(repo: String) = s"typesafe $repo" at s"http://private-repo.typesafe.com/typesafe/maven-$repo"

def publishSbtPlugin = Seq(
  publishMavenStyle := false,
  publishTo := {
    if (isSnapshot.value) Some(Classpaths.sbtPluginSnapshots)
    else Some(Classpaths.sbtPluginReleases)
  }
)

def noPublish = Seq(
  publish := {},
  publishLocal := {}
)

