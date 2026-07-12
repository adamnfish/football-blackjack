package com.adamnfish.fbj

import com.adamnfish.fbj.HttpMapping.HttpResponse
import com.adamnfish.fbj.models.{ApiError, Errors, Response}
import io.circe.parser.decode
import munit.FunSuite

import scala.util.{Failure, Success}

class HttpMappingTest extends FunSuite {
  test("operationFromPath extracts the operation from an API path") {
    assertEquals(
      HttpMapping.operationFromPath("/api/create-game"),
      Some("create-game")
    )
    assertEquals(HttpMapping.operationFromPath("/api/ping"), Some("ping"))
  }

  test("operationFromPath rejects paths that are not API operations") {
    assertEquals(HttpMapping.operationFromPath("/api/"), None)
    assertEquals(HttpMapping.operationFromPath("/api"), None)
    assertEquals(HttpMapping.operationFromPath("/other/ping"), None)
    assertEquals(HttpMapping.operationFromPath("/api/a/b"), None)
    assertEquals(HttpMapping.operationFromPath("/"), None)
  }

  test("a successful response is a 200 with the circe-encoded Response") {
    val HttpResponse(status, body) =
      HttpMapping.toHttpResponse(Success(Response.Ping()))
    assertEquals(status, 200)
    assertEquals(decode[Response](body), Right(Response.Ping()))
  }

  test("a domain error is its mapped status with the circe-encoded Errors") {
    val HttpResponse(status, body) =
      HttpMapping.toHttpResponse(Failure(ApiError(Errors.GameNotFound)))
    assertEquals(status, 404)
    assertEquals(decode[Errors](body), Right(Errors.GameNotFound))
  }

  test("an unexpected failure is a 500 without leaking the exception") {
    val secret = "connection to 10.0.0.1 refused"
    val HttpResponse(status, body) =
      HttpMapping.toHttpResponse(Failure(new RuntimeException(secret)))
    assertEquals(status, 500)
    assert(!body.contains(secret))
  }

  test("every domain error maps to the intended HTTP status") {
    val expected = Map[Errors, Int](
      Errors.ValidationError("field", "message") -> 400,
      Errors.ValidationErrors("message", List("f" -> "m")) -> 400,
      Errors.Unauthorized -> 401,
      Errors.GameNotFound -> 404,
      Errors.CompetitionNotFound -> 404,
      Errors.GameLocked -> 409,
      Errors.TeamSelectionTaken -> 409,
      Errors.PlayerNameTaken -> 409
    )
    expected.foreach { case (errors, status) =>
      assertEquals(HttpMapping.statusFor(errors), status, errors.toString)
    }
  }
}
