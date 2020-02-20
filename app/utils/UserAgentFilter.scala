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
