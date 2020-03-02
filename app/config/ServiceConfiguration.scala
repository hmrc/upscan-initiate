/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
}

class PlayBasedServiceConfiguration @Inject()(configuration: Configuration) extends ServiceConfiguration {

  override def region: String = getRequired(configuration.getOptional[String](_), "aws.s3.region")

  override def uploadProxyUrl: String = getRequired(configuration.getOptional[String](_), "uploadProxy.url")

  override def inboundBucketName: String = getRequired(configuration.getOptional[String](_), "aws.s3.bucket.inbound")

  override def fileExpirationPeriod: Duration = {
    val readAsMillis: String => Option[Long] = configuration.getOptional[scala.concurrent.duration.Duration](_).map(_.toMillis)
    Duration.ofMillis(getRequired(readAsMillis, "aws.s3.upload.link.validity.duration"))
  }

  override def accessKeyId: String = getRequired(configuration.getOptional[String](_), "aws.accessKeyId")

  override def secretAccessKey: String = getRequired(configuration.getOptional[String](_), "aws.secretAccessKey")

  override def sessionToken: Option[String] = configuration.getOptional[String]("aws.sessionToken")

  override def globalFileSizeLimit: Int = getRequired(configuration.getOptional[Int](_), "global.file.size.limit")

  override def allowedCallbackProtocols: Seq[String] =
    commaSeparatedList(configuration.getOptional[String]("callbackValidation.allowedProtocols"))

  private def getRequired[T](read: String => Option[T], path: String): T =
    read(path).getOrElse(throw new ConfigException.Missing(path))

  private def commaSeparatedList(maybeString: Option[String]): List[String] =
    maybeString.fold(List.empty[String])(_.split(",").toList.filter(isNotBlank))

}
