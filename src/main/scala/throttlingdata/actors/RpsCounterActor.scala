package throttlingdata.actors

import throttlingdata.ThrottlingDataConf

import scala.collection.mutable

object RpsCounterActor {
  sealed trait RpsCounterActorRequest
  case class IsAllowedRequest() extends RpsCounterActorRequest

  sealed trait RpsCounterActorResponse
  case class IsAllowedResponse(result: Boolean) extends RpsCounterActorResponse
}
abstract class RpsCounterActor(maxRpsAllowed: Int) extends ImplicitActor {

  import RpsCounterActor._

  val DELTA_TIME: Long = 1000 * ThrottlingDataConf.secondsCheckSize

  var queue: mutable.Queue[Long] = mutable.Queue.empty

  def countLastSec(millis: Long): Int = {
    def cutOld(cut_millis: Long): Unit = {
      queue.get(0) match {
        case Some(value) =>
          if (value < cut_millis) {
            logger.info("countLastSec Some value " + value)
            queue.dequeue()
            cutOld(cut_millis)
          }
        case None =>
      }
    }
    logger.info("countLastSec millis " + millis)
    queue.enqueue(millis)
    cutOld(millis - DELTA_TIME)
    logger.info("countLastSec size " + queue.size)
    queue.size
  }

  def getPath: String

  override def receive: Receive = {

    // TODO 1/10 and add more
    case IsAllowedRequest() =>
      logger.info("IsAllowedRequest path " + getPath)
      val millis = System.currentTimeMillis()
      logger.info("IsAllowedRequest millis " + millis)
      logger.info("IsAllowedRequest maxRpsAllowed " + maxRpsAllowed)
      sender ! IsAllowedResponse(
        maxRpsAllowed >= countLastSec(millis)
      )
  }
}
class UnauthorizedRpsCounterActor(maxRpsAllowed: Int) extends RpsCounterActor(maxRpsAllowed) {

  def getPath: String = "unauthorized"
}
class UserRpsCounterActor(maxRpsAllowed: Int, user: String) extends RpsCounterActor(maxRpsAllowed) {

  def getPath: String = user
}
