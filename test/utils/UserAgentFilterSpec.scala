package utils

import akka.util.Timeout
import config.ServiceConfiguration
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import play.api.mvc.Action
import play.api.mvc.Results._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.play.test.UnitSpec
import scala.concurrent.duration._

class UserAgentFilterSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  class UserAgentFilterImpl(override val configuration: ServiceConfiguration) extends UserAgentFilter

  "UserAgentFilter" should {
    val action =  Action(_ => Ok("This is a successful result"))
    implicit val timeout = Timeout(3.seconds)

    "accept any request if no whitelist set" in {
      Given("a service configuration with no whitelist set")
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedUserAgents).thenReturn(Nil)
      val filter = new UserAgentFilterImpl(config)

      When("a request is received")
      val result = filter.onlyAllowedServices(action)(FakeRequest())

      Then("the request should be passed through the filter")
      status(result) shouldBe 200
      Helpers.contentAsString(result) shouldBe "This is a successful result"
    }

    "accept request if whitelist set and valid user agent" in {
      Given("a service configuration with no whitelist set")
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedUserAgents).thenReturn(List("VALID-AGENT"))
      val filter = new UserAgentFilterImpl(config)

      When("a request is received")
      val result = filter.onlyAllowedServices(action)(FakeRequest().withHeaders(("User-Agent", "VALID-AGENT")))

      Then("the request should be passed through the filter")
      status(result) shouldBe 200
      Helpers.contentAsString(result) shouldBe "This is a successful result"
    }

    "reject request if whitelist set and no user agent" in {
      Given("a service configuration with no whitelist set")
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedUserAgents).thenReturn(List("VALID-AGENT"))
      val filter = new UserAgentFilterImpl(config)

      When("a request is received")
      val result = filter.onlyAllowedServices(action)(FakeRequest())

      Then("the filter should reject as forbidden")
      status(result) shouldBe 403
      Helpers.contentAsString(result) shouldBe "This service is not allowed to use upscan-initiate. " +
        "If you need to use this service, please contact Platform Services team."
    }

    "reject request if whitelist set and invalid agent" in {
      Given("a service configuration with no whitelist set")
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedUserAgents).thenReturn(List("VALID-AGENT"))
      val filter = new UserAgentFilterImpl(config)

      When("a request is received")
      val result = filter.onlyAllowedServices(action)(FakeRequest().withHeaders(("User-Agent", "INVALID-AGENT")))

      Then("the filter should reject as forbidden")
      status(result) shouldBe 403
      Helpers.contentAsString(result) shouldBe "This service is not allowed to use upscan-initiate. " +
        "If you need to use this service, please contact Platform Services team."
    }
  }
}
