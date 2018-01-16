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

  lazy val graceRps: Int = config.getInt("app.graceRps")
  lazy val secondsCheckSize: Int = config.getInt("app.secondsCheckSize")
  lazy val secondsSchedulerRecall: Int = config.getInt("app.secondsSchedulerRecall")
  lazy val registryNameByTokenActorName: String =
    config.getString("app.registryNameByTokenActorName")
  lazy val registryCounterByNameActorName: String =
    config.getString("app.registryCounterByNameActorName")
  lazy val registryCleanSchedulerActorName: String =
    config.getString("app.registryCleanSchedulerActorName")
  lazy val initializerActorName: String =
    config.getString("app.initializerActorName")
  lazy val unauthorizedActorName: String =
    config.getString("app.unauthorizedActorName")
}
