import akka.actor.Actor
import akka.actor.Actor.Receive
import com.danielasfregola.twitter4s.entities.Tweet
import com.danielasfregola.twitter4s.entities.streaming.{CommonStreamingMessage, UserStreamingMessage}
import com.danielasfregola.twitter4s.entities.streaming.common.StatusDeletionNotice
import com.github.nscala_time.time.Imports.{DateTime, DateTimeZone}
import couchdb.{CouchdbClientSync, CouchdbDatabaseSync}
import de.knutwalker.akka.typed.TypedActor
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JObject
import org.json4s.native.JsonMethods
import org.json4s.native.Serialization.write
import org.json4s.JsonDSL._

class UserStreamActor(couchdbDatabaseSync: CouchdbDatabaseSync) extends TypedActor.Of[UserStreamingMessage] {
  implicit val defaultFormats = DefaultFormats
  val db = couchdbDatabaseSync
  override def preStart(): Unit = {
    db.ifNotExistCreateDb()
  }

  override def typedReceive: TypedReceive = {
    case tweet: Tweet =>
      print("User: ")
      println(db.createById(tweet.id_str, write(tweet)))
    case statusDelete: StatusDeletionNotice =>
      val update = db.update(statusDelete.delete.status.id_str, raw => {
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
}

class PublicStreamActor(couchdbDatabaseSync: CouchdbDatabaseSync) extends TypedActor.Of[CommonStreamingMessage] {
  implicit val defaultFormats = DefaultFormats
  val db = couchdbDatabaseSync
  override def preStart(): Unit = {
    db.ifNotExistCreateDb()
  }
  override def typedReceive: TypedReceive = {
    case tweet: Tweet =>
      print("Public: ")
      db.createById(tweet.id_str, write(tweet)) match {
        case Right(x) =>
//          println(s"ID: ${tweet.id_str} Ref: ${x.rev}")
          println(s"${tweet.user.map(_.screen_name).getOrElse("")} ${tweet.text.replace("\n", "").take(50)}")
        case Left(err) =>
          println(err)
      }
  }
}