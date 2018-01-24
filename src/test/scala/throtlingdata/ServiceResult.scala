package throtlingdata

import akka.actor.{Actor, ActorSystem}
import akka.util.Timeout

import scala.concurrent.duration._

object ServiceResult {
  case class ShowResult()
}
class ServiceResult(system: ActorSystem) extends Actor {

  import ServiceResult._
  import ServiceCall._

  implicit val timeout = Timeout(5 seconds)
  implicit val executionContext = system.dispatcher

  var results: List[Response] = Nil

  def receive = {

    case result: Response =>
      println("Result = " + result)
      results = result :: results

    case ShowResult() =>
      println("Results = " + results)
  }
}
