package throttlingdata.actors

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import throttlingdata.model.Sla
import throttlingdata.service.SlaService

import scala.collection.mutable

object InitializerActor {
  sealed trait InitializerActorRequest
  case class CreateCounterByToken(token: String) extends InitializerActorRequest
  case class CreateCounterByName(name: String) extends InitializerActorRequest
  case class CreateRpsCounter(token: String, sla: Sla) extends InitializerActorRequest
  case class CreateRpsCounterError(token: String, message: String) extends InitializerActorRequest

  case class SetResolvers(resolverTokenRef: ActorRef,
                          resolverNameRef: ActorRef) extends InitializerActorRequest

  sealed trait InitializerActorResponse
  case class CounterActorRef(actorRef: ActorRef) extends InitializerActorResponse

  sealed trait InitializerActorInitResponse
  case class InitializerActorReady() extends InitializerActorInitResponse
}
class InitializerActor(slaService: SlaService) extends ImplicitActor {

  import InitializerActor._
  import ResolverTokenActor._
  import ResolverNameActor._
  import SlaRequestActor._

  var requests: mutable.Map[String, ActorRef] = mutable.Map.empty

  var resolverTokenActorRef: ActorRef = _
  var resolverNameActorRef: ActorRef = _

  override def receive: Receive = receiveInit

  def receiveInit: Receive = {

    case SetResolvers(resolverTokenRef, resolverNameRef) =>
      resolverTokenActorRef = resolverTokenRef
      resolverNameActorRef = resolverNameRef
      context become receiveMain
      sender ! InitializerActorReady()

    case message =>
      self ! message
  }

  def receiveMain: Receive = {

    case CreateCounterByName(name) =>

    case CreateCounterByToken(token) =>
      logger.info(s"Get counter by token = $token")
      requests.get(token) match {
        case Some(_) =>
          logger.info(s"Counter by token = $token exists")
        case None =>
          val slaRequestRef =
            actorSystem.actorOf(Props(new SlaRequestActor(slaService)))
          requests += (token -> slaRequestRef)
          slaRequestRef ! GetSlaDataByToken(token)
      }

    case CreateRpsCounter(token: String, sla: Sla) =>
      logger.info(s"Create rps counter for token = $token and name = ${sla.user}")
      val counterRef = context.actorOf(
        name = sla.user,
        props = Props(new UserRpsCounterActor(sla.rps, sla.user))
      )
      (resolverNameActorRef ? RegisterCounterByName(sla.user, counterRef))
        .mapTo[ResolverNameRegisterResponse].map {
          case NameRegisteredSuccessfully() =>
            logger.info(s"NameRegisteredSuccessfully for token = $token")
            (resolverTokenActorRef ? RegisterNameByToken(token, sla.user))
              .mapTo[ResolverTokenRegisterResponse].map {
                case CounterRegisteredSuccessfully() =>
                  logger.info(s"CounterRegisteredSuccessfully for token = $token")
                  requests.remove(token)
                case CounterRegisteredAlreadyExists() =>
                  logger.info(s"CounterRegisteredAlreadyExists for token = $token")
            }
          case NameRegisteredAlreadyExists() =>
            logger.info(s"NameRegisteredAlreadyExists for name = ${sla.user}")
      }

    case CreateRpsCounterError(token, message) =>
      logger.info(s"Error for getting sla for token = $token")
      requests.remove(token)

//      (resolverTokenActorRef ? ResolveNameByToken(token)).mapTo[ResolverTokenActorNameExistsResponse].map {
//        case ResolvedNameByToken(nameOption) =>
//          nameOption match {
//            case Some(name) =>
//              logger.info(s"init new counter, counter already created for token = $token with name = $name")
//            case None =>
//              logger.info(s"init new counter, for token = $token")
//              val slaFuture: Future[Sla] =
//                slaService.getSlaByToken(token)
//              logger.info(s"slaFuture => $slaFuture")
//              val sla = Sla("user_111", 2)
//              slaFuture.onComplete {
//                case Success(sla) =>
//                  logger.info(s"slaFuture complete, sla user = ${sla.user}, rps = ${sla.rps}")
                  //requester ! CounterByNameActorRef(
//                  val actorRef = context.actorOf(
//                    name = sla.user,
//                    props = Props(new UserRpsCounterActor(sla.rps, sla.user))
//                  )
//                  logger.info(s"actorRef new counter = $actorRef")
                  //)
//                  resolverTokenActorRef ! RegisterNameByToken(token, sla.user)
//                case Failure(ex) =>
//                  logger.info("exception when call sla: " + ex.getMessage)
//              }
//          }
//      }
  }
}
