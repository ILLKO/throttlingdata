package throtlingdata

import throttlingdata.model.Sla
import throttlingdata.service.SlaService

import scala.concurrent.{ExecutionContext, Future}

class ThrottlingSlaServiceForLoadTest(implicit executionContext: ExecutionContext)
  extends SlaService {

  def getSlaByToken(token: String): Future[Sla] = {

    token match {

      case token: String =>

        if (token.split("_").head != "token")
          throw new IllegalArgumentException(s"No any user for token $token")

        val name = "login_for_" + token
        val count = token.split("_").tail.head.toInt

        Future {
          Sla(name, count)
        }

      case _  =>
        throw new IllegalArgumentException("No any user")
    }
  }
}
