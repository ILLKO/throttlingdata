package throttlingdata.actors

import akka.actor.ActorRef

import scala.collection.mutable

object ResolverNameActor {
  sealed trait ResolverNameActorRequest
  case class ResolveCounterByName(name: String) extends ResolverNameActorRequest
  case class RegisterCounterByName(name: String, counterActorRef: ActorRef) extends ResolverNameActorRequest
  case class ResolverNameSetInitializer(actorRef: ActorRef) extends ResolverNameActorRequest

  sealed trait ResolverNameActorResponse
  case class ResolveCounterRef(counterActorRef: ActorRef) extends ResolverNameActorResponse
  case class NoCounterRef() extends ResolverNameActorResponse

  sealed trait ResolverNameRegisterResponse
  case class NameRegisteredSuccessfully() extends ResolverNameRegisterResponse
  case class NameRegisteredAlreadyExists() extends ResolverNameRegisterResponse

  sealed trait ResolverNameActorInitResponse
  case class ResolverNameActorReady() extends ResolverNameActorInitResponse
}
class ResolverNameActor extends ImplicitActor {

  import ResolverNameActor._
  import InitializerActor._

  var counters: mutable.Map[String, ActorRef] = mutable.Map.empty

  var initializerActorRef: ActorRef = _

  override def receive: Receive = receiveInit

  def receiveInit: Receive = {

    case ResolverNameSetInitializer(actorRef) =>
      initializerActorRef = actorRef
      context become receiveMain
      sender ! ResolverNameActorReady()

    case message =>
      self ! message
  }

  def receiveMain: Receive = {

    case ResolveCounterByName(name) =>
      counters.get(name) match {
        case Some(counterActorRef) =>
          logger.info(s"resolve counter ref by name $name")
          sender ! ResolveCounterRef(counterActorRef)
        case None =>
          logger.info(s"no any counter ref by name $name")
          sender ! NoCounterRef()
          initializerActorRef ! CreateCounterByName(name)
      }

    case RegisterCounterByName(name, counterActorRef) =>
      logger.info(s"register counter with name $name")
      counters.get(name) match {
        case Some(_) =>
          sender ! NameRegisteredAlreadyExists()
          logger.info(s"already exists counter for name $name")
        case None =>
          counters += (name -> counterActorRef)
          sender ! NameRegisteredSuccessfully()
          logger.info(s"registered counter for name $name")
      }
  }
}
