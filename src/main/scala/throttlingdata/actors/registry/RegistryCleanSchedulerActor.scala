package throttlingdata.actors.registry

import throttlingdata.ThrottlingDataConf
import throttlingdata.actors.common.ImplicitActor

import scala.util.{Failure, Success}

object RegistryCleanSchedulerActor {
  sealed trait RegistryCleanSchedulerRequest
  case class WaitAndCall() extends RegistryCleanSchedulerRequest
}
class RegistryCleanSchedulerActor(secondsSchedulerRecall: Long) extends ImplicitActor {

  import RegistryCleanSchedulerActor._
  import RegistryNameByTokenActor._

  val millisSchedulerRecall: Long = 1000 * secondsSchedulerRecall

  override def receive: Receive = {

    case WaitAndCall() =>
      Thread.sleep(millisSchedulerRecall)
      val requester = self
      val tokenStorageActorPath =
        "/user/" + ThrottlingDataConf.registryNameByTokenActorName
      context.actorSelection(tokenStorageActorPath).resolveOne().onComplete {
        case Success(actorRef) =>
          logger.info("registryNameByTokenActorRef found")
          actorRef ! UnregisterExpiredToken(
            System.currentTimeMillis() - millisSchedulerRecall
          )
          requester ! WaitAndCall()
        case Failure(ex) =>
          logger.error("registryNameByTokenActorRef not found, Error: " + ex.getMessage)
          requester ! WaitAndCall()
      }
  }
}
