package com.adamnfish.fbj.devserver

import com.adamnfish.fbj.API
import com.adamnfish.fbj.models.*
import munit.FunSuite

import java.time.Instant

class DevFixturesTest extends FunSuite {
  val when: Instant = Instant.parse("2026-06-11T12:00:00Z")

  def statsFor(state: TournamentState): CompetitionStats =
    DevFixtures.stats(state, DevFixtures.competition.id, when)

  test("stats carry the requested competition id and timestamp") {
    TournamentState.values.foreach { state =>
      val stats = statsFor(state)
      assertEquals(stats.id, DevFixtures.competition.id)
      assertEquals(stats.timestamp, when)
    }
  }

  test("every state covers the same teams") {
    TournamentState.values.foreach { state =>
      assertEquals(
        statsFor(state).teams.keySet,
        DevFixtures.teams.toSet,
        state.id
      )
    }
  }

  test("only the pre-tournament state shows the competition as not started") {
    assert(!API.competitionStarted(statsFor(TournamentState.PreTournament)))
    List(
      TournamentState.MidGroup,
      TournamentState.Knockouts,
      TournamentState.Finished
    ).foreach { state =>
      assert(API.competitionStarted(statsFor(state)), state.id)
    }
  }

  test("the knockouts state has knockout teams and eliminated teams") {
    val teamStats = statsFor(TournamentState.Knockouts).teams.values.toList
    assert(teamStats.exists {
      case TeamStats(_, Progress.Knockout(_), Status.Playing) => true
      case _                                                  => false
    })
    assert(teamStats.exists(_.status == Status.Eliminated))
  }

  test("the finished state has each top-four rank exactly once") {
    val ranks = statsFor(TournamentState.Finished).teams.values.collect {
      case TeamStats(_, Progress.TopFour(rank), _) => rank
    }.toList
    assertEquals(ranks.sorted, List(1, 2, 3, 4))
  }

  test("TournamentState ids round trip") {
    TournamentState.values.foreach { state =>
      assertEquals(TournamentState.fromId(state.id), Some(state))
    }
    assertEquals(TournamentState.fromId("nope"), None)
  }
}
