package throttlingdata

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import throttlingdata.actors.{InitializerActor, ResolverTokenActor}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

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

//  val slaService =
//    new ThrottlingSlaService()
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
