package com.adamnfish.fbj

import com.adamnfish.fbj.models.*
import com.adamnfish.fbj.services.{
  ConcurrentModification,
  GameRecord,
  InMemoryPersistence,
  Versioned
}
import munit.FunSuite

import scala.util.{Failure, Try}

/** Integration tests for joinGame, editTeams, lockGame, unlockGame and
  * fetchPlayerKeys: happy paths, error cases, the lock-state matrix, and
  * conflict handling with a stubbed Persistence.
  */
class APIEndpointsTest extends FunSuite with APITestSupport {
  val bobsTeams: List[TeamId] =
    List("england", "brazil", "france", "japan").map(TeamId(_))
  val carolsTeams: List[TeamId] =
    List("england", "brazil", "france", "senegal").map(TeamId(_))

  // joinGame

  test("joinGame adds the player to the game") {
    val Ctx(api, persistence) = apiFixture()
    val created = createGame(api)
    val joined = joinGame(api, created.game.id, "bob", bobsTeams)
    assertEquals(joined.player.name, PlayerName("bob"))
    assertEquals(joined.player.selection, bobsTeams)
    assertEquals(
      joined.game.players.map(_.id),
      List(created.player.id, joined.player.id)
    )
    assertEquals(joined.competitionStats, preTournamentStats)
    val record = persistence.loadGame(created.game.id).get.get
    assertEquals(record.version, 2)
    assertEquals(record.value.game, joined.game)
    assertEquals(
      record.value.playerKeys(joined.player.id),
      joined.playerKey
    )
  }

  test("joinGame fails with GameNotFound for an unknown game") {
    val Ctx(api, _) = apiFixture()
    assertEquals(
      expectErrors(
        api.dispatch("join-game", joinBody(GameId("nope"), "bob", bobsTeams))
      ),
      Errors.GameNotFound
    )
  }

  test("joinGame rejects a taken player name") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    // "alice" is the creator's name; the check ignores case and whitespace
    assertEquals(
      expectErrors(
        api.dispatch(
          "join-game",
          joinBody(created.game.id, " Alice ", bobsTeams)
        )
      ),
      Errors.PlayerNameTaken
    )
  }

  test("joinGame rejects a selection of the wrong size") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    expectErrors(
      api.dispatch(
        "join-game",
        joinBody(created.game.id, "bob", bobsTeams.take(3))
      )
    ) match {
      case Errors.ValidationError(field, _) => assertEquals(field, "teams")
      case other => fail(s"expected ValidationError, got $other")
    }
  }

  test("joinGame rejects a selection with repeated teams") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    val teams = List("england", "england", "brazil", "france").map(TeamId(_))
    expectErrors(
      api.dispatch("join-game", joinBody(created.game.id, "bob", teams))
    ) match {
      case Errors.ValidationError(field, _) => assertEquals(field, "teams")
      case other => fail(s"expected ValidationError, got $other")
    }
  }

  test("joinGame rejects teams that are not in the competition") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    val teams = List("england", "brazil", "france", "narnia").map(TeamId(_))
    expectErrors(
      api.dispatch("join-game", joinBody(created.game.id, "bob", teams))
    ) match {
      case Errors.ValidationError(field, message) =>
        assertEquals(field, "teams")
        assert(message.contains("narnia"))
      case other => fail(s"expected ValidationError, got $other")
    }
  }

  test("joinGame accumulates field problems into ValidationErrors") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    expectErrors(
      api
        .dispatch("join-game", joinBody(created.game.id, "", bobsTeams.take(2)))
    ) match {
      case Errors.ValidationErrors(_, fields) =>
        assertEquals(fields.map(_._1), List("playerName", "teams"))
      case other => fail(s"expected ValidationErrors, got $other")
    }
  }

  test("joinGame rejects a taken team selection") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    joinGame(api, created.game.id, "bob", bobsTeams)
    assertEquals(
      expectErrors(
        api.dispatch("join-game", joinBody(created.game.id, "carol", bobsTeams))
      ),
      Errors.TeamSelectionTaken
    )
  }

  test("joinGame validates before writing anything") {
    var saveCount = 0
    val persistence = new InMemoryPersistence() {
      override def saveGame(
          game: Versioned[GameRecord]
      ): Try[Versioned[GameRecord]] = {
        saveCount += 1
        super.saveGame(game)
      }
    }
    val Ctx(api, _) = apiFixture(persistence = persistence)
    val created = createGame(api)
    val savesAfterCreate = saveCount
    assert(
      api
        .dispatch("join-game", joinBody(created.game.id, "", bobsTeams.take(2)))
        .isFailure
    )
    assertEquals(saveCount, savesAfterCreate)
  }

  // the lock-state matrix, for joins and edits

  test("joinGame succeeds on an Auto game before the tournament starts") {
    val Ctx(api, _) = apiFixture(stats = Some(preTournamentStats))
    val created = createGame(api)
    joinGame(api, created.game.id, "bob", bobsTeams)
  }

  test("joinGame fails on an Auto game once the tournament has started") {
    val Ctx(api, persistence) = apiFixture(stats = Some(preTournamentStats))
    val created = createGame(api)
    persistence.saveCompetitionStats(midGroupStats).get
    assertEquals(
      expectErrors(
        api.dispatch("join-game", joinBody(created.game.id, "bob", bobsTeams))
      ),
      Errors.GameLocked
    )
  }

  test("joinGame fails on a game the admin has locked") {
    val Ctx(api, _) = apiFixture(stats = Some(preTournamentStats))
    val created = createGame(api)
    api.dispatch("lock-game", authBody(created.game.id, created.playerKey)).get
    assertEquals(
      expectErrors(
        api.dispatch("join-game", joinBody(created.game.id, "bob", bobsTeams))
      ),
      Errors.GameLocked
    )
  }

  test(
    "joinGame succeeds mid-tournament on a game the admin has explicitly unlocked"
  ) {
    val Ctx(api, persistence) = apiFixture(stats = Some(preTournamentStats))
    val created = createGame(api)
    persistence.saveCompetitionStats(midGroupStats).get
    api
      .dispatch("unlock-game", authBody(created.game.id, created.playerKey))
      .get
    joinGame(api, created.game.id, "bob", bobsTeams)
  }

  test("editTeams fails on an Auto game once the tournament has started") {
    val Ctx(api, persistence) = apiFixture(stats = Some(preTournamentStats))
    val created = createGame(api)
    persistence.saveCompetitionStats(midGroupStats).get
    assertEquals(
      expectErrors(
        api.dispatch(
          "edit-teams",
          editTeamsBody(created.game.id, bobsTeams, created.playerKey)
        )
      ),
      Errors.GameLocked
    )
  }

  test("editTeams fails on a game the admin has locked") {
    val Ctx(api, _) = apiFixture(stats = Some(preTournamentStats))
    val created = createGame(api)
    api.dispatch("lock-game", authBody(created.game.id, created.playerKey)).get
    assertEquals(
      expectErrors(
        api.dispatch(
          "edit-teams",
          editTeamsBody(created.game.id, bobsTeams, created.playerKey)
        )
      ),
      Errors.GameLocked
    )
  }

  test(
    "editTeams succeeds mid-tournament on a game the admin has explicitly unlocked"
  ) {
    val Ctx(api, persistence) = apiFixture(stats = Some(preTournamentStats))
    val created = createGame(api)
    persistence.saveCompetitionStats(midGroupStats).get
    api
      .dispatch("unlock-game", authBody(created.game.id, created.playerKey))
      .get
    assertEquals(
      api.dispatch(
        "edit-teams",
        editTeamsBody(created.game.id, bobsTeams, created.playerKey)
      ),
      Try(Response.TeamsEdited(bobsTeams, created.player.id))
    )
  }

  // editTeams

  test("editTeams sets the caller's selection (the creator's first pick)") {
    val Ctx(api, persistence) = apiFixture()
    val created = createGame(api)
    assertEquals(
      api.dispatch(
        "edit-teams",
        editTeamsBody(created.game.id, bobsTeams, created.playerKey)
      ),
      Try(Response.TeamsEdited(bobsTeams, created.player.id))
    )
    val record = persistence.loadGame(created.game.id).get.get
    assertEquals(
      record.value.game.players.find(_.id == created.player.id).get.selection,
      bobsTeams
    )
  }

  test("editTeams fails with Unauthorized for an unknown player key") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    assertEquals(
      expectErrors(
        api.dispatch(
          "edit-teams",
          editTeamsBody(created.game.id, bobsTeams, PlayerKey("wrong"))
        )
      ),
      Errors.Unauthorized
    )
  }

  test("editTeams fails with GameNotFound for an unknown game") {
    val Ctx(api, _) = apiFixture()
    assertEquals(
      expectErrors(
        api.dispatch(
          "edit-teams",
          editTeamsBody(GameId("nope"), bobsTeams, PlayerKey("any"))
        )
      ),
      Errors.GameNotFound
    )
  }

  test("editTeams applies the selection validations") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    expectErrors(
      api.dispatch(
        "edit-teams",
        editTeamsBody(created.game.id, bobsTeams.take(2), created.playerKey)
      )
    ) match {
      case Errors.ValidationError(field, _) => assertEquals(field, "teams")
      case other => fail(s"expected ValidationError, got $other")
    }
  }

  test("editTeams rejects another player's selection") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    joinGame(api, created.game.id, "bob", bobsTeams)
    assertEquals(
      expectErrors(
        api.dispatch(
          "edit-teams",
          editTeamsBody(created.game.id, bobsTeams.reverse, created.playerKey)
        )
      ),
      Errors.TeamSelectionTaken
    )
  }

  test("editTeams accepts the caller re-submitting their own selection") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    val joined = joinGame(api, created.game.id, "bob", bobsTeams)
    assertEquals(
      api.dispatch(
        "edit-teams",
        editTeamsBody(created.game.id, bobsTeams.reverse, joined.playerKey)
      ),
      Try(Response.TeamsEdited(bobsTeams.reverse, joined.player.id))
    )
  }

  // lockGame / unlockGame

  test("lockGame sets the game's lock state to Locked") {
    val Ctx(api, persistence) = apiFixture()
    val created = createGame(api)
    assertEquals(
      api.dispatch("lock-game", authBody(created.game.id, created.playerKey)),
      Try(Response.GameLocked())
    )
    val record = persistence.loadGame(created.game.id).get.get
    assertEquals(record.value.game.lockState, LockState.Locked)
  }

  test("unlockGame sets the game's lock state to Unlocked") {
    val Ctx(api, persistence) = apiFixture()
    val created = createGame(api)
    assertEquals(
      api
        .dispatch("unlock-game", authBody(created.game.id, created.playerKey)),
      Try(Response.GameUnlocked())
    )
    val record = persistence.loadGame(created.game.id).get.get
    assertEquals(record.value.game.lockState, LockState.Unlocked)
  }

  test("lockGame and unlockGame are admin-only") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    val joined = joinGame(api, created.game.id, "bob", bobsTeams)
    List("lock-game", "unlock-game").foreach { operation =>
      assertEquals(
        expectErrors(
          api.dispatch(operation, authBody(created.game.id, joined.playerKey))
        ),
        Errors.Unauthorized,
        operation
      )
      assertEquals(
        expectErrors(
          api.dispatch(operation, authBody(created.game.id, PlayerKey("bad")))
        ),
        Errors.Unauthorized,
        operation
      )
    }
  }

  test("lockGame fails with GameNotFound for an unknown game") {
    val Ctx(api, _) = apiFixture()
    assertEquals(
      expectErrors(
        api.dispatch("lock-game", authBody(GameId("nope"), PlayerKey("any")))
      ),
      Errors.GameNotFound
    )
  }

  // fetchPlayerKeys

  test("fetchPlayerKeys returns every player's key to the admin") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    val joined = joinGame(api, created.game.id, "bob", bobsTeams)
    assertEquals(
      api.dispatch(
        "fetch-player-keys",
        authBody(created.game.id, created.playerKey)
      ),
      Try(
        Response.PlayerKeysFetched(
          Map(
            created.player.id -> created.playerKey,
            joined.player.id -> joined.playerKey
          )
        )
      )
    )
  }

  test("fetchPlayerKeys is admin-only") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    val joined = joinGame(api, created.game.id, "bob", bobsTeams)
    assertEquals(
      expectErrors(
        api.dispatch(
          "fetch-player-keys",
          authBody(created.game.id, joined.playerKey)
        )
      ),
      Errors.Unauthorized
    )
  }

  test("fetchPlayerKeys fails with GameNotFound for an unknown game") {
    val Ctx(api, _) = apiFixture()
    assertEquals(
      expectErrors(
        api.dispatch(
          "fetch-player-keys",
          authBody(GameId("nope"), PlayerKey("any"))
        )
      ),
      Errors.GameNotFound
    )
  }

  // conflict handling

  /** Fails a save with ConcurrentModification, optionally applying a competing
    * write first, so the retry sees the competitor's state.
    */
  class ContendedPersistence extends InMemoryPersistence {
    var competingWrite: Option[GameRecord => GameRecord] = None

    override def saveGame(
        game: Versioned[GameRecord]
    ): Try[Versioned[GameRecord]] =
      competingWrite match {
        case Some(update) =>
          competingWrite = None
          val current = super.loadGame(game.value.game.id).get.get
          super.saveGame(Versioned(update(current.value), current.version)).get
          Failure(new ConcurrentModification("injected conflict"))
        case None =>
          super.saveGame(game)
      }
  }

  test("a conflicted save is retried once and succeeds") {
    val persistence = new ContendedPersistence()
    val Ctx(api, _) = apiFixture(persistence = persistence)
    val created = createGame(api)
    // dave's competing join lands first; carol's save is retried against it
    persistence.competingWrite = Some { record =>
      val dave = Player(PlayerId("dave-id"), PlayerName("dave"), bobsTeams)
      record.copy(
        game = record.game.copy(players = record.game.players :+ dave),
        playerKeys = record.playerKeys + (dave.id -> PlayerKey("dave-key"))
      )
    }
    val joined = joinGame(api, created.game.id, "carol", carolsTeams)
    val record = persistence.loadGame(created.game.id).get.get
    assertEquals(
      record.value.game.players.map(_.name.name),
      List("alice", "dave", "carol")
    )
    assertEquals(record.version, 3)
    assertEquals(record.value.playerKeys(joined.player.id), joined.playerKey)
  }

  test("a conflicted save re-validates and yields the honest domain error") {
    val persistence = new ContendedPersistence()
    val Ctx(api, _) = apiFixture(persistence = persistence)
    val created = createGame(api)
    // dave takes carol's selection in the competing write
    persistence.competingWrite = Some { record =>
      val dave = Player(PlayerId("dave-id"), PlayerName("dave"), carolsTeams)
      record.copy(
        game = record.game.copy(players = record.game.players :+ dave),
        playerKeys = record.playerKeys + (dave.id -> PlayerKey("dave-key"))
      )
    }
    assertEquals(
      expectErrors(
        api
          .dispatch(
            "join-game",
            joinBody(created.game.id, "carol", carolsTeams)
          )
      ),
      Errors.TeamSelectionTaken
    )
  }
}
