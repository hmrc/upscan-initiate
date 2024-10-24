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

import uk.gov.hmrc.upscaninitiate.connector.model.AwsCredentials

import java.nio.charset.Charset
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

trait PolicySigner:
  def signPolicy(
    credentials         : AwsCredentials,
    formattedSigningDate: String,
    regionName          : String,
    encodedPolicy       : String
  ): String

object PolicySigner extends PolicySigner:

  def signPolicy(
    credentials         : AwsCredentials,
    formattedSigningDate: String,
    regionName          : String,
    encodedPolicy       : String
  ): String =
    val signingKey      = newSigningKey(credentials, formattedSigningDate, regionName, "s3")
    val policySignature = sign(encodedPolicy, signingKey)
    toHex(policySignature)

  private def newSigningKey(
    credentials: AwsCredentials,
    dateStamp  : String,
    regionName : String,
    serviceName: String
  ) =
    val kSecret  = ("AWS4" + credentials.secretKey).getBytes(Charset.forName("UTF-8"))
    val kDate    = sign(dateStamp  , kSecret)
    val kRegion  = sign(regionName , kDate)
    val kService = sign(serviceName, kRegion)
    sign("aws4_request", kService)

  private def sign(stringData: String, key: Array[Byte]): Array[Byte] =
    val algorithm = "HmacSHA256"
    val mac       = Mac.getInstance(algorithm)
    mac.init(SecretKeySpec(key, algorithm))
    mac.doFinal(stringData.getBytes("UTF-8"))

  private def toHex(bytes: Array[Byte]): String =
    java.lang.String.format("%032x", new java.math.BigInteger(1, bytes))
