import sbt._

object AppDependencies {
  private val bootstrapVersion = "9.11.0"

  private val compile = Seq(
    "uk.gov.hmrc"            %% "bootstrap-backend-play-30" % bootstrapVersion,
    "software.amazon.awssdk" %  "s3"                        % "2.30.30",
    "software.amazon.awssdk" %  "secretsmanager"            % "2.30.30"
  )

  private val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"  % bootstrapVersion % Test
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
