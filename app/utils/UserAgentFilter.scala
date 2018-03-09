package utils

import config.ServiceConfiguration
import play.api.http.HeaderNames
import play.api.mvc._

import scala.concurrent.Future

trait UserAgentFilter extends Results {

  protected val configuration : ServiceConfiguration

  private val userAgents = configuration.allowedUserAgents

  def onlyAllowedServices[A](action: Action[A]): Action[A] = Action.async(action.parser) { request =>
    if (validateUserAgent(request.headers.get(HeaderNames.USER_AGENT))) {
      action(request)
    } else {
      Future.successful(Forbidden("This service is not allowed to use upscan-initiate. " +
        "If you need to use this service, please contact Platform Services team."))
    }
  }

  private def validateUserAgent(userAgent: Option[String]): Boolean = {
    userAgents.isEmpty || userAgent.exists(userAgents.contains)
  }
}