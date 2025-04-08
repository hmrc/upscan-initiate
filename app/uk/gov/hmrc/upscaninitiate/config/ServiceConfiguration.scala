/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.upscaninitiate.config

import play.api.Configuration

import javax.inject.Inject
import scala.concurrent.duration.FiniteDuration

trait ServiceConfiguration:
  def region                  : String
  def uploadProxyUrl          : String
  def inboundBucketName       : String
  def sessionToken            : Option[String]
  def accessKeyId             : String
  def secretAccessKey         : String
  def secretArn               : String
  def fileExpirationPeriod    : FiniteDuration
  def maxFileSizeLimit        : Long
  def defaultMaxFileSize      : Long
  def allowedCallbackProtocols: Seq[String]

class PlayBasedServiceConfiguration @Inject()(
  configuration: Configuration
) extends ServiceConfiguration:

  override val region: String =
    configuration.get[String]("aws.s3.region")

  override val uploadProxyUrl: String =
    configuration.get[String]("uploadProxy.url")

  override val inboundBucketName: String =
    configuration.get[String]("aws.s3.bucket.inbound")

  override val fileExpirationPeriod: FiniteDuration =
    configuration.get[FiniteDuration]("aws.s3.upload.link.validity.duration")

  override val accessKeyId: String =
    configuration.get[String]("aws.accessKeyId")

  override val secretAccessKey: String =
    configuration.get[String]("aws.secretAccessKey")

  override val secretArn: String =
    configuration.get[String]("aws.secretArn")

  override val sessionToken: Option[String] =
    configuration.getOptional[String]("aws.sessionToken")

  override val maxFileSizeLimit: Long =
    configuration.get[Long]("maxFileSize.limit")

  override val defaultMaxFileSize: Long =
    configuration.get[Long]("maxFileSize.default")

  override val allowedCallbackProtocols: Seq[String] =
    configuration
      .get[String]("callbackValidation.allowedProtocols")
      .split(",").toList.map(_.trim).filter(_.nonEmpty)
