package couchdb

import org.http4s._
import org.http4s.client.blaze.PooledHttp1Client
import org.json4s.JsonAST.JNothing
import org.json4s.native.{JsonMethods, Serialization}
import org.json4s.reflect.{Reflector, ScalaType}
import org.json4s.{DefaultFormats, Extraction, Formats, JValue, MappingException}

import scala.reflect.Manifest
import scalaj.http.{Http, HttpResponse}

case class CouchdbConfig(url: String)



case class CouchdbClientSync(config: CouchdbConfig) {
  val baseUri = Uri.unsafeFromString(config.url)
  val httpClient = PooledHttp1Client()

  def db(databaseName: String) = {
    CouchdbDatabaseSync(this, databaseName)
  }

  def shutdown() = {
    httpClient.shutdownNow()
  }
}

object CouchdbClientSync {
  def apply(url: String): CouchdbClientSync = {
    val config = CouchdbConfig(
      url = url
    )
    CouchdbClientSync(config)
  }
}

import org.json4s.Extraction.extractOpt
case class CouchdbDatabaseSync(client: CouchdbClientSync, databaseName: String) {
  implicit val defaultFormats = DefaultFormats

  def extractOptByType[A](scalaType: ScalaType)(json: JValue)(implicit formats: Formats, mf: Manifest[A]): Option[A] =
    try {
      Option(Extraction.extract(json, scalaType).asInstanceOf[A])
    } catch {
      case _: MappingException => None
    }
  def jsonExtractOpt[A](jValue: JValue)(implicit formats: Formats, mf: Manifest[A]): Option[A] = {
    mf match {
      case x if x == manifest[String] => Some(Serialization.write(jValue).asInstanceOf[A])
      case _ => extractOptByType[A](Reflector.scalaTypeOf[A])(jValue)
    }
  }
  def responseParser[A](httpResponse: HttpResponse[String])(implicit formats: Formats, mf: Manifest[A]): Either[ErrorResponse, A] = {
    val responseBody = httpResponse.body
    val json = if (responseBody == "") Some(JNothing ) else JsonMethods.parseOpt(responseBody)
//    println(s"### responseParser ${httpResponse.code} ${json} ${responseBody}")
    json.flatMap(jsonExtractOpt[A](_)) match {
      case Some(x) => Right(x)
      case None =>

    }
    val a: Either[ErrorResponse, A] = httpResponse.code match {
      case 200 | 201 =>
        json.flatMap(jsonExtractOpt[A](_)) match {
          case Some(success) => Right(success)
          case None => Left(InvalidJson(responseBody))
        }
      case 400 => Left(json.flatMap(extractOpt[BadRequest](_)).getOrElse(BadRequest()))
      case 401 => Left(json.flatMap(extractOpt[Unauthorized](_)).getOrElse(Unauthorized()))
      case 404 => Left(json.flatMap(extractOpt[NotFound](_)).getOrElse(NotFound()))
      case 409 => Left(json.flatMap(extractOpt[Conflict](_)).getOrElse(Conflict()))
      case 500 => Left(json.flatMap(extractOpt[InternalServerError](_)).getOrElse(InternalServerError()))
      case _ => Left(KnownResponse(responseBody, httpResponse.toString))
    }
    a
  }


  ///////////////// Database
  val databaseHttp = Http((client.baseUri / databaseName).renderString)
    .header("content-type", "application/json")

  def info() = {
    responseParser[DatabaseInfo](databaseHttp.method("GET").asString)
  }
  def existDb() = {
    responseParser[String](databaseHttp.method("HEAD").asString)
  }
  def createDb() = {
    responseParser[DatabaseCreated](databaseHttp.method("PUT").asString)
  }
  def ifNotExistCreateDb() = {
//    println(s"########### ${existDb()}")
    existDb() match {
     case Right(x) => Right("OK")
     case x @ Left(NotFound(_, _)) => createDb()
     case x @ Left(_) => x
    }
  }

  ///////////////// Document
  val documentHttp = (id: String) => Http((client.baseUri / databaseName / id).renderString)
    .header("content-type", "application/json")
  def create(jsonStr: String) = {
    responseParser[DocumentCreated](databaseHttp.method("POST").postData(jsonStr).asString)
  }
  def createById(id: String, jsonStr: String, newEdits: Boolean = true) = {
//    println(s"CreateById / ${(client.baseUri / databaseName / id).renderString} ")
    val req = documentHttp(id)
      .param("new_edits", if (newEdits) "true" else "false")
      .postData(jsonStr)
      .method("PUT")
      .asString
    responseParser[DocumentCreated](req)
  }
  def getById(id: String) = {
    responseParser[String](documentHttp(id).method("GET").asString)
  }
  def update(id: String, editor: String => Option[String], conflictRetry: Int = 5) = {
    def updateDocument(retry: Int): Either[ErrorResponse, DocumentCreated] = {
      getById(id) match {
        case Right(json) =>
          editor(json) match {
            case Some(newDoc) =>
              createById(id, newDoc) match {
                case created @ Right(_) => created
                case conflict @ Left(Conflict(_, _)) =>
                  if (conflictRetry > 0) updateDocument(conflictRetry - 1)
                  else conflict
                case x => x
              }
            case None => Left(AbortProcess())
          }
        case Left(err) => Left(err)
      }
    }
    updateDocument(conflictRetry)
  }
//  def createOrUpdate(id: String)
}
