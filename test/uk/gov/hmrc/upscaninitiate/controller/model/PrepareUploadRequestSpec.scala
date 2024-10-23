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

package uk.gov.hmrc.upscaninitiate.controller.model

import play.api.libs.json.{JsSuccess, Json, Reads}
import uk.gov.hmrc.upscaninitiate.controller.model.PrepareUploadRequestSpec._
import uk.gov.hmrc.upscaninitiate.test.UnitSpec

class PrepareUploadRequestSpec extends UnitSpec {

  "V1 deserialisation" should {
    behave like
      testDeserialisation(
        reads                 = PrepareUploadRequest.readsV1(maxFileSize),
        expectedErrorRedirect = None
      )
  }

  "V2 deserialisation" should {
    behave like
      testDeserialisation(
        reads                 = PrepareUploadRequest.readsV2(maxFileSize),
        expectedErrorRedirect = Some("https://www.example.com/error")
      )
  }

  //noinspection ScalaStyle
  private def testDeserialisation(
    reads: Reads[PrepareUploadRequest],
    expectedErrorRedirect: Option[String]
  ) = {

    "deserialise from required fields" in {
      val parse =
        Json.parse(
          """
            |{
            |  "callbackUrl": "https://www.example.com/"
            |}
            |""".stripMargin
        ).validate(reads)

      val expectedPrepareUploadRequest =
        requestTemplate

      parse shouldBe JsSuccess(expectedPrepareUploadRequest)
    }

    "deserialise from all fields" in {
      val parse =
        Json.parse(
          """
            |{
            |  "callbackUrl"     : "https://www.example.com/",
            |  "minimumFileSize" : 0,
            |  "maximumFileSize" : 10,
            |  "successRedirect" : "https://www.example.com/success",
            |  "errorRedirect"   : "https://www.example.com/error",
            |  "consumingService": "some-consuming-service"
            |}
            |""".stripMargin
        ).validate(reads)

      val expectedPrepareUploadRequest =
        requestTemplate
          .copy(
            minimumFileSize  = Some(0),
            maximumFileSize  = Some(10),
            successRedirect  = Some("https://www.example.com/success"),
            errorRedirect    = expectedErrorRedirect,
            consumingService = Some("some-consuming-service")
          )

      parse shouldBe JsSuccess(expectedPrepareUploadRequest)
    }

    "reject out-of-bounds `minimumFileSize` values" in {
      val parse =
        Json.parse(
          """
            |{
            |  "callbackUrl"    : "https://www.example.com/",
            |  "minimumFileSize": -1
            |}
            |""".stripMargin
        ).validate(reads)

      parse.isError shouldBe true
    }

    "reject out-of-bounds `maximumFileSize` values" in {
      def parse(withMaximumFileSizeValue: Long) =
        Json.parse(
          s"""
            |{
            |  "callbackUrl"    : "https://www.example.com/",
            |  "maximumFileSize": $withMaximumFileSizeValue
            |}
            |""".stripMargin
        ).validate(reads)

      parse(withMaximumFileSizeValue = -1).isError shouldBe true
      parse(withMaximumFileSizeValue = maxFileSize + 1).isError shouldBe true
    }

    "reject erroneous `minimumFileSize` & `maximumFileSize` combinations" in {
      val parse =
        Json.parse(
          """
            |{
            |  "callbackUrl"    : "https://www.example.com/",
            |  "minimumFileSize": 100,
            |  "maximumFileSize": 99
            |}
            |""".stripMargin
        ).validate(reads)

      parse.isError shouldBe true
    }
  }
}

object PrepareUploadRequestSpec {

  val maxFileSize: Long =
    100

  val requestTemplate: PrepareUploadRequest =
    PrepareUploadRequest(
      callbackUrl      = "https://www.example.com/",
      minimumFileSize  = None,
      maximumFileSize  = None,
      successRedirect  = None,
      errorRedirect    = None,
      consumingService = None
    )
}
