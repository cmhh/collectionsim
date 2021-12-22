import Dependencies._

ThisBuild / scalaVersion     := "2.13.7"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "org.cmhh"
ThisBuild / organizationName := "cmhh"

scalacOptions ++= Seq("-deprecation", "-feature")

lazy val root = (project in file("."))
  .settings(
    name := "collectionsim",

    libraryDependencies ++= Seq(
      akkaactor,
      proj,
      upickle,
      sqlite,
      slf4japi,
      slf4jlog4j,
      log4j,
      scallop
    ), 
    
    ThisBuild / assemblyMergeStrategy := {
      case n if n.contains("services") => MergeStrategy.concat
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case x => MergeStrategy.first
    },

    assembly / assemblyJarName := "collectionsim.jar"
  )
