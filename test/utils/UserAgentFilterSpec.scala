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

import akka.util.Timeout
import org.scalatest.{GivenWhenThen, Matchers}
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.mvc.Results._
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import play.mvc.Http.HeaderNames.USER_AGENT
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.concurrent.duration._

class UserAgentFilterSpec extends UnitSpec with Matchers with GivenWhenThen {

  import UserAgentFilterSpec._

  "UserAgentFilter" should {
    "accept a request when the User-Agent header is specified" in {
      Given("a request that specifies a User-Agent header")
      val request = FakeRequest().withHeaders((USER_AGENT, SomeUserAgent))

      When("the request is received")
      val result = UserAgentFilter.requireUserAgent(block)(request)

      Then("the request should be accepted")
      status(result) shouldBe OK

      And("the User-Agent header value should be passed to the block")
      contentAsString(result) shouldBe SomeUserAgent
    }

    "reject a request when the User-Agent header is not specified" in {
      Given("a request that does not specify a User-Agent header")
      val request = FakeRequest()

      When("the request is received")
      val result = UserAgentFilter.requireUserAgent(block)(request)

      Then("the request should be rejected")
      status(result) shouldBe BAD_REQUEST
    }
  }
}

private object UserAgentFilterSpec {
  implicit val timeout: Timeout = Timeout(3.seconds)
  val SomeUserAgent = "SOME_USER-AGENT"
  val block: (Request[_], String) => Future[Result] = (_, userAgent) => Future.successful(Ok(userAgent))

  object UserAgentFilter extends UserAgentFilter
}