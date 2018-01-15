package throttlingdata.actors.counter

class RpsCounterUserActor(maxRpsAllowed: Int, user: String) extends RpsCounterActor(maxRpsAllowed) {

  def getCounterName: String = user
}