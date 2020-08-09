// Your profile name of the sonatype account. The default is the same with the organization value
sonatypeProfileName := "com.github.bertrand31"

// To sync with Maven central, you need to supply the following information:
publishMavenStyle := true

// Open-source license of your choice
licenses := Seq("Mozilla Public License 2.0" -> new URL("https://github.com/mozilla/openbadges-bakery/blob/master/LICENSE-MPL-2.0"))

// Where is the source code hosted: GitHub or GitLab?
import xerial.sbt.Sonatype._
sonatypeProjectHosting := Some(GitHubHosting("Bertrand31", "Pandore", "bertrandjun@gmail.com"))
