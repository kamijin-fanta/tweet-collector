import akka.actor.Actor
import com.danielasfregola.twitter4s.entities.enums.Language
import com.danielasfregola.twitter4s.entities.streaming.common.StatusDeletionNotice
import com.danielasfregola.twitter4s.entities.streaming.{CommonStreamingMessage, UserStreamingMessage}
import com.danielasfregola.twitter4s.entities.{AccessToken, ConsumerToken, Tweet}
import com.danielasfregola.twitter4s.util.Configurations
import com.danielasfregola.twitter4s.{TwitterRestClient, TwitterStreamingClient}
import com.github.nscala_time.time.Imports.{DateTime, DateTimeZone}
import couchdb.{CouchdbClientSync, CouchdbDatabaseSync}
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods
import org.json4s.native.Serialization.write

class TwitterStreamCollectActor extends Actor {
  implicit val defaultFormats = DefaultFormats
  var restClient: TwitterRestClient = null
  var streamingClient: TwitterStreamingClient = null
  var couchdbClient: CouchdbClientSync = null
  var db: CouchdbDatabaseSync = null
  var dbSecond: CouchdbDatabaseSync = null
  var dbPublic: CouchdbDatabaseSync = null

  override def preStart(): Unit = {
    val consumerToken = ConsumerToken(key = Configurations.consumerTokenKey, secret = Configurations.consumerTokenSecret)
    val accessToken = AccessToken(key = Configurations.accessTokenKey, secret = Configurations.accessTokenSecret)

    restClient = new TwitterRestClient(consumerToken, accessToken)
    streamingClient = new TwitterStreamingClient(consumerToken, accessToken)
    couchdbClient = CouchdbClientSync("http://172.24.0.10:5984/")

    db = couchdbClient.db("tweet-1")
    db.ifNotExistCreateDb()

    dbSecond = couchdbClient.db("tweet-2")
    dbSecond.ifNotExistCreateDb()

    dbPublic = couchdbClient.db("public-tweet-1")
    dbPublic.ifNotExistCreateDb()

    val f = streamingClient.userEvents(replies = Some(true))(userEventsCollect)
    val f2 = streamingClient.sampleStatuses(Seq(Language.Japanese))(publicEventsCollect)
  }

  override def postStop(): Unit = {
    couchdbClient.shutdown()
  }

  override def receive: Receive = {
    case _ =>
  }

  def userEventsCollect: PartialFunction[UserStreamingMessage, Unit] = {
    case tweet: Tweet =>
      print("User: ")
      println(dbSecond.createById(tweet.id_str, write(tweet)))
    case statusDelete: StatusDeletionNotice =>
      val update = dbSecond.update(statusDelete.delete.status.id_str, raw => {
        val json = JsonMethods.parse(raw)
        json match {
          case jObj: JObject =>
            val nowDate = DateTime.now.withZone(DateTimeZone.UTC).toString("yyyy-MM-dd'T'HH:mm:ss'Z")
            Some(write(jObj ~ ("deleted_at" -> nowDate)))
          case x =>
            println("ParseError")
            None
        }
      })
      println(s"Delete! ${statusDelete.delete.status.id_str} ${update}")
  }

  def publicEventsCollect: PartialFunction[CommonStreamingMessage, Unit] = {
    case tweet: Tweet =>
      print("Public: ")
      dbPublic.createById(tweet.id_str, write(tweet)) match {
        case Right(x) =>
          println(s"ID: ${tweet.id_str} Ref: ${x.rev}")
          println(s"${tweet.user.map(_.screen_name).getOrElse("")} ${tweet.text.replace("\n", "").take(50)}")
      }
  }
}
