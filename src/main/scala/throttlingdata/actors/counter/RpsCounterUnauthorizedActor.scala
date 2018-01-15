package throttlingdata.actors.counter

class RpsCounterUnauthorizedActor(maxRpsAllowed: Int) extends RpsCounterActor(maxRpsAllowed) {

  def getCounterName: String = "unauthorized"
}