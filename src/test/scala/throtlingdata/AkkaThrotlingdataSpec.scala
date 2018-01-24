package throtlingdata

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import throttlingdata.ThrottlingDataConf
import throttlingdata.actors.root.RpsServiceActor

import scala.concurrent.duration._

class AkkaThrotlingdataSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with Matchers with WordSpecLike with BeforeAndAfterAll {

  def this() = this(ActorSystem("throttling-data-actor-system-test"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  implicit val timeout = Timeout(5 seconds)
  implicit val executionContext = system.dispatcher

  import ServiceCall._
  import ServiceResult._

  "RpsServiceActor tests" must {

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

    "one allowed request per second" in {

      val token = "one_per_sec"
      val timestamp = System.currentTimeMillis()

      println(s"Test token = $token and timestamp = $timestamp")

      serviceCall ! Request(token, timestamp)
      testProbe.expectMsg(Response(token, timestamp, true))
      testProbe.forward(serviceResult)

      Thread.sleep(100)

      serviceCall ! Request(token, timestamp + 1)
      testProbe.expectMsg(Response(token, timestamp + 1, true))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 2)
      testProbe.expectMsg(Response(token, timestamp + 2, false))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 1011)
      testProbe.expectMsg(Response(token, timestamp + 1011, true))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 1012)
      testProbe.expectMsg(Response(token, timestamp + 1012, false))
      testProbe.forward(serviceResult)

      serviceResult ! ShowResult()
    }

    "threee allowed requests per second" in {

      val token = "three_per_sec"
      val timestamp = System.currentTimeMillis()

      println(s"Test token = $token and timestamp = $timestamp")

      serviceCall ! Request(token, timestamp)
      testProbe.expectMsg(Response(token, timestamp, true))
      testProbe.forward(serviceResult)

      Thread.sleep(100)

      serviceCall ! Request(token, timestamp + 1)
      testProbe.expectMsg(Response(token, timestamp + 1, true))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 2)
      testProbe.expectMsg(Response(token, timestamp + 2, true))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 888)
      testProbe.expectMsg(Response(token, timestamp + 888, true))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 999)
      testProbe.expectMsg(Response(token, timestamp + 999, false))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 1000)
      testProbe.expectMsg(Response(token, timestamp + 1000, false))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 1002)
      testProbe.expectMsg(Response(token, timestamp + 1002, true))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 1003)
      testProbe.expectMsg(Response(token, timestamp + 1003, true))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 1777)
      testProbe.expectMsg(Response(token, timestamp + 1777, false))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 1888)
      testProbe.expectMsg(Response(token, timestamp + 1888, false))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 1889)
      testProbe.expectMsg(Response(token, timestamp + 1889, true))
      testProbe.forward(serviceResult)

      serviceResult ! ShowResult()
    }

    "additional allowed requests if all depleted in first 1/10 of second" in {

      val token = "additional_per_sec"
      val timestamp = 0 //System.currentTimeMillis()

      println(s"Test token = $token and timestamp = $timestamp")

      serviceCall ! Request(token, timestamp)
      testProbe.expectMsg(Response(token, timestamp, true))
      testProbe.forward(serviceResult)

      Thread.sleep(100)

      (1 to 30).map {
        i => {
          serviceCall ! Request(token, timestamp + i)
          testProbe.expectMsg(Response(token, timestamp + i, true))
          testProbe.forward(serviceResult)
        }
      }

      (990 + 1 to 990 + 3).map {
        i => {
          serviceCall ! Request(token, timestamp + i)
          testProbe.expectMsg(Response(token, timestamp + i, true))
          testProbe.forward(serviceResult)
        }
      }

      serviceCall ! Request(token, timestamp + 999)
      testProbe.expectMsg(Response(token, timestamp + 999, false))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 1002)
      testProbe.expectMsg(Response(token, timestamp + 1002, false))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 1004)
      testProbe.expectMsg(Response(token, timestamp + 1004, false))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 1005)
      testProbe.expectMsg(Response(token, timestamp + 1005, true))
      testProbe.forward(serviceResult)

      (1050 + 1 to 1050 + 29).map {
        i => {
          serviceCall ! Request(token, timestamp + i)
          testProbe.expectMsg(Response(token, timestamp + i, true))
          testProbe.forward(serviceResult)
        }
      }

      serviceCall ! Request(token, timestamp + 1050 + 30)
      testProbe.expectMsg(Response(token, timestamp + 1050 + 30, false))
      testProbe.forward(serviceResult)

//      (1990 + 2 to 1990 + 4).map {
//        i => {
//          serviceCall ! Request(token, timestamp + i)
//          testProbe.expectMsg(Response(token, timestamp + i, true))
//          testProbe.forward(serviceResult)
//        }
//      }
//
//      serviceCall ! Request(token, timestamp + 1990 + 5)
//      testProbe.expectMsg(Response(token, timestamp + 1990 + 5, false))
//      testProbe.forward(serviceResult)

      serviceResult ! ShowResult()
    }

    // todo - many tokens for one user 1 11 111
    // todo - grace period for all tokens without auth
    // todo - 5 ms per sec check average time for many calls
    // todo - load test
  }
}