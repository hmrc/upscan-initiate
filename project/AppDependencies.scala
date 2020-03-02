import sbt._

object AppDependencies {
  import play.core.PlayVersion

  private val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-play-26" % "1.4.0",
    "com.typesafe.play" %% "play-json"         % "2.6.14",
    "com.amazonaws"      % "aws-java-sdk-s3"   % "1.11.699",
    "org.apache.commons" % "commons-lang3"     % "3.9"
  )

  private val test = Seq(
    "uk.gov.hmrc"            %% "hmrctest"                    % "3.9.0-play-26"     % s"$Test,$IntegrationTest",
    "com.typesafe.play"      %% "play-test"                   % PlayVersion.current % s"$Test,$IntegrationTest",
    "org.scalatest"          %% "scalatest"                   % "3.0.8"             % s"$Test,$IntegrationTest",
    "org.scalatestplus.play" %% "scalatestplus-play"          % "3.1.2"             % s"$Test,$IntegrationTest",
    "org.pegdown"             % "pegdown"                     % "1.6.0"             % s"$Test,$IntegrationTest",
    "org.mockito"             % "mockito-core"                % "3.3.0"             % s"$Test,$IntegrationTest"
  )

  def apply(): Seq[ModuleID] = compile ++ test
}