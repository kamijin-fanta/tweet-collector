import akka.actor.{ActorSystem, Props}

object main {
  def main(args: Array[String]): Unit = {
    implicit val actorSystem = ActorSystem()

    println("Start")

    val actor = actorSystem.actorOf(Props[TwitterStreamCollectActor])

    println("Pooling...")
    if (io.StdIn.readLine != null) {
      println("Shutdown")
      actorSystem.shutdown()
    }
  }
}
