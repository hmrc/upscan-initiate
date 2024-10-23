import sbt._

object AppDependencies {
  private val bootstrapVersion = "9.5.0"

  private val compile = Seq(
    "uk.gov.hmrc"        %% "bootstrap-backend-play-30" % bootstrapVersion,
    "com.amazonaws"      %  "aws-java-sdk-s3"           % "1.12.606",
    "org.apache.commons" %  "commons-lang3"             % "3.12.0"
  )

  private val test = Seq(
    "uk.gov.hmrc"          %% "bootstrap-test-play-30"  % bootstrapVersion % Test
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
