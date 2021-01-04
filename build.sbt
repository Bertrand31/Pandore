scalaVersion := "2.13.4"

name := "Pandore"
organization := "com.github.bertrand31"
version := "0.1"

coverageEnabled := true
coverageMinimum := 80
coverageFailOnMinimum := true

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "2.3.1",
  "org.typelevel" %% "cats-effect" % "3.0.0-M5",
  "org.apache.commons" % "commons-compress" % "1.20",
  "org.scalatest" %% "scalatest" % "3.2.3" % Test,
)

scalacOptions ++= Seq(
  "-deprecation", // Warn about deprecated features
  "-encoding", "UTF-8", // Specify character encoding used by source files
  "-feature", // Emit warning and location for usages of features that should be imported explicitly
  "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
  "-language:higherKinds", // Allow higher-kinded types
  "-unchecked", // Enable additional warnings where generated code depends on assumptions
  "-Xlint:_", // Enable all available style warnings
  "-Ywarn-macros:after", // Only inspect expanded trees when generating unused symbol warnings
  "-Ywarn-unused:_", // Enables all unused warnings
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
)

scalacOptions in Test --= Seq(
  "-Xlint:_",
)

publishTo := sonatypePublishToBundle.value

credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

// Test suite settings
fork in Test := true
parallelExecution in Test := false
