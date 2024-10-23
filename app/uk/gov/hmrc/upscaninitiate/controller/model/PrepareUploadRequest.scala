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

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Reads.{max, min}

final case class PrepareUploadRequest(
  callbackUrl: String,
  minimumFileSize: Option[Long],
  maximumFileSize: Option[Long],
  successRedirect: Option[String],
  errorRedirect: Option[String],
  consumingService: Option[String]
)

object PrepareUploadRequest {

  def readsV1(maxFileSize: Long): Reads[PrepareUploadRequest] =
    readsV2(maxFileSize)
      .map(_.copy(errorRedirect = None))

  def readsV2(maxFileSize: Long): Reads[PrepareUploadRequest] =
    ( (__ \ "callbackUrl"     ).read[String]
    ~ (__ \ "minimumFileSize" ).readNullable[Long](min(0L))
    ~ (__ \ "maximumFileSize" ).readNullable[Long](min(0L) keepAnd max(maxFileSize))
    ~ (__ \ "successRedirect" ).readNullable[String]
    ~ (__ \ "errorRedirect"   ).readNullable[String]
    ~ (__ \ "consumingService").readNullable[String]
    )(PrepareUploadRequest.apply _)
      .filter(JsonValidationError("Maximum file size must be equal or greater than minimum file size"))(request =>
        request.minimumFileSize.getOrElse(0L) <= request.maximumFileSize.getOrElse(maxFileSize))
}
