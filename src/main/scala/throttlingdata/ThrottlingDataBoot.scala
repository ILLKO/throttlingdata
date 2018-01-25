package throttlingdata

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout

import scala.concurrent.duration._

// TODO - describe all steps in each test
// TODO - make one fnc in tests for "serviceCall ! Request(.."

object ThrottlingDataBoot extends App with ThrottlingDataHttpRestApi {

  implicit val system = ActorSystem("throttling-data-actor-system")
  implicit val executionContext = system.dispatcher
  implicit val materializer = ActorMaterializer()
  implicit val timeout = Timeout(ThrottlingDataConf.requestTimeout seconds)

  val host = ThrottlingDataConf.host
  val port = ThrottlingDataConf.port

  Http().bindAndHandle(citiesRoute, host, port) map { binding =>
    println(s"Server bound to http:/${binding.localAddress}/")
  } recover {
    case exception =>
      println(s"Server could not bind to http://$host:$port",
              exception.getMessage)
  }
}
