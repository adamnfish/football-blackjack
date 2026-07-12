package com.adamnfish.fbj

import com.adamnfish.fbj.models.*
import com.adamnfish.fbj.services.InMemoryPersistence
import munit.FunSuite

import java.time.Instant
import scala.util.{Failure, Try}

/** Integration tests for dispatch and the phase 2 core endpoints: ping,
  * createGame, fetchGameInfo. The remaining endpoints are covered in
  * APIEndpointsTest.
  */
class APITest extends FunSuite with APITestSupport {
  test("ping answers a Ping response") {
    val Ctx(api, _) = apiFixture()
    assertEquals(api.dispatch("ping", ""), Try(Response.Ping()))
  }

  test("dispatching an unknown operation is a validation failure") {
    val Ctx(api, _) = apiFixture()
    expectErrors(api.dispatch("does-not-exist", "{}")) match {
      case Errors.ValidationError(field, _) => assertEquals(field, "operation")
      case other => fail(s"expected ValidationError, got $other")
    }
  }

  test("createGame creates a game with the creator as its admin") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    assertEquals(created.game.gameName, GameName("world cup"))
    assertEquals(created.game.gameSettings, GameSettings(25, 4))
    assertEquals(created.game.lockState, LockState.Auto)
    assertEquals(created.game.competition, competition)
    assertEquals(created.game.players, List(created.player))
    assertEquals(created.game.gameAdmin, created.player.id)
    assertEquals(created.player.name, PlayerName("alice"))
    assertEquals(created.competitionStats, preTournamentStats)
  }

  test("createGame's creator starts with an empty selection") {
    val Ctx(api, _) = apiFixture()
    assertEquals(createGame(api).player.selection, Nil)
  }

  test("createGame generates a short human-friendly join code") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    assert(
      created.game.id.id.matches("[a-z2-9]{6}"),
      s"expected a 6 character join code, got: ${created.game.id.id}"
    )
  }

  test("createGame generates UUID player ids and keys") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    val uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    assert(created.player.id.id.matches(uuid))
    assert(created.playerKey.key.matches(uuid))
  }

  test("createGame persists the game record with the creator's key") {
    val Ctx(api, persistence) = apiFixture()
    val created = createGame(api)
    val record = persistence.loadGame(created.game.id).get.get
    assertEquals(record.version, 1)
    assertEquals(record.value.game, created.game)
    assertEquals(
      record.value.playerKeys,
      Map(created.player.id -> created.playerKey)
    )
  }

  test("createGame rejects a single invalid field with ValidationError") {
    val Ctx(api, _) = apiFixture()
    val body =
      """{"gameName":{"name":"  "},"gameSettings":{"goalLimit":25,"teamCount":4},"competitionCode":{"code":"WC"},"playerName":{"name":"alice"}}"""
    expectErrors(api.dispatch("create-game", body)) match {
      case Errors.ValidationError(field, _) => assertEquals(field, "gameName")
      case other => fail(s"expected ValidationError, got $other")
    }
  }

  test("createGame accumulates several invalid fields into ValidationErrors") {
    val Ctx(api, _) = apiFixture()
    val body =
      """{"gameName":{"name":""},"gameSettings":{"goalLimit":0,"teamCount":0},"competitionCode":{"code":"WC"},"playerName":{"name":""}}"""
    expectErrors(api.dispatch("create-game", body)) match {
      case Errors.ValidationErrors(_, fields) =>
        assertEquals(
          fields.map(_._1),
          List("gameName", "playerName", "goalLimit", "teamCount")
        )
      case other => fail(s"expected ValidationErrors, got $other")
    }
  }

  test("createGame validates before writing anything") {
    var saveCount = 0
    val persistence = new InMemoryPersistence() {
      override def saveGame(
          game: services.Versioned[services.GameRecord]
      ): Try[services.Versioned[services.GameRecord]] = {
        saveCount += 1
        super.saveGame(game)
      }
    }
    persistence.saveCompetitionStats(preTournamentStats).get
    val api = new API(persistence, List(competition))
    val body =
      """{"gameName":{"name":""},"gameSettings":{"goalLimit":25,"teamCount":4},"competitionCode":{"code":"WC"},"playerName":{"name":"alice"}}"""
    assert(api.dispatch("create-game", body).isFailure)
    assertEquals(saveCount, 0)
  }

  test("createGame rejects a competition that is not configured") {
    val Ctx(api, _) = apiFixture()
    val body =
      """{"gameName":{"name":"world cup"},"gameSettings":{"goalLimit":25,"teamCount":4},"competitionCode":{"code":"NOPE"},"playerName":{"name":"alice"}}"""
    assertEquals(
      expectErrors(api.dispatch("create-game", body)),
      Errors.CompetitionNotFound
    )
  }

  test("createGame requires stats to exist in the store") {
    val Ctx(api, _) = apiFixture(stats = None)
    assertEquals(
      expectErrors(api.dispatch("create-game", createGameBody)),
      Errors.CompetitionNotFound
    )
  }

  test("createGame rejects a malformed body as a validation failure") {
    val Ctx(api, _) = apiFixture()
    expectErrors(api.dispatch("create-game", "not json")) match {
      case Errors.ValidationError(field, _) => assertEquals(field, "body")
      case other => fail(s"expected ValidationError, got $other")
    }
  }

  test("fetchGameInfo returns the game and the latest stats") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    val body = s"""{"gameId":{"id":"${created.game.id.id}"}}"""
    assertEquals(
      api.dispatch("fetch-game-info", body),
      Try(Response.GameInfoFetched(created.game, preTournamentStats))
    )
  }

  test("fetchGameInfo returns the latest stats snapshot") {
    val Ctx(api, persistence) = apiFixture()
    val created = createGame(api)
    val updated = preTournamentStats.copy(
      timestamp = Instant.parse("2022-11-02T00:00:00Z")
    )
    persistence.saveCompetitionStats(updated).get
    val body = s"""{"gameId":{"id":"${created.game.id.id}"}}"""
    assertEquals(
      api.dispatch("fetch-game-info", body),
      Try(Response.GameInfoFetched(created.game, updated))
    )
  }

  test("fetchGameInfo fails with GameNotFound for an unknown game") {
    val Ctx(api, _) = apiFixture()
    assertEquals(
      expectErrors(
        api.dispatch("fetch-game-info", """{"gameId":{"id":"nope"}}""")
      ),
      Errors.GameNotFound
    )
  }
}
