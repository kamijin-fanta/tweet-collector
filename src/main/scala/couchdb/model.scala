package couchdb

import org.json4s.JsonAST.JValue

sealed trait CouchdbMessage

sealed trait SuccessResponse extends CouchdbMessage

sealed trait ErrorResponse extends CouchdbMessage

final case class AbortProcess() extends ErrorResponse

case class Sizes(
                  file: Double,
                  external: Double,
                  active: Double
                )

case class Other(
                  data_size: Double
                )

case class DatabaseInfo(
                         db_name: String,
                         update_seq: String,
                         sizes: Sizes,
                         purge_seq: Double,
                         other: Other,
                         doc_del_count: Double,
                         doc_count: Double,
                         disk_size: Double,
                         disk_format_version: Double,
                         data_size: Double,
                         compact_running: Boolean,
                         instance_start_time: String
                       ) extends SuccessResponse

case class DocumentCreated(
                            ok: Boolean,
                            id: String,
                            rev: String
                          ) extends SuccessResponse

case class DatabaseCreated(
                            ok: Boolean
                          ) extends SuccessResponse

case class InvalidJson(strJson: String) extends ErrorResponse

case class KnownResponse(strJson: String, debugMessage: String = "") extends ErrorResponse

trait ErrorWithReasonBase extends ErrorResponse {
  def error: String
  def reason: String
}
case class ErrorWithReason(error: String = "", reason: String = "") extends ErrorWithReasonBase
case class NotFound(error: String = "", reason: String = "") extends ErrorWithReasonBase
case class BadRequest(error: String = "", reason: String = "") extends ErrorWithReasonBase
case class Unauthorized(error: String = "", reason: String = "") extends ErrorWithReasonBase
case class Conflict(error: String = "", reason: String = "") extends ErrorWithReasonBase
case class InternalServerError(error: String = "", reason: String = "") extends ErrorWithReasonBase

