ThisBuild / organization := "com.bertrandjunqua"
ThisBuild / organizationName := "bertrandjunqua"
ThisBuild / organizationHomepage := Some(url("http://github.com/Bertrand31"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/Bertrand31/FSUtils"),
    "scm:git@github.com:Bertrand31/FSUtils.git"
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
ThisBuild / homepage := Some(url("https://github.com/Bertrand31/FSUtils"))

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true
