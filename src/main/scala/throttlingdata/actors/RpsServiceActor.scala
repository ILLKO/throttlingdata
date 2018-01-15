package throttlingdata.actors

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import throttlingdata.ThrottlingDataConf
import throttlingdata.actors.common.ImplicitActor
import throttlingdata.actors.counter.RpsCounterUnauthorizedActor
import throttlingdata.actors.registry.{RegistryCounterByNameActor, RegistryNameByTokenActor}
import throttlingdata.service.SlaService

import scala.util.{Failure, Success}

object RpsServiceActor {
  sealed trait RpsServiceActorRequest
  case class IsAllowedByTokenRequest(token: Option[String],
                                     millis: Long) extends RpsServiceActorRequest

  sealed trait RpsServiceActorInitRequest
  case class RpsServiceInit() extends RpsServiceActorInitRequest
  case class RegistryNameByTokenReady(actorRef: ActorRef) extends RpsServiceActorInitRequest
  case class RegistryCounterByNameReady(actorRef: ActorRef) extends RpsServiceActorInitRequest
  case class InitializerReady(initializerActorRef: ActorRef) extends RpsServiceActorInitRequest
  case class UnauthorizedReady(unauthorizedActorRef: ActorRef) extends RpsServiceActorInitRequest

  sealed trait RpsServiceActorResponse
  case class IsAllowedByTokenResponse(result: Boolean) extends RpsServiceActorResponse
  case class IsAllowedErrorResponse(message: String) extends RpsServiceActorResponse
}
class RpsServiceActor(graceRps: Int, slaService: SlaService) extends ImplicitActor {

  import RpsServiceActor._
  import InitializerActor._
  import registry.RegistryNameByTokenActor._
  import registry.RegistryCounterByNameActor._
  import counter.RpsCounterActor._

  var registryNameByTokenActorRef: ActorRef = _
  var registryCounterByNameActorRef: ActorRef = _
  var initializerActorRef: ActorRef = _
  var unauthorizedActorRef: ActorRef = _

  def checkState(): Unit = {
    logger.info("RpsServiceActor check receiveMain")
    if (registryNameByTokenActorRef != null &&
      registryCounterByNameActorRef != null &&
        initializerActorRef != null &&
        unauthorizedActorRef != null) {
      logger.info("RpsServiceActor become receiveMain")
      context become receiveMain
    }
  }

  override def receive: Receive = receiveInit

  def receiveInit: Receive = {

    case RegistryNameByTokenReady(actorRef) =>
      registryNameByTokenActorRef = actorRef
      checkState()

    case RegistryCounterByNameReady(actorRef) =>
      registryCounterByNameActorRef = actorRef
      checkState()

    case InitializerReady(actorRef) =>
      initializerActorRef = actorRef
      checkState()

    case UnauthorizedReady(actorRef) =>
      unauthorizedActorRef = actorRef
      checkState()

    case RpsServiceInit() =>
      val requester = self

      val registryNameByTokenRef =
        actorSystem.actorOf(
          props = Props(new RegistryNameByTokenActor()),
          name = ThrottlingDataConf.registryNameByTokenActorName
        )

      val registryCounterByNameRef =
        actorSystem.actorOf(
          props = Props(new RegistryCounterByNameActor()),
          name = ThrottlingDataConf.registryCounterByNameActorName
        )

      val initializerActorRef =
        actorSystem.actorOf(
          props = Props(new InitializerActor(slaService)),
          name = ThrottlingDataConf.initializerActorName
        )

      (initializerActorRef ? InitializerInit(registryNameByTokenRef, registryCounterByNameRef))
        .mapTo[InitializerActorInitResponse].map {
          case InitializerActorInited() =>
            logger.info("initializerActorRef ready")
            requester ! InitializerReady(initializerActorRef)
        }
      logger.info("initializerActorRef end")

      (registryNameByTokenRef ? RegisterNameInit(initializerActorRef))
        .mapTo[RegistryNameInitResponse].map {
          case RegistryNameInited() =>
            logger.info("registryNameByTokenRef ready")
            requester ! RegistryNameByTokenReady(registryNameByTokenRef)
        }
      logger.info("registryNameByTokenRef end")

      (registryCounterByNameRef ? RegistryCounterInit(initializerActorRef))
        .mapTo[RegistryCounterInitResponse].map {
          case RegistryCounterInited() =>
            logger.info("registryCounterByNameRef ready")
            requester ! RegistryCounterByNameReady(registryCounterByNameRef)
        }
      logger.info("registryCounterByNameRef end")

      // TODO remove selection
      context.actorSelection(ThrottlingDataConf.unauthorizedActorName).resolveOne().onComplete {
        case Success(actorRef) =>
          logger.info("unauth counter exists")
          requester ! UnauthorizedReady(actorRef)
        case Failure(ex) =>
          logger.info("unauth counter not exists")
          val unauthorizedActorRef = actorSystem.actorOf(
            props = Props(new RpsCounterUnauthorizedActor(graceRps)),
            name = ThrottlingDataConf.unauthorizedActorName
          )
          requester ! UnauthorizedReady(unauthorizedActorRef)
      }
      logger.info("unauthorizedActorRef end")

    case message =>
      self ! message
  }

  def receiveMain: Receive = {

    case IsAllowedByTokenRequest(tokenOption, millis) =>
      val requester = sender
      logger.info(s"IsAllowedByTokenRequest tokenOption = $tokenOption. sender = $requester")
      tokenOption match {
        case Some(token) =>
          (registryNameByTokenActorRef ? ResolveNameByToken(token))
            .mapTo[ResolveTokenResponse].map {
              case ResolvedCounterName(name) =>
                (registryCounterByNameActorRef ? ResolveCounterByName(name))
                  .mapTo[ResolveNameResponse].map {
                    case ResolvedCounterRef(counterActorRef) =>
                      logger.info("exists counter actor for " + name)
                      askCounter(millis, counterActorRef, requester)
                    case UnresolvedCounterRef() =>
                      logger.info("unauth yet")
                      askCounter(millis, unauthorizedActorRef, requester)
                  }
              case UnresolvedCounterName() =>
                logger.info("unauth yet")
                askCounter(millis, unauthorizedActorRef, requester)
            }
        case None =>
          logger.info("unauth")
          askCounter(millis, unauthorizedActorRef, requester)
      }
  }

  def askCounter(millis: Long, counterActorRef: ActorRef, requester: ActorRef): Unit = {
    (counterActorRef ? IsAllowedRequest(millis)).mapTo[RpsCounterActorResponse].map {
      case IsAllowedResponse(result) =>
        logger.info(s"counter response result $result")
        requester ! IsAllowedByTokenResponse(result)
    }
  }
}
