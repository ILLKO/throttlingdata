package throttlingdata

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import throttlingdata.actors.RpsServiceActor
import throttlingdata.service.{SlaService, ThrottlingService}

import scala.concurrent.{Await, ExecutionContext, Future}

class ThrottlingDataService(slaServiceImpl: SlaService)
                           (implicit val system: ActorSystem,
                            implicit val executionContext: ExecutionContext,
                            implicit val timeout: Timeout) extends ThrottlingService {

  import throttlingdata.actors.RpsServiceActor._

  override val graceRps: Int = ThrottlingDataConf.graceRps
  override val slaService: SlaService = slaServiceImpl

  val rpsServiceActorRef: ActorRef =
    system.actorOf(Props(new RpsServiceActor(graceRps, slaService)))

  // TODO init before first call
  rpsServiceActorRef ! RpsServiceInit()

  def rpsCounterActorCall(token: Option[String]): Future[Boolean] = {
    val millis =
      System.currentTimeMillis()
    (rpsServiceActorRef ? IsAllowedByTokenRequest(token, millis))
      .mapTo[RpsServiceActorResponse]
      .map {
        case IsAllowedByTokenResponse(value) =>
          println("Is allowed value = " + value)
          value
        case IsAllowedErrorResponse(message) =>
          println("Is allowed error = " + message)
          false
        case _ =>
          throw new IllegalStateException(
            "Unhandled response for ThrottlingDataService.isRequestAllowed")
      }
  }

  override def isRequestAllowed(token: Option[String]): Boolean = {
    Await.result(rpsCounterActorCall(token), timeout.duration)
  }
}
