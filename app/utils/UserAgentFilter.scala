package utils

import config.ServiceConfiguration
import play.api.Logger
import play.api.http.HeaderNames
import play.api.mvc.{Request, Result}
import play.api.mvc.Results.Forbidden

import scala.concurrent.Future

trait UserAgentFilter {

  protected val configuration : ServiceConfiguration

  private val userAgents: Seq[String] = configuration.allowedUserAgents

  def onlyAllowedServices[A](block: (Request[A], String) => Future[Result])
                            (implicit request: Request[A]): Future[Result] = {

    request.headers.get(HeaderNames.USER_AGENT) match {
      case Some(userAgent) if allowedUserAgent(userAgent) =>
        block(request, userAgent)
      case _ => {
        Logger.warn(s"Invalid User-Agent: [${request.headers.get(HeaderNames.USER_AGENT)}].")

        Future.successful(Forbidden("This service is not allowed to use upscan-initiate. " +
          "If you need to use this service, please contact Platform Services team."))
      }
    }
  }

  private def allowedUserAgent(userAgent: String): Boolean =
    userAgents.contains(userAgent)
}