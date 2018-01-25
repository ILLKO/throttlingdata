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
          new ThrottlingSlaServiceForLoadTest()
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

    "check 5 ms is average time per sec for many calls" in {

      serviceResult ! CleanResult()

      val timestamp = 0//System.currentTimeMillis()

      println(s"Load test, start timestamp = $timestamp")

      val tokensSuffixAndRps = List(100, 200, 300, 400, 500)

      tokensSuffixAndRps.foreach {
        s => {
          serviceCall ! Request("token_" + s, timestamp)
          testProbe.expectMsg(Response("token_" + s, timestamp, true))
          testProbe.forward(serviceResult)
        }
      }

      Thread.sleep(100)

      val timestampBefore = System.currentTimeMillis()

      def callRightMore(millisDrift: Int, millisStep: Int, tokensMask: List[Int]): Unit = {
        (millisDrift + 1 to millisDrift + millisStep).foreach {
          i => {
            tokensMask.foreach {
              s => {
                serviceCall ! Request("token_" + s, timestamp + i)
                testProbe.expectMsg(Response("token_" + s, timestamp + i, true))
                testProbe.forward(serviceResult)
              }
            }
          }
        }
        tokensMask match {
          case Nil =>
          case h :: t =>
            callRightMore(millisDrift + millisStep, millisStep, t)
        }
      }
      callRightMore(0, 100, tokensSuffixAndRps)
      callRightMore(700, 10, List(tokensSuffixAndRps.head))

      tokensSuffixAndRps.foreach {
        s => {
          serviceCall ! Request("token_" + s, timestamp + 999)
          testProbe.expectMsg(Response("token_" + s, timestamp + 999, false))
          testProbe.forward(serviceResult)
        }
      }

      val timestampAfter = System.currentTimeMillis()

      val callCount = 500 + 400 + 300 + 200 + 110

      val averageMs =
        (timestampAfter - timestampBefore) / callCount

      val isAverageMsLessThenFive =
        averageMs < 5

      println(s"average Ms per request = $averageMs")
      println(s"is average Ms less then five = $isAverageMsLessThenFive")

      serviceResult ! ShowResult()

      assert(isAverageMsLessThenFive)
    }
  }
}