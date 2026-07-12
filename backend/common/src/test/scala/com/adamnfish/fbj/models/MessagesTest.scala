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
      LockState.Auto,
      List(
        Player(
          PlayerId("alice-id"),
          PlayerName("alice"),
          List(TeamId("england"))
        )
      ),
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
        Team(
          TeamId("england"),
          TeamTLA("ENG"),
          TeamName("Eng", "England"),
          "https://example.com/england.png"
        )
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
      Request.CreateGame(
        GameName("world cup"),
        GameSettings(3, 8),
        CompetitionCode("WC"),
        PlayerName("alice")
      ),
      Request.JoinGame(
        GameId("game-1"),
        PlayerName("alice"),
        List(TeamId("england"))
      ),
      Request.EditTeams(
        GameId("game-1"),
        List(TeamId("england")),
        Auth(PlayerKey("secret-key"))
      ),
      Request.LockGame(GameId("game-1"), Auth(PlayerKey("secret-key"))),
      Request.UnlockGame(GameId("game-1"), Auth(PlayerKey("secret-key"))),
      Request.FetchGameInfo(GameId("game-1")),
      Request.FetchPlayerKeys(GameId("game-1"), Auth(PlayerKey("secret-key")))
    )
    values.foreach { value =>
      assertEquals(decode[Request](value.asJson.noSpaces), Right(value))
    }
  }

  test("Request cases decode from bare payloads, for every case") {
    // the operation in the URL selects the case, so request bodies hold just
    // that case's fields
    def assertDecodes[A <: Request: io.circe.Decoder](
        json: String,
        expected: Request
    ): Unit =
      assertEquals(
        decode[A](json).map(request => request: Request),
        Right(expected)
      )

    assertDecodes[Request.CreateGame](
      """{"gameName":{"name":"world cup"},"gameSettings":{"goalLimit":25,"teamCount":4},"competitionCode":{"code":"WC"},"playerName":{"name":"alice"}}""",
      Request.CreateGame(
        GameName("world cup"),
        GameSettings(25, 4),
        CompetitionCode("WC"),
        PlayerName("alice")
      )
    )
    assertDecodes[Request.JoinGame](
      """{"gameId":{"id":"game-1"},"playerName":{"name":"alice"},"teams":[{"id":"england"}]}""",
      Request.JoinGame(
        GameId("game-1"),
        PlayerName("alice"),
        List(TeamId("england"))
      )
    )
    assertDecodes[Request.EditTeams](
      """{"gameId":{"id":"game-1"},"teams":[{"id":"england"}],"auth":{"playerKey":{"key":"secret-key"}}}""",
      Request.EditTeams(
        GameId("game-1"),
        List(TeamId("england")),
        Auth(PlayerKey("secret-key"))
      )
    )
    assertDecodes[Request.LockGame](
      """{"gameId":{"id":"game-1"},"auth":{"playerKey":{"key":"secret-key"}}}""",
      Request.LockGame(GameId("game-1"), Auth(PlayerKey("secret-key")))
    )
    assertDecodes[Request.UnlockGame](
      """{"gameId":{"id":"game-1"},"auth":{"playerKey":{"key":"secret-key"}}}""",
      Request.UnlockGame(GameId("game-1"), Auth(PlayerKey("secret-key")))
    )
    assertDecodes[Request.FetchGameInfo](
      """{"gameId":{"id":"game-1"}}""",
      Request.FetchGameInfo(GameId("game-1"))
    )
    assertDecodes[Request.FetchPlayerKeys](
      """{"gameId":{"id":"game-1"},"auth":{"playerKey":{"key":"secret-key"}}}""",
      Request.FetchPlayerKeys(GameId("game-1"), Auth(PlayerKey("secret-key")))
    )
  }

  test("Response round trips, for every case") {
    val values: List[Response] = List(
      Response.Ping(),
      Response
        .GameCreated(game, player, PlayerKey("secret-key"), competitionStats),
      Response
        .GameJoined(game, player, PlayerKey("secret-key"), competitionStats),
      Response.TeamsEdited(List(TeamId("england")), PlayerId("alice-id")),
      Response.GameLocked(),
      Response.GameUnlocked(),
      Response.GameInfoFetched(game, competitionStats),
      Response.PlayerKeysFetched(
        Map(PlayerId("alice-id") -> PlayerKey("secret-key"))
      )
    )
    values.foreach { value =>
      assertEquals(decode[Response](value.asJson.noSpaces), Right(value))
    }
  }

  test("Errors round trips, for every case") {
    val values: List[Errors] = List(
      Errors.ValidationError("field", "message"),
      Errors.ValidationErrors("message", List("field" -> "message")),
      Errors.GameNotFound,
      Errors.CompetitionNotFound,
      Errors.Unauthorized,
      Errors.GameLocked,
      Errors.TeamSelectionTaken,
      Errors.PlayerNameTaken
    )
    values.foreach { value =>
      assertEquals(decode[Errors](value.asJson.noSpaces), Right(value))
    }
  }
}
