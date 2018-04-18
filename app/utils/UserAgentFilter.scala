package utils

import config.ServiceConfiguration
import play.api.Logger
import play.api.http.HeaderNames
import play.api.mvc.Action
import play.api.mvc.Results.Forbidden

import scala.concurrent.Future

trait UserAgentFilter {

  protected val configuration : ServiceConfiguration

  private val userAgents: Seq[String] = configuration.allowedUserAgents

  def onlyAllowedServices[A](action: Action[A]): Action[A] = Action.async(action.parser) { request =>
    if (validateUserAgent(request.headers.get(HeaderNames.USER_AGENT))) {
      action(request)
    } else {
      Logger.warn(s"Invalid User-Agent: [${request.headers.get(HeaderNames.USER_AGENT)}].")

      Future.successful(Forbidden("This service is not allowed to use upscan-initiate. " +
        "If you need to use this service, please contact Platform Services team."))
    }
  }

  private def validateUserAgent(userAgent: Option[String]): Boolean = {
    userAgents.isEmpty || userAgent.exists(userAgents.contains)
  }
}