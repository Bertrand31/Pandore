ThisBuild / organization := "io.github.bertrand31"
ThisBuild / organizationName := "bertrand31"
ThisBuild / organizationHomepage := Some(url("http://github.com/Bertrand31"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/Bertrand31/Pandore"),
    "scm:git@github.com:Bertrand31/Pandore.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id    = "Bertrand31",
    name  = "Bertrand Junqua",
    email = "bertrandjun@gmail.com",
    url   = url("http://github.com/Bertrand31")
  )
)

ThisBuild / description := "The functional files manipulation library Scala was missing"
ThisBuild / licenses := List("Mozilla Public License 2.0" -> new URL("https://github.com/mozilla/openbadges-bakery/blob/master/LICENSE-MPL-2.0"))
ThisBuild / homepage := Some(url("https://github.com/Bertrand31/Pandore"))

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }
publishTo := Some("Sonatype Snapshots Nexus" at "https://oss.sonatype.org/content/repositories/snapshots")
ThisBuild / publishMavenStyle := true
