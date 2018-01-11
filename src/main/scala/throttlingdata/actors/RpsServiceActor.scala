package throttlingdata.actors

import akka.actor.{ActorRef, PoisonPill, Props}
import akka.pattern.ask
import throttlingdata.ThrottlingDataConf
import throttlingdata.service.SlaService

import scala.concurrent.Future
import scala.util.{Failure, Success}

object RpsServiceActor {
  sealed trait RpsServiceActorRequest
  case class IsAllowedByTokenRequest(token: Option[String]) extends RpsServiceActorRequest

  sealed trait RpsServiceActorInitRequest
  case class StartInit() extends RpsServiceActorInitRequest
  case class ResolverReady(resolverActorRef: ActorRef) extends RpsServiceActorInitRequest
  case class InitializerReady(initializerActorRef: ActorRef) extends RpsServiceActorInitRequest
  case class UnauthorizedReady(unauthorizedActorRef: ActorRef) extends RpsServiceActorInitRequest

  sealed trait RpsServiceActorResponse
  case class IsAllowedByTokenResponse(result: Boolean) extends RpsServiceActorResponse
  case class IsAllowedErrorResponse(message: String) extends RpsServiceActorResponse
}
class RpsServiceActor(graceRps: Int, slaService: SlaService) extends ImplicitActor {

  import RpsServiceActor._
  import ResolverActor._
  import InitializerActor._
  import RpsCounterActor._

  var resolverActorRef: ActorRef = null
  var initializerActorRef: ActorRef = null
  var unauthorizedActorRef: ActorRef = null

//  override def preStart: Unit = {
//    logger.info("resolver Actor preStart before")
//    context.actorSelection(ThrottlingDataConf.resolverActorName).resolveOne().onComplete {
//      case Success(actorRef) =>
//        resolverActorRef = actorRef
//        logger.info("resolver Actor founded")
//      case Failure(ex) =>
//        logger.info("resolver Actor not found")
//    }
//    logger.info("resolver Actor preStart end")
//
//    logger.info("initializer Actor preStart before")
//    context.actorSelection(ThrottlingDataConf.initializerActorName).resolveOne().onComplete {
//      case Success(actorRef) =>
//        initializerActorRef = actorRef
//        logger.info("initializer Actor founded")
//      case Failure(ex) =>
//        logger.info("initializer Actor not found")
//    }
//    logger.info("initializer Actor preStart end")
//  }

  def checkState(): Unit = {
    logger.info("RpsServiceActor check receiveMain")
    if (resolverActorRef != null && initializerActorRef != null && unauthorizedActorRef != null) {
      logger.info("RpsServiceActor become receiveMain")
      context become receiveMain
    }
  }

  override def receive: Receive = receiveInit

  def receiveInit: Receive = {

    case ResolverReady(actorRef) =>
      resolverActorRef = actorRef
      checkState()

    case InitializerReady(actorRef) =>
      initializerActorRef = actorRef
      checkState()

    case UnauthorizedReady(actorRef) =>
      unauthorizedActorRef = actorRef
      checkState()

    case StartInit() =>
      val requester = self

      val resolverActorRef =
        actorSystem.actorOf(
          props = Props(new ResolverActor()),
          name = ThrottlingDataConf.resolverActorName
        )

      val initializerActorRef =
        actorSystem.actorOf(
          props = Props(new InitializerActor(slaService)),
          name = ThrottlingDataConf.initializerActorName
        )

      (initializerActorRef ? SetResolver(resolverActorRef)).mapTo[InitializerActorInitResponse].map {
        case InitializerActorReady() =>
          logger.info("initializerActorRef ready")
          requester ! InitializerReady(initializerActorRef)
      }
      logger.info("initializerActorRef end")

      (resolverActorRef ? SetInitializer(initializerActorRef)).mapTo[ResolverActorOk].map {
        case ResolverActorOk() =>
          logger.info("resolverActorRef ready")
          requester ! ResolverReady(resolverActorRef)
      }
      logger.info("resolverActorRef end")

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
  }

  def receiveMain: Receive = {

    case IsAllowedByTokenRequest(tokenOption) =>
      val requester = sender
      logger.info("IsAllowedByTokenRequest tokenOption - " + tokenOption)
      tokenOption match {
        case Some(token) =>
          (resolverActorRef ? ResolveCounterNameByToken(token)).mapTo[ResolverActorResponse].map {
            case ResolvedCounterName(name) =>
              context.actorSelection(ThrottlingDataConf.initializerActorName + "/" + name).resolveOne().onComplete {
                case Success(actorRef) =>
                  logger.info("exists counter actor for " + name)
                  (actorRef ? IsAllowedRequest()).mapTo[RpsCounterActorResponse].map {
                    case IsAllowedResponse(result) =>
                      logger.info("exists counter actor for IsAllowedResponse " + result)
                      requester ! IsAllowedByTokenResponse(result)
                  }
                case Failure(ex) =>
                  logger.info("not exists counter actor for " + name)
                  requester ! IsAllowedErrorResponse(ex.getMessage)
              }
            case ResolvedUnauthorizedYet() =>
              logger.info("unauth yet")
              (unauthorizedActorRef ? IsAllowedRequest()).mapTo[RpsCounterActorResponse].map {
                case IsAllowedResponse(result) =>
                  logger.info("unauth yet IsAllowedResponse " + result)
                  requester ! IsAllowedByTokenResponse(result)
              }
          }
        case None =>
          logger.info("unauth")
          (unauthorizedActorRef ? IsAllowedRequest()).mapTo[RpsCounterActorResponse].map {
            case IsAllowedResponse(result) =>
              logger.info("unauth IsAllowedResponse " + result)
              requester ! IsAllowedByTokenResponse(result)
          }
      }
  }
}
