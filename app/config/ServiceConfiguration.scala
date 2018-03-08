package config

import java.time.Duration
import javax.inject.Inject

import play.api.Configuration

trait ServiceConfiguration {

  def useInstanceProfileCredentials: Boolean
  def region: String
  def inboundBucketName: String
  def sessionToken: Option[String]
  def accessKeyId: String
  def secretAccessKey: String
  def fileExpirationPeriod: java.time.Duration
  def globalFileSizeLimit: Int
}

class PlayBasedServiceConfiguration @Inject()(configuration: Configuration) extends ServiceConfiguration {

  override def useInstanceProfileCredentials =
    configuration.getBoolean("aws.s3.use.instance.profile.credentials").getOrElse(false)

  override def region = getRequired(configuration.getString(_), "aws.s3.region")

  override def inboundBucketName = getRequired(configuration.getString(_), "aws.s3.bucket.inbound")

  override def fileExpirationPeriod =
    Duration.ofMillis(getRequired(configuration.getMilliseconds, "aws.s3.upload.link.validity.duration"))

  override def accessKeyId = getRequired(configuration.getString(_), "aws.s3.accessKeyId")

  override def secretAccessKey = getRequired(configuration.getString(_), "aws.s3.secretAccessKey")

  def getRequired[T](function: String => Option[T], key: String) =
    function(key).getOrElse(throw new IllegalStateException(s"$key missing"))

  override def sessionToken = configuration.getString("aws.s3.sessionToken")

  override def globalFileSizeLimit = getRequired(configuration.getInt(_), "global.file.size.limit")
}
