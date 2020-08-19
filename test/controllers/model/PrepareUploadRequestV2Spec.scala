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

package controllers.model

import org.scalatest.EitherValues
import play.api.libs.json.Json
import test.UnitSpec


class PrepareUploadRequestV2Spec extends UnitSpec with EitherValues {

  import PrepareUploadRequestV2Spec._

  "A v2 upload request" when {
    "specifying an upload success redirect URL" should {
      "parse as a valid request containing a success redirect URL" in {
        val request = s"""|{"callbackUrl":"$CallbackUrl",
                          | "successRedirect":"$SuccessRedirectUrl",
                          | "errorRedirect":"$ErrorRedirectUrl"}""".stripMargin
        val parseResult = Json.parse(request).validate(PrepareUploadRequestV2.reads(MaxFileSize))

        parseResult.asEither.map(_.successRedirect).right.value should contain (SuccessRedirectUrl)
      }

      "generate upload settings that contain the success redirect URL" in {
        val uploadSettings = aV2RequestWith(successRedirectUrl = Some(SuccessRedirectUrl)).toUploadSettings(UploadUrl)

        uploadSettings.successRedirect should contain (SuccessRedirectUrl)
      }
    }

    "omitting an upload success redirect URL" should {
      "parse as a valid request without a success redirect URL" in {
        val request = s"""|{"callbackUrl":"$CallbackUrl",
                          | "errorRedirect":"$ErrorRedirectUrl"}""".stripMargin
        val parseResult = Json.parse(request).validate(PrepareUploadRequestV2.reads(MaxFileSize))

        parseResult.asEither.map(_.successRedirect).right.value shouldBe empty
      }

      "generate upload settings without a success redirect URL" in {
        val uploadSettings = aV2RequestWith(successRedirectUrl = None).toUploadSettings(UploadUrl)

        uploadSettings.successRedirect shouldBe empty
      }
    }
  }
}

private object PrepareUploadRequestV2Spec {
  val UploadUrl = "https://xxxx/upscan-upload-proxy/bucketName"
  val CallbackUrl = "https://myservice.com/callback"
  val SuccessRedirectUrl = "https://myservice.com/nextPage"
  val ErrorRedirectUrl = "https://myservice.com/errorPage"
  val MaxFileSize = 512

  private val template = PrepareUploadRequestV2(
    callbackUrl = CallbackUrl,
    successRedirect = None,
    errorRedirect = ErrorRedirectUrl,
    minimumFileSize = Some(1),
    maximumFileSize = Some(99),
    expectedContentType = Some("application/pdf"))

  def aV2RequestWith(successRedirectUrl: Option[String]): PrepareUploadRequestV2 =
    template.copy(successRedirect = successRedirectUrl)
}