package utils

import akka.util.Timeout
import config.ServiceConfiguration
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import play.api.mvc.{Request, Result}
import play.api.mvc.Results._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.concurrent.duration._

class UserAgentFilterSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  class UserAgentFilterImpl(override val configuration: ServiceConfiguration) extends UserAgentFilter

  "UserAgentFilter" should {
    val block: (Request[_], String) => Future[Result] = (_,_) => Future.successful(Ok("This is a successful result"))

    implicit val timeout = Timeout(3.seconds)

    "accept request if user agent in whitelist" in {
      Given("a service configuration with no whitelist set")
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedUserAgents).thenReturn(List("VALID-AGENT"))
      val filter = new UserAgentFilterImpl(config)

      When("a request is received")
      val result = filter.onlyAllowedServices(block)(FakeRequest().withHeaders(("User-Agent", "VALID-AGENT")))

      Then("the request should be passed through the filter")
      status(result) shouldBe 200
      Helpers.contentAsString(result) shouldBe "This is a successful result"
    }

    "reject request if user agent not in whitelist" in {
      Given("a service configuration with no whitelist set")
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedUserAgents).thenReturn(List("VALID-AGENT"))
      val filter = new UserAgentFilterImpl(config)

      When("a request is received")
      val result = filter.onlyAllowedServices(block)(FakeRequest())

      Then("the filter should reject as forbidden")
      status(result) shouldBe 403
      Helpers.contentAsString(result) shouldBe "This service is not allowed to use upscan-initiate. " +
        "If you need to use this service, please contact Platform Services team."
    }
  }
}
