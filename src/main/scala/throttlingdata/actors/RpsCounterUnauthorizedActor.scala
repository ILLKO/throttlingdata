package throttlingdata.actors

class RpsCounterUnauthorizedActor(maxRpsAllowed: Int) extends RpsCounterActor(maxRpsAllowed) {

  def getCounterName: String = "unauthorized"
}