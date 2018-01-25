package throtlingdata

import akka.pattern.ask
import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.util.Timeout
import throttlingdata.ThrottlingDataConf

import scala.concurrent.duration._

object ServiceCall {
  case class Request(token: String, timestamp: Long)
  case class Response(token: String, timestamp: Long, isAllowed: Boolean)
}
class ServiceCall(system: ActorSystem,
                  testProbeRef: ActorRef,
                  rpsServiceActorRef: ActorRef) extends Actor {

  import throttlingdata.actors.root.RpsServiceActor._
  import ServiceCall._

  implicit val timeout = Timeout(ThrottlingDataConf.requestTimeout seconds)
  implicit val executionContext = system.dispatcher

  override def preStart(): Unit = {
    rpsServiceActorRef ! RpsServiceInit()
  }

  def receive = {

    case Request(token, timestamp) =>
      (rpsServiceActorRef ? IsAllowedByTokenRequest(Some(token), timestamp))
        .mapTo[RpsServiceActorResponse].map {
        case IsAllowedByTokenResponse(value) =>
          println("Is allowed value = " + value)
          testProbeRef ! Response(token, timestamp, value)
        case IsAllowedErrorResponse(message) =>
          println("Is allowed error = " + message)
          testProbeRef ! Response(token, timestamp, false)
        case _ =>
          println("Is allowed _")
          throw new IllegalStateException(
            "Unhandled response for ThrottlingDataService.isRequestAllowed")
      }
  }
}