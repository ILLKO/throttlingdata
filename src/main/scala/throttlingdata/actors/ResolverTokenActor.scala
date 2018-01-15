package throttlingdata.actors

import akka.actor.ActorRef

import scala.collection.mutable

object ResolverTokenActor {
  sealed trait ResolverTokenActorRequest
  case class ResolveCounterNameByToken(token: String) extends ResolverTokenActorRequest
  case class ResolveNameByToken(token: String) extends ResolverTokenActorRequest
  case class RegisterNameByToken(token: String, name: String) extends ResolverTokenActorRequest
  case class ResolverTokenSetInitializer(actorRef: ActorRef) extends ResolverTokenActorRequest

  sealed trait ResolverTokenActorCounterNameResponse
  case class ResolvedCounterName(name: String) extends ResolverTokenActorCounterNameResponse
  case class ResolvedUnauthorizedYet() extends ResolverTokenActorCounterNameResponse

  sealed trait ResolverTokenRegisterResponse
  case class CounterRegisteredSuccessfully() extends ResolverTokenRegisterResponse
  case class CounterRegisteredAlreadyExists() extends ResolverTokenRegisterResponse

  sealed trait ResolverTokenActorInitResponse
  case class ResolverTokenActorReady() extends ResolverTokenActorInitResponse
}
class ResolverTokenActor extends ImplicitActor {

  import ResolverTokenActor._
  import InitializerActor._

  // TODO remove old
  var tokens: mutable.Map[String, String] = mutable.Map.empty

  var initializerActorRef: ActorRef = _

  override def receive: Receive = receiveInit

  def receiveInit: Receive = {

    case ResolverTokenSetInitializer(actorRef) =>
      initializerActorRef = actorRef
      context become receiveMain
      sender ! ResolverTokenActorReady()

    case message =>
      self ! message
  }

  def receiveMain: Receive = {

    case ResolveCounterNameByToken(token) =>
      tokens.get(token) match {
        case Some(name) =>
          logger.info(s"resolve counter with name $name by token $token")
          sender ! ResolvedCounterName(name)
        case None =>
          logger.info(s"no any name by token $token")
          sender ! ResolvedUnauthorizedYet()
          initializerActorRef ! CreateCounterByToken(token)
      }

    case RegisterNameByToken(token, name) =>
      logger.info(s"register counter with name $name for token $token")
      tokens.get(token) match {
        case Some(_) =>
          sender ! CounterRegisteredAlreadyExists()
          logger.info(s"already exists name $name for token $token")
        case None =>
          tokens += (token -> name)
          sender ! CounterRegisteredSuccessfully()
          logger.info(s"registered name $name for token $token")
      }
  }
}
