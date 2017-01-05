import akka.actor.{Actor, ActorRef, Props}
import com.danielasfregola.twitter4s.entities.{AccessToken, ConsumerToken}
import com.danielasfregola.twitter4s.util.Configurations
import com.danielasfregola.twitter4s.{TwitterRestClient, TwitterStreamingClient}
import couchdb.CouchdbClientSync
import akka.actor.ActorDSL._
import akka.routing.RoundRobinPool
import com.danielasfregola.twitter4s.entities.enums.Language
import com.danielasfregola.twitter4s.entities.streaming.UserStreamingMessage

class TwitterStreamCollectActor extends Actor {
  var restClient: TwitterRestClient = null
  var streamingClient: TwitterStreamingClient = null
  var couchdbClient: CouchdbClientSync = null
  var publicStreamActor: ActorRef = null
  var userStreamActor: ActorRef = null

  override def preStart(): Unit = {
    val consumerToken = ConsumerToken(key = Configurations.consumerTokenKey, secret = Configurations.consumerTokenSecret)
    val accessToken = AccessToken(key = Configurations.accessTokenKey, secret = Configurations.accessTokenSecret)

    restClient = new TwitterRestClient(consumerToken, accessToken)
    streamingClient = new TwitterStreamingClient(consumerToken, accessToken)
    couchdbClient = CouchdbClientSync("http://172.24.0.10:5984/")

    val userStreamDb = couchdbClient.db("tweet-2")
    val publicStreamDb = couchdbClient.db("public-tweet-1")

    val userStreamRoundRobin = RoundRobinPool(5).props(Props(new UserStreamActor(userStreamDb)))
    userStreamActor = context.actorOf(userStreamRoundRobin)
    context.watch(userStreamActor)
    publicStreamActor = context.actorOf(Props(new PublicStreamActor(publicStreamDb)))
    context.watch(publicStreamActor)

    streamingClient.userEvents(replies = Some(true)) {
      case message => userStreamActor ! message
    }
    streamingClient.sampleStatuses(Seq(Language.Japanese)) {
      case message => publicStreamActor ! message
    }
  }

  override def postStop(): Unit = {
    couchdbClient.shutdown()
    context.stop(publicStreamActor)
    context.stop(userStreamActor)
    restClient.system.terminate()
    streamingClient.system.terminate()
  }

  override def receive: Receive = {
    case _ =>
  }

}
