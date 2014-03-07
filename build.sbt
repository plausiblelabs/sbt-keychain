import sbtrelease.ReleasePlugin._
import sbtrelease.ReleasePlugin.ReleaseKeys._
import sbtrelease.ReleaseStateTransformations._

name := """sbt-keychain"""

sbtPlugin := true

organization := "coop.plausible"

scalaVersion := "2.10.3"

homepage := Some(url("https://opensource.plausible.coop/src/projects/SBT/repos/sbt-keychain"))

licenses := Seq("The MIT License (MIT)" -> url("http://opensource.org/licenses/mit-license.php"))

libraryDependencies ++= Seq(
  "org.specs2"              %%  "specs2"                    % "2.3.8"   % "test"
)

scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-Xlint",
    "-Ywarn-dead-code",
    "-Xfatal-warnings",
    "-language:_",
    "-target:jvm-1.7",
    "-encoding", "UTF-8"
)

autoAPIMappings := true

publishTo := {
  val nexus = "https://opensource.plausible.coop/nexus/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "content/repositories/releases")
}

publishMavenStyle := true

pomIncludeRepository := { _ => false }

pomExtra :=
  <scm>
    <url>https://opensource.plausible.coop/stash/scm/sbt/sbt-keychain.git</url>
  </scm>
  <developers>
    <developer>
      <id>landonf</id>
      <name>Landon Fuller</name>
      <url>https://www.plausible.coop/about</url>
    </developer>
  </developers>
