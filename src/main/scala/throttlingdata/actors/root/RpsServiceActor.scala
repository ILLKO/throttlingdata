package throttlingdata.actors.root

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import throttlingdata.ThrottlingDataConf
import throttlingdata.actors.common.ImplicitActor
import throttlingdata.actors.counter.RpsCounterUnauthorizedActor
import throttlingdata.actors.init.InitializerActor
import throttlingdata.actors.registry.{RegistryCleanSchedulerActor, RegistryCounterByNameActor, RegistryNameByTokenActor}
import throttlingdata.actors.{counter, registry}
import throttlingdata.service.SlaService

object RpsServiceActor {
  sealed trait RpsServiceActorRequest
  case class IsAllowedByTokenRequest(token: Option[String],
                                     millis: Long) extends RpsServiceActorRequest

  sealed trait RpsServiceActorInitRequest
  case class RpsServiceInit() extends RpsServiceActorInitRequest
  case class RegistryNameByTokenReady(actorRef: ActorRef) extends RpsServiceActorInitRequest
  case class RegistryCounterByNameReady(actorRef: ActorRef) extends RpsServiceActorInitRequest
  case class InitializerReady(actorRef: ActorRef) extends RpsServiceActorInitRequest

  sealed trait RpsServiceActorResponse
  case class IsAllowedByTokenResponse(result: Boolean) extends RpsServiceActorResponse
  case class IsAllowedErrorResponse(message: String) extends RpsServiceActorResponse
}
class RpsServiceActor(graceRps: Int, slaService: SlaService) extends ImplicitActor {

  import InitializerActor._
  import RegistryCleanSchedulerActor._
  import RpsServiceActor._
  import counter.RpsCounterActor._
  import registry.RegistryCounterByNameActor._
  import registry.RegistryNameByTokenActor._

  var registryNameByTokenActorRef: ActorRef = _
  var registryCounterByNameActorRef: ActorRef = _
  var initializerActorRef: ActorRef = _

  val unauthorizedActorRef: ActorRef =
    actorSystem.actorOf(
      props = Props(new RpsCounterUnauthorizedActor(graceRps)),
      name = ThrottlingDataConf.unauthorizedActorName
    )
  val registryCleanSchedulerActorRef: ActorRef =
    actorSystem.actorOf(
      props = Props(new RegistryCleanSchedulerActor(ThrottlingDataConf.secondsSchedulerRecall)),
      name = ThrottlingDataConf.registryCleanSchedulerActorName
    )

  def checkState(): Unit = {
    if (registryNameByTokenActorRef != null &&
        registryCounterByNameActorRef != null &&
        initializerActorRef != null) {
      logger.info("RpsServiceActor become receiveMain")
      context become receiveMain
      registryCleanSchedulerActorRef ! WaitAndCall()
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

    case RpsServiceInit() =>
      val requester = self
      logger.info("RpsServiceInit message")
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
      (registryNameByTokenRef ? RegisterNameInit(initializerActorRef))
        .mapTo[RegistryNameInitResponse].map {
          case RegistryNameInitDone() =>
            logger.info("registryNameByTokenRef ready")
            requester ! RegistryNameByTokenReady(registryNameByTokenRef)
        }
      (registryCounterByNameRef ? RegistryCounterInit(initializerActorRef))
        .mapTo[RegistryCounterInitResponse].map {
          case RegistryCounterInitDone() =>
            logger.info("registryCounterByNameRef ready")
            requester ! RegistryCounterByNameReady(registryCounterByNameRef)
        }

    case message =>
      logger.info(s"Message in RpsServiceActor before init done: $message")
  }

  def receiveMain: Receive = {

    // TODO broadcast call to 10 subactors

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
