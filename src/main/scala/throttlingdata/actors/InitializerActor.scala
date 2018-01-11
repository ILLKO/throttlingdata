package throttlingdata.actors

import akka.actor.{ActorRef, ActorSelection, Props}
import akka.pattern.ask
import throttlingdata.ThrottlingDataConf
import throttlingdata.model.Sla
import throttlingdata.service.SlaService

import scala.concurrent.Future
import scala.util.{Failure, Success}

object InitializerActor {
  sealed trait InitializerActorRequest
  case class CreateCounterByToken(token: String) extends InitializerActorRequest

  case class SetResolver(actorRef: ActorRef) extends InitializerActorRequest

  sealed trait InitializerActorResponse
  case class CounterActorRef(actorRef: ActorRef) extends InitializerActorResponse

  sealed trait InitializerActorInitResponse
  case class InitializerActorReady() extends InitializerActorInitResponse
}
class InitializerActor(slaService: SlaService) extends ImplicitActor {

  import InitializerActor._
  import ResolverActor._

  var resolverActorRef: ActorRef = null

  override def receive: Receive = receiveInit

  def receiveInit: Receive = {

    case SetResolver(actorRef) =>
      resolverActorRef = actorRef
      context become receiveMain
      sender ! InitializerActorReady()
  }

  def receiveMain: Receive = {

    case CreateCounterByToken(token) =>
      val requester = sender
      (resolverActorRef ? ResolveNameByToken(token)).mapTo[ResolverActorResponse].map {
        case ResolvedNameByToken(nameOption) =>
          nameOption match {
            case Some(path) =>
              logger.info("CreateCounterByToken, counter already created for " + path)
            case None =>
              val slaFuture: Future[Sla] =
                slaService.getSlaByToken(token)
              slaFuture.onComplete {
                case Success(sla) =>
                  //requester ! CounterByNameActorRef(
                    actorSystem.actorOf(
                      name = "counter/" + sla.user,
                      props = Props(new UserRpsCounterActor(sla.rps, sla.user))
                    )
                  //)
                  resolverActorRef ! RegisterNameByToken(token, sla.user)
                case Failure(ex) =>
                  logger.info("exception when call sla: " + ex.getMessage)
              }
          }
      }
      logger.info("CreateCounterByToken for token " + token)
  }
}
