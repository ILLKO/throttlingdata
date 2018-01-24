package throttlingdata

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import throttlingdata.service.impl.{ThrottlingDataService, ThrottlingSlaService}

import scala.concurrent.ExecutionContext

trait ThrottlingDataHttpRestApi {

  def extractToken(httpRequest: HttpRequest): Option[String] =
    httpRequest.headers.filter(header => header.name() == "Authorization") match {
      case Nil => None
      case x :: _ =>
        Some(x.value())
    }

  implicit def executionContext: ExecutionContext
  implicit def system: ActorSystem
  implicit def timeout: Timeout

  lazy val throttlingService =
    new ThrottlingDataService(new ThrottlingSlaService())

  lazy val citiesRoute: Route =
    pathPrefix("throttlingdata" / "endpoint") {
      extractRequest { httpRequest =>
        val token = extractToken(httpRequest)
        get {
          complete("get with " + token + " " + throttlingService.isRequestAllowed(token))
        } ~
          post {
            complete("post with " + token + " " + throttlingService.isRequestAllowed(token))
          }
      }
    }
}
