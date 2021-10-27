scalaVersion := "3.0.0"

name := "Pandore"
organization := "com.github.bertrand31"
version := "0.2"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "2.6.1",
  "org.typelevel" %% "cats-effect" % "3.1.1",
  "org.apache.commons" % "commons-compress" % "1.20",
  "org.scalatest" %% "scalatest" % "3.2.9" % Test,
)

scalacOptions ++= Seq(
  "-deprecation", // Warn about deprecated features
  "-encoding", "UTF-8", // Specify character encoding used by source files
  "-feature", // Emit warning and location for usages of features that should be imported explicitly
  "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
  "-language:higherKinds", // Allow higher-kinded types
  "-unchecked", // Enable additional warnings where generated code depends on assumptions
)

Test / scalacOptions --= Seq(
  "-Xlint:_",
)

publishTo := sonatypePublishToBundle.value

credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

// Test suite settings
Test / fork := true
Test / parallelExecution := false
