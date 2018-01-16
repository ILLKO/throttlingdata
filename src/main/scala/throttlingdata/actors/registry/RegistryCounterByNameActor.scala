package throttlingdata.actors.registry

import akka.actor.{ActorRef, PoisonPill}
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

  sealed trait RegistryCounterInitResponse
  case class RegistryCounterInitDone() extends RegistryCounterInitResponse
}
class RegistryCounterByNameActor extends ImplicitActor {

  import RegistryCounterByNameActor._

  // TODO remove old name+ref
  // (consider create registry actor by the name which know all his tokens - and can remove all tokens and itself)

  var countersByName: mutable.Map[String, ActorRef] = mutable.Map.empty

  var initializerActorRef: ActorRef = _

  override def receive: Receive = receiveInit

  def receiveInit: Receive = {

    case RegistryCounterInit(initializerRef) =>
      initializerActorRef = initializerRef
      context become receiveMain
      sender ! RegistryCounterInitDone()

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
        case Some(oldCounterActorRef) =>
          countersByName += (name -> counterActorRef)
          oldCounterActorRef ! PoisonPill
          logger.info(s"registered new counter for name $name")
        case None =>
          countersByName += (name -> counterActorRef)
          logger.info(s"registered counter for name $name")
      }
      sender ! CounterRegisteredSuccessfully()
  }
}
