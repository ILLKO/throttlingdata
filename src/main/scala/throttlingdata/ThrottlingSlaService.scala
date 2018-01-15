package throttlingdata

import throttlingdata.model.Sla
import throttlingdata.service.SlaService

import scala.concurrent.{ExecutionContext, Future}

class ThrottlingSlaService(implicit executionContext: ExecutionContext) extends SlaService {

  def getSlaByToken(token: String): Future[Sla] = {

    println(s"getSlaByToken - $token")

    token match {
      case "111" =>
        Future { Sla("username_111", 10) }
      case "222" =>
        Future { Sla("username_222", 2) }
      case "333" =>
        Future { Sla("username_333", 3) }
      case "444" =>
        Future { Sla("username_444", 1) }
      case "555" =>
        Future { Sla("username_555", 20) }
    }
  }
}
