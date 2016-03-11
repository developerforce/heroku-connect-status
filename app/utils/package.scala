import play.api.http.Status
import play.api.libs.ws.WSResponse

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

package object utils {

  implicit def toRichFutureWSResponse(f: Future[WSResponse]): RichFutureWSResponse = new RichFutureWSResponse(f)

  case class UnauthorizedError(message: String) extends Exception {
    override def getMessage: String = message
  }

  case class RequestError(message: String) extends Exception {
    override def getMessage: String = message
  }

  case class NotFoundError(message: String) extends Exception {
    override def getMessage: String = message
  }

  class RichFutureWSResponse(val future: Future[WSResponse]) extends AnyVal {

    def ok[A](f: (WSResponse => A))(implicit executionContext: ExecutionContext): Future[A] = status(f)(Status.OK)
    def okF[A](f: (WSResponse => Future[A]))(implicit executionContext: ExecutionContext): Future[A] = statusF(f)(Status.OK)

    def created[A](f: (WSResponse => A))(implicit executionContext: ExecutionContext): Future[A] = status(f)(Status.CREATED)
    def createdF[A](f: (WSResponse => Future[A]))(implicit executionContext: ExecutionContext): Future[A] = statusF(f)(Status.CREATED)

    def noContent[A](f: (WSResponse => A))(implicit executionContext: ExecutionContext): Future[A] = status(f)(Status.NO_CONTENT)
    def noContentF[A](f: (WSResponse => Future[A]))(implicit executionContext: ExecutionContext): Future[A] = statusF(f)(Status.NO_CONTENT)

    def status[A](f: (WSResponse => A))(statusCode: Int)(implicit executionContext: ExecutionContext): Future[A] = {
      future.flatMap { response =>
        statusF { response =>
          Future.successful(f(response))
        } (statusCode)
      }
    }

    def statusF[A](f: (WSResponse => Future[A]))(statusCode: Int)(implicit executionContext: ExecutionContext): Future[A] = {
      future.flatMap { response =>
        response.status match {
          case `statusCode` =>
            f(response)
          case Status.UNAUTHORIZED =>
            Future.failed(UnauthorizedError((response.json \\ "message").map(_.as[String]).mkString(" ")))
          case Status.NOT_FOUND =>
            Future.failed(NotFoundError(response.body))
          case _ =>
            Future.failed(RequestError(response.body))
        }
      }
    }

  }

}
