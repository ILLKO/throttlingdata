package throttlingdata.actors

class UserRpsCounterActor(maxRpsAllowed: Int, user: String) extends RpsCounterActor(maxRpsAllowed) {

  def getPath: String = user
}