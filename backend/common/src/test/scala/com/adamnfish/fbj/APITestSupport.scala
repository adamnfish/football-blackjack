package com.adamnfish.fbj

import com.adamnfish.fbj.models.*
import com.adamnfish.fbj.services.InMemoryPersistence
import io.circe.Json
import io.circe.syntax.*
import munit.FunSuite

import java.time.Instant
import scala.util.{Failure, Try}

/** Shared fixtures for the API integration tests: an in-memory Persistence
  * seeded with competition stats, driven through `dispatch` with operation +
  * JSON body (see the test strategy in plan/00-overview.md).
  */
trait APITestSupport { self: FunSuite =>
  val competition: Competition =
    Competition(
      CompetitionId(1),
      CompetitionCode("WC"),
      CompetitionName("World Cup")
    )

  def team(id: String): Team =
    Team(
      TeamId(id),
      TeamTLA(id.take(3).toUpperCase),
      TeamName(id, id),
      s"https://example.com/$id.png"
    )

  val competitionTeamIds: List[String] =
    List("england", "brazil", "france", "japan", "senegal")

  /** Pre-tournament stats: nothing has kicked off yet. */
  def preTournamentStats: CompetitionStats =
    CompetitionStats(
      competition.id,
      Instant.parse("2022-11-01T00:00:00Z"),
      competitionTeamIds.map { name =>
        team(name) -> TeamStats(
          Score(0, 0),
          Progress.NotStarted,
          Status.Playing
        )
      }.toMap
    )

  /** Mid-group stats: the tournament has started, so `Auto` games are
    * effectively locked.
    */
  def midGroupStats: CompetitionStats = {
    val base = preTournamentStats
    base.copy(
      timestamp = Instant.parse("2022-11-25T00:00:00Z"),
      teams = base.teams.map {
        case (t, stats) if t.id == TeamId("england") =>
          t -> stats.copy(score = Score(3, 1), progress = Progress.Group(2))
        case other => other
      }
    )
  }

  case class Ctx(api: API, persistence: InMemoryPersistence)

  def apiFixture(
      stats: Option[CompetitionStats] = Some(preTournamentStats),
      persistence: InMemoryPersistence = new InMemoryPersistence()
  ): Ctx = {
    stats.foreach(persistence.saveCompetitionStats(_).get)
    Ctx(new API(persistence, List(competition)), persistence)
  }

  def expectErrors(result: Try[Response]): Errors =
    result match {
      case Failure(ApiError(errors)) => errors
      case other => fail(s"expected an ApiError failure, got $other")
    }

  // request bodies

  val createGameBody: String =
    createGameBodyJson()

  def createGameBodyJson(
      gameName: String = "world cup",
      goalLimit: Int = 25,
      teamCount: Int = 4,
      competitionCode: String = "WC",
      playerName: String = "alice"
  ): String =
    Json
      .obj(
        "gameName" -> GameName(gameName).asJson,
        "gameSettings" -> GameSettings(goalLimit, teamCount).asJson,
        "competitionCode" -> CompetitionCode(competitionCode).asJson,
        "playerName" -> PlayerName(playerName).asJson
      )
      .noSpaces

  def joinBody(
      gameId: GameId,
      playerName: String,
      teams: List[TeamId]
  ): String =
    Json
      .obj(
        "gameId" -> gameId.asJson,
        "playerName" -> PlayerName(playerName).asJson,
        "teams" -> teams.asJson
      )
      .noSpaces

  def editTeamsBody(
      gameId: GameId,
      teams: List[TeamId],
      playerKey: PlayerKey
  ): String =
    Json
      .obj(
        "gameId" -> gameId.asJson,
        "teams" -> teams.asJson,
        "auth" -> Auth(playerKey).asJson
      )
      .noSpaces

  /** Body for the gameId + auth requests: lock, unlock, fetch-player-keys. */
  def authBody(gameId: GameId, playerKey: PlayerKey): String =
    Json
      .obj(
        "gameId" -> gameId.asJson,
        "auth" -> Auth(playerKey).asJson
      )
      .noSpaces

  // driving the API

  def createGame(api: API): Response.GameCreated =
    api.dispatch("create-game", createGameBody).get match {
      case created: Response.GameCreated => created
      case other => fail(s"expected GameCreated, got $other")
    }

  def joinGame(
      api: API,
      gameId: GameId,
      playerName: String,
      teams: List[TeamId]
  ): Response.GameJoined =
    api.dispatch("join-game", joinBody(gameId, playerName, teams)).get match {
      case joined: Response.GameJoined => joined
      case other => fail(s"expected GameJoined, got $other")
    }
}
