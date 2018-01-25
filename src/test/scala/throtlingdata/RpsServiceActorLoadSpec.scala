package throtlingdata

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import throttlingdata.ThrottlingDataConf
import throttlingdata.actors.root.RpsServiceActor

import scala.concurrent.duration._

class RpsServiceActorLoadSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with Matchers with WordSpecLike with BeforeAndAfterAll {

  def this() = this(ActorSystem("throttling-data-actor-system-test-unauth"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  implicit val timeout = Timeout(5 seconds)
  implicit val executionContext = system.dispatcher

  import ServiceCall._
  import ServiceResult._

  "RpsServiceActor load test" must {

    val testProbe = TestProbe()

    val rpsServiceActorRef =
      TestActorRef(
        Props(new RpsServiceActor(
          ThrottlingDataConf.graceRps,
          new ThrottlingSlaServiceMocked()
        ))
      )
    val serviceCall =
      TestActorRef(
        new ServiceCall(system, testProbe.ref, rpsServiceActorRef)
      )
    val serviceResult =
      TestActorRef(
        new ServiceResult(system)
      )

    "check grace requests per second for all tokens without auth" in {

      serviceResult ! CleanResult()

      val timestamp = System.currentTimeMillis()

      println(s"Load test, start timestamp = $timestamp")

      // todo - load test + check 5 ms is average time per sec for many calls

      serviceResult ! ShowResult()
    }
  }
}