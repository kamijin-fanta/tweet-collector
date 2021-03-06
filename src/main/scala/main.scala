import akka.actor.{ActorSystem, Props}
import akka.pattern.{Backoff, BackoffSupervisor}
import scala.concurrent.duration._

object main {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    println("Start")

    val childProps = Props(classOf[TwitterStreamCollectActor])

    val supervisor = BackoffSupervisor.props(
      Backoff.onStop(
        childProps,
        childName = "twitter-stream",
        minBackoff = 10.seconds,
        maxBackoff = 30.seconds,
        randomFactor = 0.2
      ))

    system.actorOf(supervisor, name = "supervisor")

    println("Pooling...")
    if (io.StdIn.readLine != null) {
      println("Shutdown")
      system.terminate()
    }
  }
}
