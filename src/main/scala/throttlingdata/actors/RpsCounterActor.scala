package throttlingdata.actors

import scala.collection.mutable

object RpsCounterActor {
  sealed trait RpsCounterActorRequest
  case class IsAllowedRequest() extends RpsCounterActorRequest

  sealed trait RpsCounterActorResponse
  case class IsAllowedResponse(result: Boolean) extends RpsCounterActorResponse
}
abstract class RpsCounterActor(maxRpsAllowed: Int) extends ImplicitActor {

  import RpsCounterActor._

  var queue: mutable.Queue[Int] = mutable.Queue.empty

  def countLastSec(millis: Int): Int = {
    logger.info("countLastSec millis " + millis)
    queue.get(0) match {
      case Some(value) =>
        logger.info("countLastSec Some value " + value)
        if (value < millis) {
          queue.dequeue()
          countLastSec(millis)
        } else {
          queue.enqueue(millis)
          logger.info("countLastSec Some size " + queue.size)
          queue.size
        }
      case None =>
        logger.info("countLastSec None")
        queue.enqueue(millis)
        logger.info("countLastSec None size " + queue.size)
        queue.size
    }
  }

  def getPath: String

  override def receive: Receive = {

    // TODO 1/10 and add more
    case IsAllowedRequest() =>
      logger.info("IsAllowedRequest path " + getPath)
      val millis =
        System.currentTimeMillis() % 1000
      logger.info("IsAllowedRequest millis " + millis)
      logger.info("IsAllowedRequest maxRpsAllowed " + maxRpsAllowed)
      sender ! IsAllowedResponse(
        maxRpsAllowed >= countLastSec(millis.asInstanceOf[Int])
      )
  }
}
class UnauthorizedRpsCounterActor(maxRpsAllowed: Int) extends RpsCounterActor(maxRpsAllowed) {

  def getPath: String = "unauthorized"
}
class UserRpsCounterActor(maxRpsAllowed: Int, user: String) extends RpsCounterActor(maxRpsAllowed) {

  def getPath: String = user
}
