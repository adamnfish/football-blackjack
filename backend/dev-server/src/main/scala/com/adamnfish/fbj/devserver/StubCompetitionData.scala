package com.adamnfish.fbj.devserver

import com.adamnfish.fbj.models.{CompetitionId, CompetitionStats}
import com.adamnfish.fbj.services.CompetitionData

import java.time.Instant
import scala.util.{Failure, Success, Try}

/** Serves the canned fixture stats for the selected tournament state, standing
  * in for the real football-data client (and, from phase 3, the
  * fixture-rewriting FixtureCompetitionData with its simulated clock).
  *
  * Fetched stats are stamped with the current time, so switching to any state
  * always produces the store's latest snapshot.
  */
class StubCompetitionData(competitionId: CompetitionId)
    extends CompetitionData {
  @volatile var state: TournamentState = TournamentState.PreTournament

  override def fetchCompetitionStats(
      requestedId: CompetitionId
  ): Try[CompetitionStats] =
    if (requestedId == competitionId)
      Success(DevFixtures.stats(state, competitionId, Instant.now()))
    else
      Failure(
        new Exception(
          s"no fixture data for competition ${requestedId.id}, expected ${competitionId.id}"
        )
      )
}
