import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.model.StatusCode
import akka.routing.RoundRobinPool
import com.danielasfregola.twitter4s.entities.enums.Language
import com.danielasfregola.twitter4s.entities.{AccessToken, ConsumerToken}
import com.danielasfregola.twitter4s.exceptions.TwitterException
import com.danielasfregola.twitter4s.http.clients.streaming.TwitterStream
import com.danielasfregola.twitter4s.util.Configurations
import com.danielasfregola.twitter4s.{TwitterRestClient, TwitterStreamingClient}
import couchdb.CouchdbClientSync

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

class TwitterStreamCollectActor extends Actor {
  var terminateFlag = false
  var restClient: TwitterRestClient = null
  var streamingClient: TwitterStreamingClient = null
  var couchdbClient: CouchdbClientSync = null
  var publicStreamActor: ActorRef = null
  var userStreamActor: ActorRef = null

  override def preStart(): Unit = {
    terminateFlag = false
    val consumerToken = ConsumerToken(key = Configurations.consumerTokenKey, secret = Configurations.consumerTokenSecret)
    val accessToken = AccessToken(key = Configurations.accessTokenKey, secret = Configurations.accessTokenSecret)

//    restClient = new TwitterRestClient(consumerToken, accessToken)(context.system)
    streamingClient = new TwitterStreamingClient(consumerToken, accessToken)(context.system)
    couchdbClient = CouchdbClientSync("http://172.24.0.10:5984/")

    val userStreamDb = couchdbClient.db("tweet-2")
    val publicStreamDb = couchdbClient.db("public-tweet-1")

    val userStreamRoundRobin = RoundRobinPool(5).props(Props(new UserStreamActor(userStreamDb)))
    userStreamActor = context.actorOf(userStreamRoundRobin, "userStreamRoundRobin")
    publicStreamActor = context.actorOf(Props(new PublicStreamActor(publicStreamDb)), "PublicStreamActor")

    val restart = (t: Try[TwitterStream]) => {
      println("Complete!!!!!!!!!!!!!")
      println(t)

      t match {
        case Success(twitterStream) => {
        }
        case Failure(exception) => exception match {
          case ex: TwitterException if ex.code == StatusCode.int2StatusCode(420) => {
            println("too many request")
            Thread.sleep(1000 * 20)

            if (!terminateFlag) {
              terminateFlag = true
              context.stop(self)
            }
          }
        }
      }
    }
    streamingClient.userEvents(replies = Some(true)) {
      case message => userStreamActor ! message
    } onComplete(restart)

    streamingClient.sampleStatuses(Seq(Language.Japanese)) {
      case message => publicStreamActor ! message
    } onComplete(restart)
  }

  override def postStop(): Unit = {
    couchdbClient.shutdown()
    context.stop(publicStreamActor)
    context.stop(userStreamActor)

//    restClient.system.terminate()
//    streamingClient.system.terminate()
  }

  override def receive: Receive = {
    case _ =>
  }

}
