package throtlingdata

import throttlingdata.model.Sla
import throttlingdata.service.SlaService

import scala.concurrent.{ExecutionContext, Future}

class ThrottlingSlaServiceForAuthTest(implicit executionContext: ExecutionContext)
  extends SlaService {

  def getSlaByToken(token: String): Future[Sla] = {

    println(s"call getSlaByToken for token = $token")

    token match {

      case "one_per_sec" =>
        val name = "username_one_per_sec"
        val count = 1
        println(s"founds Sla for token = $token " +
          s"with name = $name and allowed count per seconds $count")
        Future {
          Sla(name, count)
        }

      case "two_per_sec" =>
        val name = "username_two_per_sec"
        val count = 2
        println(s"founds Sla for token = $token " +
          s"with name = $name and allowed count per seconds $count")
        Future {
          Sla(name, count)
        }

      case "three_per_sec" =>
        val name = "username_three_per_sec"
        val count = 3
        println(s"founds Sla for token = $token " +
          s"with name = $name and allowed count per seconds $count")
        Future {
          Sla(name, count)
        }

      case "additional_per_sec" =>
        val name = "username_additional_per_sec"
        val count = 30
        println(s"founds Sla for token = $token " +
          s"with name = $name and allowed count per seconds $count")
        Future {
          Sla(name, count)
        }

      case "token_0" | "token_1" | "token_2" | "token_3" =>
        val name = "username_123"
        val count = 12
        println(s"founds Sla for token = $token " +
          s"with name = $name and allowed count per seconds $count")
        Future {
          Sla(name, count)
        }

      case "token_00" =>
        val name = "username_00"
        val count = 0
        println(s"founds Sla for token = $token " +
          s"with name = $name and allowed count per seconds $count")
        Future {
          Sla(name, count)
        }

      case _  =>
        throw new IllegalArgumentException("No any user")
    }
  }
}
