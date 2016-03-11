package utils

import javax.inject.Inject

import play.api.Configuration
import play.api.http.{HeaderNames, Status}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsArray, JsValue}
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.mvc.Results.EmptyContent

import scala.concurrent.Future

class HerokuUtil @Inject()(ws: WSClient, config: Configuration) {

  val oauthId = config.getString("heroku.oauth.id").get
  val oauthSecret = config.getString("heroku.oauth.secret").get

  val loginUrl = "https://id.heroku.com/oauth/authorize?client_id=%s&response_type=code&scope=%s".format(oauthId, "global")

  private def ws(url: String, accessToken: String): WSRequest = {
    ws
      .url(url)
      .withHeaders(HeaderNames.AUTHORIZATION -> s"Bearer $accessToken")
  }

  private def api(path: String, accessToken: String): WSRequest = {
    ws("https://api.heroku.com" + path, accessToken)
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.heroku+json; version=3")
  }

  def login(code: String): Future[JsValue] = {
    ws
    .url("https://id.heroku.com/oauth/token")
    .withQueryString(
      "grant_type" -> "authorization_code",
      "code" -> code,
      "client_secret" -> oauthSecret
    )
    .post(EmptyContent())
    .flatMap { response =>
      response.status match {
        case Status.OK => Future.successful(response.json)
        case _ => Future.failed(new Exception(response.body))
      }
    }
  }

  def apps(accessToken: String): Future[JsArray] = {
    api(s"/apps", accessToken).get().ok(_.json.as[JsArray])
  }

  def appInfo(app: String, accessToken: String): Future[JsValue] = {
    api(s"/apps/$app", accessToken).get().ok(_.json)
  }

  def addons(app: String, accessToken: String): Future[JsArray] = {
    api(s"/apps/$app/addons", accessToken).get().ok(_.json.as[JsArray])
  }

  def herokuConnectConnections(app: String, accessToken: String): Future[JsArray] = {
    ws("https://connect.heroku.com/api/v3/connections", accessToken)
      .withQueryString("app" -> app)
      .get()
      .ok(_.json.\("results").as[JsArray])
  }

  def herokuConnectStatus(app: String, connectionId: String, accessToken: String): Future[JsValue] = {
    ws(s"https://connect.heroku.com/api/v3/connections/$connectionId", accessToken)
      .withQueryString("deep" -> "true")
      .get()
      .ok(_.json)
  }

}