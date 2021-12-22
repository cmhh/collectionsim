import sbt._

object Dependencies {
  lazy val akkaactor = "com.typesafe.akka" %% "akka-actor-typed" % "2.6.17"
  lazy val proj = "org.locationtech.proj4j" % "proj4j" % "1.1.4"
  lazy val upickle = "com.lihaoyi" %% "upickle" % "1.4.2"
  lazy val sqlite = "org.xerial" % "sqlite-jdbc" % "3.36.0.3"
  lazy val slf4japi = "org.slf4j" % "slf4j-api" % "1.7.32"
  lazy val slf4jlog4j = "org.slf4j" % "slf4j-log4j12" % "1.7.32"
  lazy val log4j = "org.apache.logging.log4j" % "log4j-core" % "2.17.0" 
  lazy val config = "com.typesafe" % "config" % "1.4.1"
  lazy val scallop = "org.rogach" %% "scallop" % "4.1.0"
}
