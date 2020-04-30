import sbt._

object AppDependencies {
  import play.core.PlayVersion

  private val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-27"  % "2.4.0",
    "com.typesafe.play" %% "play-json"                  % PlayVersion.current,
    "com.amazonaws"      % "aws-java-sdk-s3"            % "1.11.769",
    "org.apache.commons" % "commons-lang3"              % "3.10"
  )

  private val test = Seq(
    "com.typesafe.play"      %% "play-test"                   % PlayVersion.current % s"$Test,$IntegrationTest",
    "org.scalatest"          %% "scalatest"                   % "3.1.1"             % s"$Test,$IntegrationTest",
    "org.scalatestplus"      %% "mockito-3-2"                 % "3.1.1.0"           % Test,
    "org.scalatestplus.play" %% "scalatestplus-play"          % "4.0.3"             % s"$Test,$IntegrationTest",
    "com.vladsch.flexmark"    % "flexmark-all"                % "0.35.10"           % s"$Test,$IntegrationTest"
  )

  def apply(): Seq[ModuleID] = compile ++ test
}