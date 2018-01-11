package throttlingdata.actors

import akka.actor.ActorRef
import throttlingdata.ThrottlingDataConf

import scala.collection.mutable
import scala.util.{Failure, Success}


object ResolverActor {
  sealed trait ResolverActorRequest
  case class ResolveCounterNameByToken(token: String) extends ResolverActorRequest
  case class ResolveNameByToken(token: String) extends ResolverActorRequest
  case class RegisterNameByToken(token: String, name: String) extends ResolverActorRequest

  case class SetInitializer(actorRef: ActorRef) extends ResolverActorRequest

  sealed trait ResolverActorResponse
  case class ResolvedCounterName(name: String) extends ResolverActorResponse
  case class ResolvedUnauthorizedYet() extends ResolverActorResponse

  case class ResolvedNameByToken(name: Option[String]) extends ResolverActorResponse

  case class ResolverActorOk() extends ResolverActorResponse
}
class ResolverActor extends ImplicitActor {

  import ResolverActor._
  import InitializerActor._

  var tokens: mutable.Map[String, String] = mutable.Map.empty

  var initializerActorRef: ActorRef = null

  override def receive: Receive = receiveInit

  def receiveInit: Receive = {

    case SetInitializer(actorRef) =>
      initializerActorRef = actorRef
      context become receiveMain
      sender ! ResolverActorOk()
  }

  def receiveMain: Receive = {

    case ResolveCounterNameByToken(token) =>
      tokens.get(token) match {
        case Some(name) =>
          sender ! ResolvedCounterName(name)
        case None =>
          initializerActorRef ! CreateCounterByToken(token)
          sender ! ResolvedUnauthorizedYet()
      }

    case ResolveNameByToken(token) =>
      tokens.get(token) match {
        case Some(name) =>
          sender ! ResolvedNameByToken(Some(name))
        case None =>
          sender ! ResolvedNameByToken(None)
      }

    case RegisterNameByToken(token, name) =>
      tokens += (token -> name)
  }
}
