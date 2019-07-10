package config

import java.time.Duration

import com.typesafe.config.ConfigException
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

  override def sessionToken: Option[String] = configuration.getString("aws.sessionToken")

  override def globalFileSizeLimit: Int = getRequired(configuration.getInt, "global.file.size.limit")

  override def allowedCallbackProtocols: Seq[String] =
    commaSeparatedList(configuration.getString("callbackValidation.allowedProtocols"))

  override def allowedUserAgents: Seq[String] =
    commaSeparatedList(configuration.getString("userAgentFilter.allowedUserAgents"))

  private def getRequired[T](read: String => Option[T], path: String): T =
    read(path).getOrElse(throw new ConfigException.Missing(path))

  private def commaSeparatedList(maybeString: Option[String]): List[String] =
    maybeString.fold(List.empty[String])(_.split(",").toList.filter(isNotBlank))

}
