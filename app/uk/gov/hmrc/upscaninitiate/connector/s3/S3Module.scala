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

import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.upscaninitiate.connector.model.UploadFormGenerator
import uk.gov.hmrc.upscaninitiate.config.ServiceConfiguration
import org.apache.pekko.actor.ActorSystem
import javax.inject.{Inject, Provider}
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.regions.Region

class S3Module extends Module:
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[SecretsManagerClient].toProvider[SecretsManagerClientProvider],
      bind[UploadFormGenerator].toProvider[S3UploadFormGeneratorProvider]
    )

class SecretsManagerClientProvider @Inject()(
  configuration: ServiceConfiguration,
  actorSystem  : ActorSystem
) extends Provider[SecretsManagerClient]:
  override def get(): SecretsManagerClient =
    val containerCredentials = ContainerCredentialsProvider.builder().build()
    val client =
      SecretsManagerClient.builder()
        .credentialsProvider(containerCredentials)
        .region(Region.of(configuration.region))
        .build()
    actorSystem.registerOnTermination(client.close())
    client
