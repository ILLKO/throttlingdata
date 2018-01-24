package throttlingdata.actors.registry

import akka.actor.ActorRef
import throttlingdata.actors.common.ImplicitActor
import throttlingdata.actors.init.InitializerActor

import scala.collection.mutable

object RegistryNameByTokenActor {
  sealed trait ResolveTokenRequest
  case class ResolveNameByToken(token: String) extends ResolveTokenRequest
  case class RegisterNameByToken(token: String, name: String) extends ResolveTokenRequest
  case class UnregisterExpiredToken(timestamp: Long) extends ResolveTokenRequest
  case class RegisterNameInit(initializerRef: ActorRef) extends ResolveTokenRequest

  sealed trait ResolveTokenResponse
  case class ResolvedCounterName(name: String) extends ResolveTokenResponse
  case class UnresolvedCounterName() extends ResolveTokenResponse

  sealed trait RegisterNameResponse
  case class NameRegisteredSuccessfully() extends RegisterNameResponse

  sealed trait RegistryNameInitResponse
  case class RegistryNameInitDone() extends RegistryNameInitResponse
}
class RegistryNameByTokenActor extends ImplicitActor {

  import InitializerActor._
  import RegistryNameByTokenActor._

  var namesByToken: mutable.Map[String, String] = mutable.Map.empty
  var tokensTimedQueue: mutable.Queue[(Long, String)] = mutable.Queue.empty

  var initializerActorRef: ActorRef = _

  override def receive: Receive = receiveInit

  def receiveInit: Receive = {

    case RegisterNameInit(initializerRef) =>
      initializerActorRef = initializerRef
      context become receiveMain
      sender ! RegistryNameInitDone()

    case message =>
      logger.info(s"Message in RegistryNameByTokenActor before init done: $message")
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
      logger.info(s"register counter with name = $name for token = $token")
      namesByToken.get(token) match {
        case Some(oldName) =>
          logger.info(s"token already exists with name = $name for token = $token")
          if (oldName != name) {
            logger.info(s"old name = $oldName not equals with new name = $name and replaced")
            namesByToken += (token -> name)
          }
        case None =>
          namesByToken += (token -> name)
          tokensTimedQueue.enqueue((System.currentTimeMillis(), token))
          logger.info(s"token registered with name = $name for token = $token")
      }
      sender ! NameRegisteredSuccessfully()

    case UnregisterExpiredToken(expirationTimestamp) =>
      logger.info(s"unregistered older then expirationTimestamp = $expirationTimestamp")
      tokensTimedQueue.get(0) match {
        case Some((tokenTimestamp, token)) =>
          if (tokenTimestamp < expirationTimestamp) {
            logger.info(s"unregistered token = $token")
            tokensTimedQueue.dequeue()
            namesByToken.remove(token)
            self ! UnregisterExpiredToken(expirationTimestamp)
          }
        case None =>
          logger.info(s"nothing to unregistered")
      }
  }
}
