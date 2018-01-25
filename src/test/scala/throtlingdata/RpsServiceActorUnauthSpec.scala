package throtlingdata

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import throttlingdata.ThrottlingDataConf
import throttlingdata.actors.root.RpsServiceActor

import scala.concurrent.duration._

class RpsServiceActorUnauthSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with Matchers with WordSpecLike with BeforeAndAfterAll {

  def this() = this(ActorSystem("throttling-data-actor-system-test-unauth"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  implicit val timeout = Timeout(5 seconds)
  implicit val executionContext = system.dispatcher

  import ServiceCall._
  import ServiceResult._

  "RpsServiceActor unauth test" must {

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

      println(s"RPS for all tokens without auth, start timestamp = $timestamp")

      (1 to 110).foreach {
        i => {
          serviceCall ! Request("token_unauth_"+i, timestamp + i)
          testProbe.expectMsg(Response("token_unauth_"+i, timestamp + i, true))
          testProbe.forward(serviceResult)
        }
      }

      List(111, 222, 333, 444, 555, 666, 777, 888, 999).foreach {
        i => {
          serviceCall ! Request("token_unauth_" + i, timestamp + i)
          testProbe.expectMsg(Response("token_unauth_" + i, timestamp + i, false))
          testProbe.forward(serviceResult)
        }
      }

      (1200 + 1 to 1200 + 110).foreach {
        i => {
          serviceCall ! Request("token_unauth_"+i, timestamp + i)
          testProbe.expectMsg(Response("token_unauth_"+i, timestamp + i, true))
          testProbe.forward(serviceResult)
        }
      }

      List(111, 222, 333, 444, 555, 666, 777, 888, 999).map(_ + 1200).foreach {
        i => {
          serviceCall ! Request("token_unauth_" + i, timestamp + i)
          testProbe.expectMsg(Response("token_unauth_" + i, timestamp + i, false))
          testProbe.forward(serviceResult)
        }
      }

      serviceResult ! ShowResult()
    }
  }
}