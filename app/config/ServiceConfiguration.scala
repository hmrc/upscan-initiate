package config

import java.time.Duration
import javax.inject.Inject

import play.api.Configuration

trait ServiceConfiguration {

  def region: String
  def transientBucketName: String
  def sessionToken: Option[String]
  def accessKeyId: String
  def secretAccessKey: String
  def fileExpirationPeriod: java.time.Duration
}

class PlayBasedServiceConfiguration @Inject() (configuration : Configuration) extends ServiceConfiguration {

  override def region = getRequired(configuration.getString(_), "aws.s3.region")

  override def transientBucketName = getRequired(configuration.getString(_), "aws.s3.bucket.transient")

  override def fileExpirationPeriod = Duration.ofMillis(getRequired(configuration.getMilliseconds,"aws.s3.upload.link.validity.duration"))

  override def accessKeyId = getRequired(configuration.getString(_), "aws.s3.accessKeyId")

  override def secretAccessKey = getRequired(configuration.getString(_), "aws.s3.secretAccessKey")

  def getRequired[T](function : String => Option[T], key : String) = {
    function(key).getOrElse(throw new IllegalStateException(s"$key missing"))
  }

  override def sessionToken = configuration.getString("aws.s3.sessionToken")
}
