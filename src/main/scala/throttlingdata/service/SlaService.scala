package throttlingdata.service

import throttlingdata.model.Sla

import scala.concurrent.Future

trait SlaService {
  def getSlaByToken(token: String): Future[Sla]
}
