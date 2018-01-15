package throttlingdata.actors.registry

import akka.actor.ActorRef
import throttlingdata.actors.common.ImplicitActor

import scala.collection.mutable

object RegistryCounterByNameActor {
  sealed trait ResolveNameRequest
  case class ResolveCounterByName(name: String) extends ResolveNameRequest
  case class RegisterCounterByName(name: String, counterActorRef: ActorRef) extends ResolveNameRequest

  case class RegistryCounterInit(initializerRef: ActorRef) extends ResolveNameRequest

  sealed trait ResolveNameResponse
  case class ResolvedCounterRef(counterActorRef: ActorRef) extends ResolveNameResponse
  case class UnresolvedCounterRef() extends ResolveNameResponse

  sealed trait RegisterCounterResponse
  case class CounterRegisteredSuccessfully() extends RegisterCounterResponse
  case class CounterAlreadyExists() extends RegisterCounterResponse

  sealed trait RegistryCounterInitResponse
  case class RegistryCounterInited() extends RegistryCounterInitResponse
}
class RegistryCounterByNameActor extends ImplicitActor {

  import RegistryCounterByNameActor._

  // TODO remove old
  var countersByName: mutable.Map[String, ActorRef] = mutable.Map.empty

  var initializerActorRef: ActorRef = _

  override def receive: Receive = receiveInit

  def receiveInit: Receive = {

    case RegistryCounterInit(initializerRef) =>
      initializerActorRef = initializerRef
      context become receiveMain
      sender ! RegistryCounterInited()

    case message =>
      self ! message
  }

  def receiveMain: Receive = {

    case ResolveCounterByName(name) =>
      countersByName.get(name) match {
        case Some(counterActorRef) =>
          logger.info(s"resolve counter ref by name $name")
          sender ! ResolvedCounterRef(counterActorRef)
        case None =>
          logger.info(s"no any counter ref by name $name")
          sender ! UnresolvedCounterRef()
      }

    case RegisterCounterByName(name, counterActorRef) =>
      logger.info(s"register counter with name $name")
      countersByName.get(name) match {
        case Some(_) =>
          sender ! CounterAlreadyExists()
          logger.info(s"already exists counter for name $name")
        case None =>
          countersByName += (name -> counterActorRef)
          sender ! CounterRegisteredSuccessfully()
          logger.info(s"registered counter for name $name")
      }
  }
}
