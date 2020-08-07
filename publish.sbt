ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

pomExtra :=
  <licenses>
    <license>
      <name>Mozilla Public License Version 2.0</name>
      <url>https://www.mozilla.org/en-US/MPL/2.0/</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <developers>
    <developer>
      <id>Bertrand31</id>
      <name>Bertrand Junqua</name>
      <url>https://github.com/Bertrand31</url>
    </developer>
  </developers>
  <scm>
    <url>https://github.com/Bertrand/FSUtils</url>
    <connection>scm:git:https://github.com/Bertrand31/FSUtils</connection>
  </scm>
  <url>https://github.com/Bertrand31/FSUtils</url>

ThisBuild / publishMavenStyle := true

publishConfiguration := publishConfiguration.value.withOverwrite(true)
