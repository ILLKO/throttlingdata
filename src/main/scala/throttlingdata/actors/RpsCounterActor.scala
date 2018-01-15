package throttlingdata.actors

import throttlingdata.ThrottlingDataConf

import scala.collection.mutable

object RpsCounterActor {
  sealed trait RpsCounterActorRequest
  case class IsAllowedRequest(millis: Long) extends RpsCounterActorRequest

  sealed trait RpsCounterActorResponse
  case class IsAllowedResponse(result: Boolean) extends RpsCounterActorResponse
}
abstract class RpsCounterActor(maxRpsAllowed: Int) extends ImplicitActor {

  import RpsCounterActor._

  val CHECK_TIME: Long = 1000 * ThrottlingDataConf.secondsCheckSize
  val DELTA_TIME: Long = 100 * ThrottlingDataConf.secondsCheckSize

  var checkQueue: mutable.Queue[Long] = mutable.Queue.empty
  var deltaQueue: mutable.Queue[Long] = mutable.Queue.empty
  var fixedTimestampOpt: Option[Long] = None

  def countLastSec(queue: mutable.Queue[Long],
                   perTime: Long,
                   millis: Long): Int = {

    def cutOld(cut_millis: Long): Unit = {
      queue.get(0) match {
        case Some(value) =>
          if (value < cut_millis) {
            queue.dequeue()
            cutOld(cut_millis)
          }
        case None =>
      }
    }
    queue.enqueue(millis)
    cutOld(millis - perTime)
    queue.size
  }

  def getCounterName: String

  override def receive: Receive = {

    case IsAllowedRequest(millis) =>

      logger.info(s"IsAllowedRequest for counter $getCounterName")
      logger.info(s"IsAllowedRequest millis = $millis")
      logger.info(s"IsAllowedRequest maxRpsAllowed = $maxRpsAllowed")

      val countLastPerDelta =
        countLastSec(deltaQueue, DELTA_TIME, millis)

      logger.info(s"IsAllowedRequest countLastPerDelta = $countLastPerDelta")

      if (maxRpsAllowed <= countLastPerDelta) {
        fixedTimestampOpt = deltaQueue.get(0)
      }
      val rpsAllowed = fixedTimestampOpt match {
        case None =>
          maxRpsAllowed
        case Some(fixedTimestamp) =>
          if (fixedTimestamp + CHECK_TIME >= millis) {
            maxRpsAllowed + (maxRpsAllowed / 10)
          } else {
            fixedTimestampOpt = None
            maxRpsAllowed
          }
      }

      logger.info(s"IsAllowedRequest rpsAllowed = $rpsAllowed")

      val countLastPerCheck =
        countLastSec(checkQueue, CHECK_TIME, millis)

      logger.info(s"IsAllowedRequest countLastPerCheck = $countLastPerCheck")

      sender ! IsAllowedResponse(
        rpsAllowed >= countLastPerCheck
      )
  }
}
