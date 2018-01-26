package throtlingdata

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import throttlingdata.ThrottlingDataConf
import throttlingdata.actors.root.RpsServiceActor

import scala.concurrent.duration._

class RpsServiceActorAuthSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with Matchers with WordSpecLike with BeforeAndAfterAll {

  def this() = this(ActorSystem("throttling-data-actor-system-test-auth"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  implicit val timeout = Timeout(ThrottlingDataConf.requestTimeout seconds)
  implicit val executionContext = system.dispatcher

  import ServiceCall._
  import ServiceResult._

  "RpsServiceActor auth tests" must {

    val testProbe = TestProbe()

    val rpsServiceActorRef =
      TestActorRef(
        Props(new RpsServiceActor(
          ThrottlingDataConf.graceRps,
          new ThrottlingSlaServiceForAuthTest()
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

    "check one allowed request per second" in {

      serviceResult ! CleanResult()

      val token = "one_per_sec"
      val timestamp = System.currentTimeMillis()

      println(s"Test token = $token and start timestamp = $timestamp")

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

    "check two allowed request per second with same timestamp" in {

      serviceResult ! CleanResult()

      val token = "two_per_sec"
      val timestamp = System.currentTimeMillis()

      println(s"Test token = $token and start timestamp = $timestamp")

      serviceCall ! Request(token, timestamp)
      testProbe.expectMsg(Response(token, timestamp, true))
      testProbe.forward(serviceResult)

      Thread.sleep(100)

      serviceCall ! Request(token, timestamp + 1)
      testProbe.expectMsg(Response(token, timestamp + 1, true))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 1)
      testProbe.expectMsg(Response(token, timestamp + 1, true))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 500)
      testProbe.expectMsg(Response(token, timestamp + 500, false))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 1001)
      testProbe.expectMsg(Response(token, timestamp + 1001, false))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 1002)
      testProbe.expectMsg(Response(token, timestamp + 1002, true))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 1002)
      testProbe.expectMsg(Response(token, timestamp + 1002, true))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token, timestamp + 1002)
      testProbe.expectMsg(Response(token, timestamp + 1002, false))
      testProbe.forward(serviceResult)

      serviceResult ! ShowResult()
    }

    "check three allowed requests per second" in {

      serviceResult ! CleanResult()

      val token = "three_per_sec"
      val timestamp = System.currentTimeMillis()

      println(s"Test token = $token and start timestamp = $timestamp")

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

    "check additional allowed requests if all depleted in first 1/10 of second" in {

      serviceResult ! CleanResult()

      val token = "additional_per_sec"
      val timestamp = System.currentTimeMillis()

      println(s"Test token = $token and start timestamp = $timestamp")

      serviceCall ! Request(token, timestamp)
      testProbe.expectMsg(Response(token, timestamp, true))
      testProbe.forward(serviceResult)

      Thread.sleep(100)

      (1 to 30).foreach {
        i => {
          serviceCall ! Request(token, timestamp + i)
          testProbe.expectMsg(Response(token, timestamp + i, true))
          testProbe.forward(serviceResult)
        }
      }

      (990 + 1 to 990 + 3).foreach {
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

      (1050 + 1 to 1050 + 29).foreach {
        i => {
          serviceCall ! Request(token, timestamp + i)
          testProbe.expectMsg(Response(token, timestamp + i, true))
          testProbe.forward(serviceResult)
        }
      }

      serviceCall ! Request(token, timestamp + 1050 + 30)
      testProbe.expectMsg(Response(token, timestamp + 1050 + 30, false))
      testProbe.forward(serviceResult)

      (1990 + 2 to 1990 + 4).foreach {
        i => {
          serviceCall ! Request(token, timestamp + i)
          testProbe.expectMsg(Response(token, timestamp + i, true))
          testProbe.forward(serviceResult)
        }
      }

      serviceCall ! Request(token, timestamp + 1990 + 5)
      testProbe.expectMsg(Response(token, timestamp + 1990 + 5, false))
      testProbe.forward(serviceResult)

      serviceResult ! ShowResult()
    }

    "check summary request per second for many tokens for one user" in {

      serviceResult ! CleanResult()

      val token0 = "token_0"
      val token1 = "token_1"
      val token2 = "token_2"
      val token3 = "token_3"
      val timestamp = System.currentTimeMillis()

      println(s"Test tokens = [$token0,$token1,$token2,$token3] and start timestamp = $timestamp")

      serviceCall ! Request(token1, timestamp + 1)
      testProbe.expectMsg(Response(token1, timestamp + 1, true))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token2, timestamp + 2)
      testProbe.expectMsg(Response(token2, timestamp + 2, true))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token3, timestamp + 3)
      testProbe.expectMsg(Response(token3, timestamp + 3, true))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token0, timestamp + 3)
      testProbe.expectMsg(Response(token0, timestamp + 3, true))
      testProbe.forward(serviceResult)

      Thread.sleep(100)

      serviceCall ! Request(token0, timestamp + 10)
      testProbe.expectMsg(Response(token0, timestamp + 10, true))
      testProbe.forward(serviceResult)

      (11 to 14).foreach {
        i => {
          serviceCall ! Request(token1, timestamp + i)
          testProbe.expectMsg(Response(token1, timestamp + i, true))
          testProbe.forward(serviceResult)

          serviceCall ! Request(token2, timestamp + i)
          testProbe.expectMsg(Response(token2, timestamp + i, true))
          testProbe.forward(serviceResult)

          serviceCall ! Request(token3, timestamp + i)
          testProbe.expectMsg(Response(token3, timestamp + i, true))
          testProbe.forward(serviceResult)
        }
      }

      serviceCall ! Request(token1, timestamp + 777)
      testProbe.expectMsg(Response(token1, timestamp + 777, false))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token2, timestamp + 888)
      testProbe.expectMsg(Response(token2, timestamp + 888, false))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token3, timestamp + 999)
      testProbe.expectMsg(Response(token3, timestamp + 999, false))
      testProbe.forward(serviceResult)

      (1010 to 1010).foreach {
        i => {
          serviceCall ! Request(token1, timestamp + i)
          testProbe.expectMsg(Response(token1, timestamp + i, false))
          testProbe.forward(serviceResult)

          serviceCall ! Request(token2, timestamp + i)
          testProbe.expectMsg(Response(token2, timestamp + i, false))
          testProbe.forward(serviceResult)

          serviceCall ! Request(token3, timestamp + i)
          testProbe.expectMsg(Response(token3, timestamp + i, false))
          testProbe.forward(serviceResult)
        }
      }

      (1012 to 1015).foreach {
        i => {
          serviceCall ! Request(token1, timestamp + i)
          testProbe.expectMsg(Response(token1, timestamp + i, true))
          testProbe.forward(serviceResult)

          serviceCall ! Request(token2, timestamp + i)
          testProbe.expectMsg(Response(token2, timestamp + i, true))
          testProbe.forward(serviceResult)

          serviceCall ! Request(token3, timestamp + i)
          testProbe.expectMsg(Response(token3, timestamp + i, true))
          testProbe.forward(serviceResult)
        }
      }

      serviceCall ! Request(token0, timestamp + 1016)
      testProbe.expectMsg(Response(token0, timestamp + 1016, true))
      testProbe.forward(serviceResult)

      serviceCall ! Request(token0, timestamp + 1017)
      testProbe.expectMsg(Response(token0, timestamp + 1017, false))
      testProbe.forward(serviceResult)

      serviceResult ! ShowResult()
    }

    "check user has zero allowed requests" in {

      serviceResult ! CleanResult()

      val timestamp = System.currentTimeMillis()

      println(s"User with zero RPS, start timestamp = $timestamp")

      serviceCall ! Request("token_00", timestamp + 1)
      testProbe.expectMsg(Response("token_00", timestamp + 1, true))
      testProbe.forward(serviceResult)

      Thread.sleep(100)

      serviceCall ! Request("token_00", timestamp + 101)
      testProbe.expectMsg(Response("token_00", timestamp + 101, false))
      testProbe.forward(serviceResult)

      serviceResult ! ShowResult()
    }
  }
}