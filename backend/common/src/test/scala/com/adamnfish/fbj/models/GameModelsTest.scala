package com.adamnfish.fbj.models

import io.circe.parser.decode
import io.circe.syntax.*
import munit.FunSuite

import java.time.Instant

class GameModelsTest extends FunSuite {
  def team(id: String): Team =
    Team(
      TeamId(id),
      TeamTLA(id.take(3).toUpperCase),
      TeamName(id, id),
      s"https://example.com/$id.png"
    )

  def teamStats(
      goalsFor: Int,
      goalsAgainst: Int,
      progress: Progress,
      status: Status
  ): TeamStats =
    TeamStats(Score(goalsFor, goalsAgainst), progress, status)

  def player(id: String, teams: List[TeamId]): Player =
    Player(PlayerId(id), PlayerName(id), teams)

  def competition: Competition =
    Competition(
      CompetitionId(1),
      CompetitionCode("WC"),
      CompetitionName("World Cup")
    )

  def competitionStats: CompetitionStats =
    CompetitionStats(
      CompetitionId(1),
      Instant.parse("2022-11-20T00:00:00Z"),
      Map(
        team("england") -> teamStats(3, 1, Progress.Group(2), Status.Playing),
        team("brazil") -> teamStats(5, 0, Progress.Knockout(8), Status.Playing),
        team("scotland") -> teamStats(
          0,
          4,
          Progress.NotStarted,
          Status.Eliminated
        )
      )
    )

  def game: Game =
    Game(
      GameId("game-1"),
      GameName("world cup"),
      GameSettings(3, 8),
      LockState.Auto,
      List(player("adam", List(TeamId("england")))),
      PlayerId("adam"),
      competition
    )

  test("Score round trips through JSON") {
    val value = Score(2, 1)
    assertEquals(decode[Score](value.asJson.noSpaces), Right(value))
  }

  test("Progress round trips through JSON, for every case") {
    val values = List(
      Progress.NotStarted,
      Progress.Group(3),
      Progress.Knockout(16),
      Progress.TopFour(1)
    )
    values.foreach { value =>
      assertEquals(decode[Progress](value.asJson.noSpaces), Right(value))
    }
  }

  test("Status round trips through JSON, for every case") {
    val values = List(Status.Eliminated, Status.Playing)
    values.foreach { value =>
      assertEquals(decode[Status](value.asJson.noSpaces), Right(value))
    }
  }

  test("LockState round trips through JSON, for every case") {
    val values = List(LockState.Auto, LockState.Locked, LockState.Unlocked)
    values.foreach { value =>
      assertEquals(decode[LockState](value.asJson.noSpaces), Right(value))
    }
  }

  test("Team round trips through JSON") {
    val value = team("england")
    assertEquals(decode[Team](value.asJson.noSpaces), Right(value))
  }

  test("TeamStats round trips through JSON") {
    val value = teamStats(3, 1, Progress.Group(2), Status.Playing)
    assertEquals(decode[TeamStats](value.asJson.noSpaces), Right(value))
  }

  test("GameSettings round trips through JSON") {
    val value = GameSettings(3, 8)
    assertEquals(decode[GameSettings](value.asJson.noSpaces), Right(value))
  }

  test("Player round trips through JSON") {
    val value = player("adam", List(TeamId("england"), TeamId("brazil")))
    assertEquals(decode[Player](value.asJson.noSpaces), Right(value))
  }

  test("Competition round trips through JSON") {
    val value = competition
    assertEquals(decode[Competition](value.asJson.noSpaces), Right(value))
  }

  test(
    "CompetitionStats round trips through JSON, including its Map[Team, TeamStats]"
  ) {
    val value = competitionStats
    assertEquals(decode[CompetitionStats](value.asJson.noSpaces), Right(value))
  }

  test(
    "CompetitionStats JSON keys the teams map using Team's own JSON encoding"
  ) {
    val json = competitionStats.asJson
    val teamsObject = json.hcursor.downField("teams").focus.flatMap(_.asObject)
    assert(
      teamsObject.isDefined,
      "expected teams to be encoded as a JSON object"
    )
    val keys = teamsObject.get.keys.toList
    assert(
      keys.forall(key => decode[Team](key).isRight),
      s"expected all map keys to be Team JSON, got: $keys"
    )
  }

  test("Game round trips through JSON") {
    val value = game
    assertEquals(decode[Game](value.asJson.noSpaces), Right(value))
  }
}
