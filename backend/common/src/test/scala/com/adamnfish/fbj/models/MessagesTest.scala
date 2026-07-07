package com.adamnfish.fbj.models

import io.circe.parser.decode
import io.circe.syntax.*
import munit.FunSuite

import java.time.Instant

class MessagesTest extends FunSuite {
  def game: Game =
    Game(
      GameId("game-1"),
      GameName("world cup"),
      GameSettings(25, 4),
      locked = false,
      List(Player(PlayerId("alice-id"), PlayerName("alice"), List(TeamId("england")))),
      PlayerId("alice-id"),
      Competition(
        CompetitionId(1),
        CompetitionCode("WC"),
        CompetitionName("World Cup")
      )
    )

  def player: Player =
    Player(PlayerId("alice-id"), PlayerName("alice"), List(TeamId("england")))

  def competitionStats: CompetitionStats =
    CompetitionStats(
      CompetitionId(1),
      Instant.parse("2022-11-20T00:00:00Z"),
      Map(
        Team(TeamId("england"), TeamTLA("ENG"), TeamName("Eng", "England"), "https://example.com/england.png")
          -> TeamStats(Score(3, 1), Progress.Group(2), Status.Playing)
      )
    )

  test("Auth round trip") {
    val value = Auth(PlayerKey("secret-key"))
    assertEquals(decode[Auth](value.asJson.noSpaces), Right(value))
  }

  test("Request round trips, for every case") {
    val values: List[Request] = List(
      Request.Ping,
      Request.CreateGame(GameName("world cup"), GameSettings(3, 8), CompetitionCode("WC"), PlayerName("alice")),
      Request.JoinGame(GameId("game-1"), PlayerName("alice"), List(TeamId("england"))),
      Request.EditTeams(List(TeamId("england")), Auth(PlayerKey("secret-key"))),
      Request.LockGame(GameId("game-1"), Auth(PlayerKey("secret-key"))),
      Request.FetchGameInfo(GameId("game-1"))
    )
    values.foreach { value =>
      assertEquals(decode[Request](value.asJson.noSpaces), Right(value))
    }
  }

  test("Response round trips, for every case") {
    val values: List[Response] = List(
      Response.Ping(),
      Response.GameCreated(game, player, PlayerKey("secret-key"), competitionStats),
      Response.GameJoined(game, player, PlayerKey("secret-key"), competitionStats),
      Response.TeamsEdited(List(TeamId("england")), PlayerId("alice-id")),
      Response.GameLocked(),
      Response.GameInfoFetched(game, competitionStats)
    )
    values.foreach { value =>
      assertEquals(decode[Response](value.asJson.noSpaces), Right(value))
    }
  }
}
