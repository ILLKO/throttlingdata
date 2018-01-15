package throttlingdata.actors

class UnauthorizedRpsCounterActor(maxRpsAllowed: Int) extends RpsCounterActor(maxRpsAllowed) {

  def getPath: String = "unauthorized"
}