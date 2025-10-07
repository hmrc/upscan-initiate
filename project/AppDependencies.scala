import sbt._

object AppDependencies {
  private val bootstrapVersion = "10.1.0"
  private val awsSdkVersion = "2.35.1"

  private val compile = Seq(
    "uk.gov.hmrc"            %% "bootstrap-backend-play-30" % bootstrapVersion,
    "software.amazon.awssdk" %  "s3"                        % awsSdkVersion,
    "software.amazon.awssdk" %  "secretsmanager"            % awsSdkVersion,
    "com.fasterxml.jackson.core" % "jackson-core"           % "2.20.0"
  )

  private val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"  % bootstrapVersion % Test
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
