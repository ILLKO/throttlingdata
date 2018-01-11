package throttlingdata

import com.typesafe.config.{Config, ConfigFactory}

object ThrottlingDataConf {

  val config: Config = ConfigFactory.load("throttlingdata.conf")

  lazy val host: String = config.getString("http.host")
  lazy val port: Int = config.getInt("http.port")

  lazy val requestTimeout: Int =
    config.getInt("akka.requestTimeout")
  lazy val loggingActorName: String =
    config.getString("akka.loggingActorName")

  lazy val graceRps: Int =
    config.getInt("app.graceRps")
  lazy val resolverActorName: String =
    config.getString("app.resolverActorName")
  lazy val initializerActorName: String =
    config.getString("app.initializerActorName")
  lazy val unauthorizedActorName: String =
    config.getString("app.unauthorizedActorName")
}
