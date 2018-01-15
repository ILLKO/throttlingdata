package throttlingdata.actors.registry

import akka.actor.ActorRef
import throttlingdata.actors.common.ImplicitActor
import throttlingdata.actors.InitializerActor

import scala.collection.mutable

object RegistryNameByTokenActor {
  sealed trait ResolveTokenRequest
  case class ResolveNameByToken(token: String) extends ResolveTokenRequest
  case class RegisterNameByToken(token: String, name: String) extends ResolveTokenRequest

  case class RegisterNameInit(initializerRef: ActorRef) extends ResolveTokenRequest

  sealed trait ResolveTokenResponse
  case class ResolvedCounterName(name: String) extends ResolveTokenResponse
  case class UnresolvedCounterName() extends ResolveTokenResponse

  sealed trait RegisterNameResponse
  case class NameRegisteredSuccessfully() extends RegisterNameResponse
  case class NameAlreadyExists() extends RegisterNameResponse

  sealed trait RegistryNameInitResponse
  case class RegistryNameInited() extends RegistryNameInitResponse
}
class RegistryNameByTokenActor extends ImplicitActor {

  import InitializerActor._
  import RegistryNameByTokenActor._

  // TODO remove old
  var namesByToken: mutable.Map[String, String] = mutable.Map.empty

  var initializerActorRef: ActorRef = _

  override def receive: Receive = receiveInit

  def receiveInit: Receive = {

    case RegisterNameInit(initializerRef) =>
      initializerActorRef = initializerRef
      context become receiveMain
      sender ! RegistryNameInited()

    case message =>
      self ! message
  }

  def receiveMain: Receive = {

    case ResolveNameByToken(token) =>
      namesByToken.get(token) match {
        case Some(name) =>
          logger.info(s"resolve counter with name $name by token $token")
          sender ! ResolvedCounterName(name)
        case None =>
          logger.info(s"no any name by token $token")
          sender ! UnresolvedCounterName()
          initializerActorRef ! CreateCounterByToken(token)
      }

    case RegisterNameByToken(token, name) =>
      logger.info(s"register counter with name $name for token $token")
      namesByToken.get(token) match {
        case Some(_) =>
          sender ! NameAlreadyExists()
          logger.info(s"already exists name $name for token $token")
        case None =>
          namesByToken += (token -> name)
          sender ! NameRegisteredSuccessfully()
          logger.info(s"registered name $name for token $token")
      }
  }
}
