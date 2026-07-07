package com.adamnfish.fbj.models

import io.circe.parser.decode
import io.circe.syntax.*
import munit.FunSuite

class WrappersTest extends FunSuite {
  test("GameId round trip") {
    val value = GameId("game-1")
    assertEquals(decode[GameId](value.asJson.noSpaces), Right(value))
  }

  test("GameName round trip") {
    val value = GameName("world cup")
    assertEquals(decode[GameName](value.asJson.noSpaces), Right(value))
  }

  test("PlayerId round trip") {
    val value = PlayerId("player-1")
    assertEquals(decode[PlayerId](value.asJson.noSpaces), Right(value))
  }

  test("PlayerKey round trip") {
    val value = PlayerKey("secret-key")
    assertEquals(decode[PlayerKey](value.asJson.noSpaces), Right(value))
  }

  test("PlayerName round trip") {
    val value = PlayerName("bob")
    assertEquals(decode[PlayerName](value.asJson.noSpaces), Right(value))
  }

  test("TeamId round trip") {
    val value = TeamId("team-1")
    assertEquals(decode[TeamId](value.asJson.noSpaces), Right(value))
  }

  test("TeamTLA round trip") {
    val value = TeamTLA("ENG")
    assertEquals(decode[TeamTLA](value.asJson.noSpaces), Right(value))
  }

  test("TeamName round trip") {
    val value = TeamName("Eng", "England")
    assertEquals(decode[TeamName](value.asJson.noSpaces), Right(value))
  }

  test("CompetitionCode round trip") {
    val value = CompetitionCode("WC")
    assertEquals(decode[CompetitionCode](value.asJson.noSpaces), Right(value))
  }

  test("CompetitionName round trip") {
    val value = CompetitionName("World Cup")
    assertEquals(decode[CompetitionName](value.asJson.noSpaces), Right(value))
  }
}
