import sbt._

object AppDependencies {
  private val bootstrapVersion = "10.7.0"
  private val awsSdkVersion = "2.44.7"

  private val compile = Seq(
    "uk.gov.hmrc"            %% "bootstrap-backend-play-30" % bootstrapVersion,
    "software.amazon.awssdk" %  "s3"                        % awsSdkVersion,
    "software.amazon.awssdk" %  "secretsmanager"            % awsSdkVersion
  )

  private val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"  % bootstrapVersion % Test
  )

  def apply(): Seq[ModuleID] = compile ++ test
}