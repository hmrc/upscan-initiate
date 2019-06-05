package config

import java.time.Duration

import javax.inject.Inject
import org.apache.commons.lang3.StringUtils.isNotBlank
import play.api.Configuration

trait ServiceConfiguration {

  def region: String
  def uploadProxyUrl: String
  def inboundBucketName: String
  def sessionToken: Option[String]
  def accessKeyId: String
  def secretAccessKey: String
  def fileExpirationPeriod: java.time.Duration
  def globalFileSizeLimit: Int
  def allowedCallbackProtocols: Seq[String]
  def allowedUserAgents: Seq[String]

}

class PlayBasedServiceConfiguration @Inject()(configuration: Configuration) extends ServiceConfiguration {

  override def region: String = getRequired(configuration.getString(_), "aws.s3.region")

  override def uploadProxyUrl: String = getRequired(configuration.getString(_), "uploadProxy.url")

  override def inboundBucketName: String = getRequired(configuration.getString(_), "aws.s3.bucket.inbound")

  override def fileExpirationPeriod: Duration =
    Duration.ofMillis(getRequired(configuration.getMilliseconds, "aws.s3.upload.link.validity.duration"))

  override def accessKeyId: String = getRequired(configuration.getString(_), "aws.accessKeyId")

  override def secretAccessKey: String = getRequired(configuration.getString(_), "aws.secretAccessKey")

  def getRequired[T](function: String => Option[T], key: String): T =
    function(key).getOrElse(throw new IllegalStateException(s"$key missing"))

  override def sessionToken: Option[String] = configuration.getString("aws.sessionToken")

  override def globalFileSizeLimit: Int = getRequired(configuration.getInt, "global.file.size.limit")

  override def allowedCallbackProtocols: Seq[String] =
    configuration
      .getString("callbackValidation.allowedProtocols")
      .map {
        _.split(",").toSeq
          .filter(isNotBlank)
      }
      .getOrElse(Nil)

  override def allowedUserAgents: Seq[String] =
    configuration
      .getString("userAgentFilter.allowedUserAgents")
      .map {
        _.split(",").toSeq
          .filter(isNotBlank)
      }
      .getOrElse(Nil)

}
