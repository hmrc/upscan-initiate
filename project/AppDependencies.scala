import sbt._

object AppDependencies {
  private val bootstrapVersion = "5.12.0"

  private val compile = Seq(
    "uk.gov.hmrc"        %% "bootstrap-backend-play-28" % bootstrapVersion,
    "com.amazonaws"      % "aws-java-sdk-s3"            % "1.11.921",
    "org.apache.commons" % "commons-lang3"              % "3.11"
  )

  private val test = Seq(
    "uk.gov.hmrc"          %% "bootstrap-test-play-28"  % bootstrapVersion % s"$Test,$IntegrationTest",
    "com.vladsch.flexmark" % "flexmark-all"             % "0.35.10"        % s"$Test,$IntegrationTest",
    "org.mockito"          %% "mockito-scala-scalatest" % "1.16.25"        % Test
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
