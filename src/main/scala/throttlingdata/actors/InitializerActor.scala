package throttlingdata.actors

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import throttlingdata.actors.common.ImplicitActor
import throttlingdata.actors.counter.RpsCounterUserActor
import throttlingdata.model.Sla
import throttlingdata.service.SlaService

import scala.collection.mutable

object InitializerActor {
  sealed trait InitializerActorRequest
  case class CreateCounterByToken(token: String) extends InitializerActorRequest
  case class CreateRpsCounterForSla(token: String, sla: Sla) extends InitializerActorRequest
  case class CreateRpsCounterError(token: String, message: String) extends InitializerActorRequest

  case class InitializerInit(registryNameByTokenRef: ActorRef,
                             registryCounterByNameRef: ActorRef) extends InitializerActorRequest

  sealed trait InitializerActorInitResponse
  case class InitializerActorInited() extends InitializerActorInitResponse
}
class InitializerActor(slaService: SlaService) extends ImplicitActor {

  import InitializerActor._
  import SlaRequestActor._
  import registry.RegistryNameByTokenActor._
  import registry.RegistryCounterByNameActor._

  var requests: mutable.Map[String, ActorRef] = mutable.Map.empty

  var registryNameByTokenActorRef: ActorRef = _
  var registryCounterByNameActorRef: ActorRef = _

  override def receive: Receive = receiveInit

  def receiveInit: Receive = {

    case InitializerInit(registryNameByTokenRef, registryCounterByNameRef) =>
      registryNameByTokenActorRef = registryNameByTokenRef
      registryCounterByNameActorRef = registryCounterByNameRef
      context become receiveMain
      sender ! InitializerActorInited()

    case message =>
      self ! message
  }

  def receiveMain: Receive = {

    case CreateCounterByToken(token) =>
      logger.info(s"Get counter by token = $token")
      requests.get(token) match {
        case Some(_) =>
          logger.info(s"Counter by token = $token exists")
        case None =>
          val slaRequestRef =
            context.actorOf(Props(new SlaRequestActor(slaService)))
          requests += (token -> slaRequestRef)
          slaRequestRef ! GetSlaDataByToken(token)
      }

    case CreateRpsCounterForSla(token: String, sla: Sla) =>
      logger.info(s"Create rps counter for token = $token and name = ${sla.user}")
      val counterRef = context.actorOf(
        name = sla.user,
        props = Props(new RpsCounterUserActor(sla.rps, sla.user))
      )
      (registryCounterByNameActorRef ? RegisterCounterByName(sla.user, counterRef))
        .mapTo[RegisterCounterResponse].map {
          case CounterRegisteredSuccessfully() =>
            logger.info(s"CounterRegisteredSuccessfully: user = ${sla.user} and token = $token")
            (registryNameByTokenActorRef ? RegisterNameByToken(token, sla.user))
              .mapTo[RegisterNameResponse].map {
                case NameRegisteredSuccessfully() =>
                  logger.info(s"NameRegisteredSuccessfully: user = ${sla.user} and token = $token")
                  requests.remove(token)
                case NameAlreadyExists() =>
                  logger.info(s"NameAlreadyExists: user = ${sla.user} and token = $token")
              }
          case CounterAlreadyExists() =>
            logger.info(s"CounterAlreadyExists: user = ${sla.user} and token = $token")
        }

    case CreateRpsCounterError(token, message) =>
      logger.info(s"Error for getting sla for token = $token")
      requests.remove(token)
  }
}
