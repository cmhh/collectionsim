package org.cmhh

import com.typesafe.config.{Config, ConfigFactory}

object implicits {
  implicit val conf: Config = ConfigFactory.load() 
  implicit val random: Rand = Rand()(conf)
  implicit val router: Router = Router(conf.getString("router-settings.url"))
}