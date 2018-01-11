package throttlingdata.service

trait ThrottlingService {
  val graceRps: Int // configurable
  val slaService: SlaService // use mocks/stubs for testing

  def isRequestAllowed(token: Option[String]): Boolean
}
