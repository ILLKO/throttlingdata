package throttlingdata.service.impl

import throttlingdata.model.Sla
import throttlingdata.service.SlaService

import scala.concurrent.{ExecutionContext, Future}

class ThrottlingSlaService(implicit executionContext: ExecutionContext) extends SlaService {

  def getSlaByToken(token: String): Future[Sla] = {
    throw new NotImplementedError()
  }
}
