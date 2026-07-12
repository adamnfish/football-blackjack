package com.adamnfish.fbj

import com.adamnfish.fbj.models.*
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

/** Property tests where they sharpen the point: selection uniqueness is a set
  * property, and the validation rules hold for generated inputs rather than
  * hand-picked examples.
  */
class APIPropertiesTest extends ScalaCheckSuite with APITestSupport {
  val takenSelection: List[TeamId] =
    List("england", "brazil", "france", "japan").map(TeamId(_))

  property("any permutation of a taken selection is rejected") {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    joinGame(api, created.game.id, "bob", takenSelection)
    forAll(Gen.oneOf(takenSelection.permutations.toList)) { permutation =>
      assertEquals(
        expectErrors(
          api.dispatch(
            "join-game",
            joinBody(created.game.id, "carol", permutation)
          )
        ),
        Errors.TeamSelectionTaken
      )
    }
  }

  property(
    "editTeams rejects any permutation of another player's selection"
  ) {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    joinGame(api, created.game.id, "bob", takenSelection)
    forAll(Gen.oneOf(takenSelection.permutations.toList)) { permutation =>
      assertEquals(
        expectErrors(
          api.dispatch(
            "edit-teams",
            editTeamsBody(created.game.id, permutation, created.playerKey)
          )
        ),
        Errors.TeamSelectionTaken
      )
    }
  }

  property("createGame rejects any non-positive goalLimit or teamCount") {
    forAll(Gen.chooseNum(-100, 0), Gen.chooseNum(-100, 0)) {
      (goalLimit, teamCount) =>
        val Ctx(api, _) = apiFixture()
        expectErrors(
          api.dispatch(
            "create-game",
            createGameBodyJson(goalLimit = goalLimit, teamCount = teamCount)
          )
        ) match {
          case Errors.ValidationErrors(_, fields) =>
            assertEquals(fields.map(_._1), List("goalLimit", "teamCount"))
          case other => fail(s"expected ValidationErrors, got $other")
        }
    }
  }

  property("createGame accepts any game with sensible generated settings") {
    val genName = Gen.alphaStr.suchThat(_.trim.nonEmpty)
    forAll(genName, Gen.choose(1, 100), Gen.choose(1, 8), genName) {
      (gameName, goalLimit, teamCount, playerName) =>
        val Ctx(api, _) = apiFixture()
        val result = api.dispatch(
          "create-game",
          createGameBodyJson(
            gameName = gameName,
            goalLimit = goalLimit,
            teamCount = teamCount,
            playerName = playerName
          )
        )
        assert(result.isSuccess, s"expected success, got $result")
    }
  }

  property(
    "joinGame rejects any selection that is not exactly teamCount teams"
  ) {
    val Ctx(api, _) = apiFixture()
    val created = createGame(api)
    val allTeams = competitionTeamIds.map(TeamId(_))
    val wrongSizes =
      Gen.oneOf((0 to allTeams.length).filterNot(_ == 4))
    forAll(wrongSizes) { size =>
      expectErrors(
        api.dispatch(
          "join-game",
          joinBody(created.game.id, "bob", allTeams.take(size))
        )
      ) match {
        case Errors.ValidationError(field, _) => assertEquals(field, "teams")
        case other => fail(s"expected ValidationError, got $other")
      }
    }
  }
}
