package throttlingdata

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import throttlingdata.actors.ResolverActor.SetInitializer
import throttlingdata.actors.{ResolverActor, UserRpsCounterActor}
import throttlingdata.service.{SlaService, ThrottlingService}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

class ThrottlingDataService(slaServiceImpl: SlaService,
                            implicit val system: ActorSystem,
                            implicit val executionContext: ExecutionContext,
                            implicit val timeout: Timeout) extends ThrottlingService {

  import throttlingdata.actors.RpsServiceActor
  import throttlingdata.actors.RpsServiceActor._
  import throttlingdata.actors.InitializerActor
  import throttlingdata.actors.InitializerActor._

  override val graceRps: Int = ThrottlingDataConf.graceRps
  override val slaService: SlaService = slaServiceImpl

//  context.actorSelection(ThrottlingDataConf.resolverActorName).resolveOne().onComplete {
//    case Success(actorRef) =>
//      resolverActorRef = actorRef
//    case Failure(ex) =>
//  }
//
//  context.actorSelection(ThrottlingDataConf.initializerActorName).resolveOne().onComplete {
//    case Success(actorRef) =>
//      initializerActorRef = actorRef
//    case Failure(ex) =>
//  }

  val rpsActorRef: ActorRef =
    system.actorOf(Props(new RpsServiceActor(graceRps, slaService)))
  rpsActorRef ! StartInit()

  def rpsCounterActorCall(token: Option[String]): Future[Boolean] = {
    (rpsActorRef ? IsAllowedByTokenRequest(token))
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