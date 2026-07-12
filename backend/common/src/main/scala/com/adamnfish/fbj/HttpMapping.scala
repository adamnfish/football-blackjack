package com.adamnfish.fbj

import com.adamnfish.fbj.models.{ApiError, Errors, Response}
import io.circe.Json
import io.circe.syntax.*

import scala.util.{Failure, Success, Try}

/** The HTTP wire mapping for `POST /api/{operation}`, shared by the API Lambda
  * and the dev server so they cannot drift: operation extraction from the
  * request path, and `Try[Response]` to HTTP status + JSON body.
  */
object HttpMapping {
  case class HttpResponse(status: Int, body: String)

  val jsonContentType = "application/json"

  /** Extracts the operation from an API request path, e.g. "/api/create-game"
    * yields "create-game".
    */
  def operationFromPath(path: String): Option[String] =
    path.stripPrefix("/").split("/") match {
      case Array("api", operation) if operation.nonEmpty => Some(operation)
      case _                                             => None
    }

  def toHttpResponse(result: Try[Response]): HttpResponse =
    result match {
      case Success(response) =>
        HttpResponse(200, response.asJson.noSpaces)
      case Failure(ApiError(errors)) =>
        HttpResponse(statusFor(errors), errors.asJson.noSpaces)
      case Failure(_) =>
        HttpResponse(
          500,
          Json
            .obj("message" -> Json.fromString("internal server error"))
            .noSpaces
        )
    }

  def statusFor(errors: Errors): Int =
    errors match {
      case _: Errors.ValidationError | _: Errors.ValidationErrors => 400
      case Errors.Unauthorized                                    => 401
      case Errors.GameNotFound | Errors.CompetitionNotFound       => 404
      case Errors.GameLocked | Errors.TeamSelectionTaken |
          Errors.PlayerNameTaken =>
        409
    }
}
