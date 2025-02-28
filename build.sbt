import play.sbt.PlayImport.PlayKeys.playDefaultPort
import sbt.Keys._
import sbt._
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.3.5"
ThisBuild / scalacOptions += "-Wconf:msg=Flag.*repeatedly:s"

lazy val scoverageSettings = Seq(
  // Semicolon-separated list of regexs matching classes to exclude
  ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;.*AuthService.*;models/.data/..*;view.*",
  ScoverageKeys.coverageExcludedFiles :=
    ".*/frontendGlobal.*;.*/frontendAppConfig.*;.*/frontendWiring.*;.*/views/.*_template.*;.*/govuk_wrapper.*;.*/routes_routing.*;.*/BuildInfo.*",
  // Minimum is deliberately low to avoid failures initially - please increase as we add more coverage
  ScoverageKeys.coverageMinimumStmtTotal := 25,
  ScoverageKeys.coverageFailOnMinimum := false,
  ScoverageKeys.coverageHighlighting := true
)

lazy val microservice = Project("upscan-initiate", file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(scoverageSettings: _*)
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")
  .settings(playDefaultPort := 9571)
  .settings(libraryDependencies ++= AppDependencies())
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(Test / parallelExecution := false)

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies())
