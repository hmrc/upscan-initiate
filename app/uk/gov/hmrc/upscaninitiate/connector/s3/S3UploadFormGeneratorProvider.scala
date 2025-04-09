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

package uk.gov.hmrc.upscaninitiate.connector.s3

import uk.gov.hmrc.upscaninitiate.config.ServiceConfiguration
import uk.gov.hmrc.upscaninitiate.connector.model.{AwsCredentials, UploadFormGenerator}

import java.time.{Clock, Instant}
import javax.inject.{Inject, Provider, Singleton}
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.*
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import play.api.Logger

@Singleton
class S3UploadFormGeneratorProvider @Inject()(
  configuration       : ServiceConfiguration,
  clock               : Clock,
  secretsManagerClient: SecretsManagerClient
) extends Provider[UploadFormGenerator]:
  import configuration._

  private val logger = Logger(getClass)

  override def get() =
    val credentials = sessionToken match
      case Some(_) =>
        AwsCredentials(accessKeyId, secretAccessKey, sessionToken)
      case None =>
        try
          val request = GetSecretValueRequest.builder()
            .secretId(secretArn)
            .build()

          val secretResponse = secretsManagerClient.getSecretValue(request)
          val creds = Json.parse(secretResponse.secretString()).as[RetrievedCredentials](RetrievedCredentials.reads)

          AwsCredentials(creds.accessKeyId, creds.secretAccessKey, sessionToken = None)
        catch
          case e: Exception =>
            logger.error(s"Failed to retrieve AWS credentials from Secrets Manager: ${e.getMessage}", e)
            throw e

    S3UploadFormGenerator(
      credentials,
      regionName  = region,
      currentTime = () => Instant.now(clock)
    )

final case class RetrievedCredentials(accessKeyId: String, secretAccessKey: String):
  override def toString(): String = s"RetrievedCredentials($accessKeyId, REDACTED)"

object RetrievedCredentials:
  val reads: Reads[RetrievedCredentials] =
    ( (__ \ "accessKeyId"    ).read[String]
    ~ (__ \ "secretAccessKey").read[String]
    )(apply)
