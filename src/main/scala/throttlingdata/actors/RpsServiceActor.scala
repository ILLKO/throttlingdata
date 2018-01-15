package throttlingdata.actors

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import throttlingdata.ThrottlingDataConf
import throttlingdata.service.SlaService

import scala.util.{Failure, Success}

object RpsServiceActor {
  sealed trait RpsServiceActorRequest
  case class IsAllowedByTokenRequest(token: Option[String]) extends RpsServiceActorRequest

  sealed trait RpsServiceActorInitRequest
  case class StartInit() extends RpsServiceActorInitRequest
  case class ResolverTokenReady(resolverActorRef: ActorRef) extends RpsServiceActorInitRequest
  case class ResolverNameReady(resolverActorRef: ActorRef) extends RpsServiceActorInitRequest
  case class InitializerReady(initializerActorRef: ActorRef) extends RpsServiceActorInitRequest
  case class UnauthorizedReady(unauthorizedActorRef: ActorRef) extends RpsServiceActorInitRequest

  sealed trait RpsServiceActorResponse
  case class IsAllowedByTokenResponse(result: Boolean) extends RpsServiceActorResponse
  case class IsAllowedErrorResponse(message: String) extends RpsServiceActorResponse
}
class RpsServiceActor(graceRps: Int, slaService: SlaService) extends ImplicitActor {

  import RpsServiceActor._
  import ResolverTokenActor._
  import ResolverNameActor._
  import InitializerActor._
  import RpsCounterActor._

  var resolverTokenActorRef: ActorRef = _
  var resolverNameActorRef: ActorRef = _
  var initializerActorRef: ActorRef = _
  var unauthorizedActorRef: ActorRef = _

  def checkState(): Unit = {
    logger.info("RpsServiceActor check receiveMain")
    if (resolverTokenActorRef != null &&
        resolverNameActorRef != null &&
        initializerActorRef != null &&
        unauthorizedActorRef != null) {
      logger.info("RpsServiceActor become receiveMain")
      context become receiveMain
    }
  }

  override def receive: Receive = receiveInit

  def receiveInit: Receive = {

    case ResolverTokenReady(actorRef) =>
      resolverTokenActorRef = actorRef
      checkState()

    case ResolverNameReady(actorRef) =>
      resolverNameActorRef = actorRef
      checkState()

    case InitializerReady(actorRef) =>
      initializerActorRef = actorRef
      checkState()

    case UnauthorizedReady(actorRef) =>
      unauthorizedActorRef = actorRef
      checkState()

    case StartInit() =>
      val requester = self

      val resolverTokenActorRef =
        actorSystem.actorOf(
          props = Props(new ResolverTokenActor()),
          name = ThrottlingDataConf.resolverTokenActorName
        )

      val resolverNameActorRef =
        actorSystem.actorOf(
          props = Props(new ResolverNameActor()),
          name = ThrottlingDataConf.resolverNameActorName
        )

      val initializerActorRef =
        actorSystem.actorOf(
          props = Props(new InitializerActor(slaService)),
          name = ThrottlingDataConf.initializerActorName
        )

      (initializerActorRef ? SetResolvers(resolverTokenActorRef, resolverNameActorRef)).mapTo[InitializerActorInitResponse].map {
        case InitializerActorReady() =>
          logger.info("initializerActorRef ready")
          requester ! InitializerReady(initializerActorRef)
      }
      logger.info("initializerActorRef end")

      (resolverTokenActorRef ? ResolverTokenSetInitializer(initializerActorRef)).mapTo[ResolverTokenActorInitResponse].map {
        case ResolverTokenActorReady() =>
          logger.info("resolverTokenActorRef ready")
          requester ! ResolverTokenReady(resolverTokenActorRef)
      }
      logger.info("resolverTokenActorRef end")

      (resolverNameActorRef ? ResolverNameSetInitializer(initializerActorRef)).mapTo[ResolverNameActorInitResponse].map {
        case ResolverNameActorReady() =>
          logger.info("resolverNameActorRef ready")
          requester ! ResolverNameReady(resolverNameActorRef)
      }
      logger.info("resolverNameActorRef end")

      context.actorSelection(ThrottlingDataConf.unauthorizedActorName).resolveOne().onComplete {
        case Success(actorRef) =>
          logger.info("unauth counter exists")
          requester ! UnauthorizedReady(actorRef)
        case Failure(ex) =>
          logger.info("unauth counter not exists")
          val unauthorizedActorRef = actorSystem.actorOf(
            name = ThrottlingDataConf.unauthorizedActorName,
            props = Props(new UnauthorizedRpsCounterActor(graceRps))
          )
          requester ! UnauthorizedReady(unauthorizedActorRef)
      }
      logger.info("unauthorizedActorRef end")

    case message =>
      self ! message
  }

  def receiveMain: Receive = {

    case IsAllowedByTokenRequest(tokenOption) =>
      val requester = sender
      logger.info(s"IsAllowedByTokenRequest tokenOption = $tokenOption. sender = $requester")
      tokenOption match {
        case Some(token) =>
          (resolverTokenActorRef ? ResolveCounterNameByToken(token)).mapTo[ResolverTokenActorCounterNameResponse].map {
            case ResolvedCounterName(name) =>
              (resolverNameActorRef ? ResolveCounterByName(name)).mapTo[ResolverNameActorResponse].map {
                case ResolveCounterRef(counterActorRef) =>
                  logger.info("exists counter actor for " + name)
                  askCounter(counterActorRef, requester)
                case NoCounterRef() =>
                  logger.info("unauth yet")
                  askCounter(unauthorizedActorRef, requester)
              }
            case ResolvedUnauthorizedYet() =>
              logger.info("unauth yet")
              askCounter(unauthorizedActorRef, requester)
          }
        case None =>
          logger.info("unauth")
          askCounter(unauthorizedActorRef, requester)
      }
  }

  def askCounter(counterActorRef: ActorRef, requester: ActorRef): Unit = {
    (counterActorRef ? IsAllowedRequest()).mapTo[RpsCounterActorResponse].map {
      case IsAllowedResponse(result) =>
        logger.info(s"counter response result $result")
        requester ! IsAllowedByTokenResponse(result)
    }
  }
}
