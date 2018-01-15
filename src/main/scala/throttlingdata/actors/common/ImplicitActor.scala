package throttlingdata.actors.common

import akka.actor.Actor
import akka.event.Logging
import akka.util.Timeout
import throttlingdata.ThrottlingDataConf

import scala.concurrent.duration._

trait ImplicitActor extends Actor {

  implicit val actorSystem = context.system
  implicit val timeout = Timeout(ThrottlingDataConf.requestTimeout seconds)
  implicit val executionContext = actorSystem.dispatcher

  val logger = Logging(actorSystem, this)
}
