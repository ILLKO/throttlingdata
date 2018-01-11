name := "throttlingdata"

version := "0.1"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.0.11",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.11",
  "com.typesafe.akka" %% "akka-stream" % "2.5.7",
  "com.typesafe.akka" %% "akka-actor" % "2.5.7",
  "org.scalatest" %% "scalatest" % "3.0.4" % Test,
  "junit" % "junit" % "4.10" % Test
)