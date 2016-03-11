package utils

import javax.inject.{Inject, Singleton}

import controllers.WebJarAssets
import play.api.Configuration

@Singleton
class WebJarUtil @Inject() (config: Configuration, webJarAssets: WebJarAssets) {
  // prepends a url if the assets.url config is set
  def url(path: String): String = {
    val baseUrl = controllers.routes.WebJarAssets.at(webJarAssets.locate(path)).url
    config.getString("assets.url").fold(baseUrl)(_ + baseUrl)
  }
}
