package throttlingdata.service

trait ThrottlingService {
  val graceRps: Int
  val slaService: SlaService

  def isRequestAllowed(token: Option[String]): Boolean
}
