package controllers

import javax.inject.Inject

import play.api.mvc.Security.AuthenticatedBuilder
import play.api.mvc._
import utils.{WebJarUtil, HerokuUtil}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class MainController @Inject() (heroku: HerokuUtil) (implicit webJarUtil: WebJarUtil) extends Controller {

  private val HEROKU_TOKEN = "HEROKU_TOKEN"

  def index = Authenticated.async { request =>
    heroku.apps(request.user).map { apps =>
      val names = apps.value.map(_.\("name").as[String])
      Ok(views.html.index(names))
    }
  }

  def status(app: String) = Authenticated.async { request =>
    heroku.addons(app, request.user).flatMap { addons =>
      val maybeHerokuConnect = addons.value.find(_.\("addon_service").\("name").as[String] == "herokuconnect")
      maybeHerokuConnect.fold(Future.successful(Ok(views.html.noHerokuConnect()))) { herokuConnect =>
        heroku.herokuConnectConnections(app, request.user).flatMap { herokuConnectConnections =>
          val connectionIds = herokuConnectConnections.value.map(_.\("id").as[String])
          val statusesFuture = Future.sequence {
            connectionIds.map { connectionId =>
              heroku.herokuConnectStatus(app, connectionId, request.user)
            }
          }

          statusesFuture.map { statuses =>
            Ok(views.html.herokuConnectStatus(statuses))
          }
        }
      }
    }
  }

  def logout = Action {
    Ok(views.html.login()).withNewSession
  }

  def herokuOAuthCallback(code: String) = Action.async {
    heroku.login(code).map(_.\("access_token").asOpt[String]).flatMap { maybeToken =>
      maybeToken.fold(Future.failed[Result](new Exception("Access Token not found"))) { token =>
        Future.successful(Redirect(routes.MainController.index()).withSession(HEROKU_TOKEN -> token))
      }
    } recover { case e: Error =>
      Unauthorized("Could not login to Heroku")
    }
  }

  object Authenticated extends AuthenticatedBuilder(_.session.get(HEROKU_TOKEN), _ => Redirect(heroku.loginUrl))

}

