package com.adamnfish.fbj.services

import com.adamnfish.fbj.models.*
import munit.FunSuite

import java.time.Instant
import scala.util.{Failure, Success}

/** Behavioural contract for every Persistence implementation. Concrete suites
  * provide a fresh implementation per test via `newPersistence`.
  */
abstract class PersistenceContractTests extends FunSuite {
  def newPersistence(): Persistence

  val persistence = FunFixture[Persistence](
    setup = _ => newPersistence(),
    teardown = _ => ()
  )

  def team(id: String): Team =
    Team(
      TeamId(id),
      TeamTLA(id.take(3).toUpperCase),
      TeamName(id, id),
      s"https://example.com/$id.png"
    )

  def gameRecord(gameId: String): GameRecord = {
    val playerId = PlayerId(s"$gameId-player")
    GameRecord(
      Game(
        GameId(gameId),
        GameName("world cup"),
        GameSettings(25, 4),
        LockState.Auto,
        List(Player(playerId, PlayerName("alice"), List(TeamId("england")))),
        playerId,
        Competition(
          CompetitionId(1),
          CompetitionCode("WC"),
          CompetitionName("World Cup")
        )
      ),
      Map(playerId -> PlayerKey(s"$gameId-player-key"))
    )
  }

  def competitionStats(
      competitionId: Int,
      timestamp: Instant
  ): CompetitionStats =
    CompetitionStats(
      CompetitionId(competitionId),
      timestamp,
      Map(
        team("england") -> TeamStats(
          Score(3, 1),
          Progress.Group(2),
          Status.Playing
        )
      )
    )

  persistence.test("loading a game that has never been saved returns None") {
    p =>
      assertEquals(p.loadGame(GameId("missing")), Success(None))
  }

  persistence.test("a created game round-trips through save and load") { p =>
    val record = gameRecord("game-1")
    val saved = p.saveGame(Versioned(record, 0)).get
    assertEquals(saved, Versioned(record, 1))
    assertEquals(p.loadGame(record.game.id), Success(Some(saved)))
  }

  persistence.test("an updated game round-trips through save and load") { p =>
    val record = gameRecord("game-1")
    val created = p.saveGame(Versioned(record, 0)).get
    val updated = record.copy(game =
      record.game.copy(gameName = GameName("world cup updated"))
    )
    val saved = p.saveGame(Versioned(updated, created.version)).get
    assertEquals(saved, Versioned(updated, 2))
    assertEquals(p.loadGame(record.game.id), Success(Some(saved)))
  }

  persistence.test("creating a game that already exists fails") { p =>
    val record = gameRecord("game-1")
    p.saveGame(Versioned(record, 0)).get
    p.saveGame(Versioned(record, 0)) match {
      case Failure(_: ConcurrentModification) => ()
      case other => fail(s"expected ConcurrentModification, got $other")
    }
  }

  persistence.test("saving over a newer version fails") { p =>
    val record = gameRecord("game-1")
    val created = p.saveGame(Versioned(record, 0)).get
    // another writer bumps the stored version to 2
    p.saveGame(Versioned(record, created.version)).get
    // a save based on the stale version 1 must fail
    p.saveGame(Versioned(record, created.version)) match {
      case Failure(_: ConcurrentModification) => ()
      case other => fail(s"expected ConcurrentModification, got $other")
    }
  }

  persistence.test("updating a game that does not exist fails") { p =>
    val record = gameRecord("game-1")
    p.saveGame(Versioned(record, 1)) match {
      case Failure(_: ConcurrentModification) => ()
      case other => fail(s"expected ConcurrentModification, got $other")
    }
  }

  persistence.test("a failed save leaves the stored game untouched") { p =>
    val record = gameRecord("game-1")
    val created = p.saveGame(Versioned(record, 0)).get
    val updated = record.copy(game =
      record.game.copy(gameName = GameName("world cup updated"))
    )
    assert(p.saveGame(Versioned(updated, 99)).isFailure)
    assertEquals(p.loadGame(record.game.id), Success(Some(created)))
  }

  persistence.test(
    "loading stats for a competition with none saved returns None"
  ) { p =>
    assertEquals(p.loadCompetitionStats(CompetitionId(1)), Success(None))
  }

  persistence.test("competition stats round-trip through save and load") { p =>
    val stats = competitionStats(1, Instant.parse("2022-11-20T00:00:00Z"))
    assertEquals(p.saveCompetitionStats(stats), Success(stats))
    assertEquals(p.loadCompetitionStats(stats.id), Success(Some(stats)))
  }

  persistence.test("loading stats returns the latest snapshot") { p =>
    val early = competitionStats(1, Instant.parse("2022-11-20T00:00:00Z"))
    val late = competitionStats(1, Instant.parse("2022-11-21T00:00:00Z"))
    p.saveCompetitionStats(early).get
    p.saveCompetitionStats(late).get
    assertEquals(p.loadCompetitionStats(early.id), Success(Some(late)))
  }

  persistence.test(
    "loading stats returns the latest snapshot regardless of save order"
  ) { p =>
    val early = competitionStats(1, Instant.parse("2022-11-20T00:00:00Z"))
    val late = competitionStats(1, Instant.parse("2022-11-21T00:00:00Z"))
    p.saveCompetitionStats(late).get
    p.saveCompetitionStats(early).get
    assertEquals(p.loadCompetitionStats(early.id), Success(Some(late)))
  }

  persistence.test("stats are partitioned by competition") { p =>
    val one = competitionStats(1, Instant.parse("2022-11-20T00:00:00Z"))
    val two = competitionStats(2, Instant.parse("2022-11-21T00:00:00Z"))
    p.saveCompetitionStats(one).get
    p.saveCompetitionStats(two).get
    assertEquals(p.loadCompetitionStats(one.id), Success(Some(one)))
    assertEquals(p.loadCompetitionStats(two.id), Success(Some(two)))
  }
}

class InMemoryPersistenceTest extends PersistenceContractTests {
  override def newPersistence(): Persistence = new InMemoryPersistence()
}
