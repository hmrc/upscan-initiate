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

package utils

import play.api.Logger
import play.api.http.HeaderNames.USER_AGENT
import play.api.mvc.Results.BadRequest
import play.api.mvc.{Request, Result}

import scala.concurrent.Future

trait UserAgentFilter {
  /*
   * We require the user agent to be set with the name of the client service.
   */
  def requireUserAgent[A](block: (Request[A], String) => Future[Result])(implicit request: Request[A]): Future[Result] =
    request.headers.get(USER_AGENT).fold(onMissingUserAgent())(block(request, _))

  private def onMissingUserAgent(): Future[Result] = {
    Logger.warn(s"No $USER_AGENT Request Header found - unable to identify client service")
    Future.successful(BadRequest(s"Missing $USER_AGENT Header"))
  }
}
