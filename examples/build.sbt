// Chisel Handout 範例專案 —— 基準版本 Chisel 7.13.0 + Scala 2.13 + ChiselSim
ThisBuild / scalaVersion := "2.13.18"
ThisBuild / version      := "0.1.0"
ThisBuild / organization := "handout"

val chiselVersion = "7.13.0"

lazy val root = (project in file("."))
  .settings(
    name := "chisel-handout-examples",
    libraryDependencies ++= Seq(
      "org.chipsalliance" %% "chisel"    % chiselVersion,
      "org.scalatest"     %% "scalatest" % "3.2.19" % Test,
    ),
    addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
    ),
  )
