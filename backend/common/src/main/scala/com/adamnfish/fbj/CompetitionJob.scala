package com.adamnfish.fbj

import com.adamnfish.fbj.models.CompetitionId
import com.adamnfish.fbj.services.{CompetitionData, Persistence}

import scala.util.Try

/** Fetches fresh stats for a competition and persists them as a new snapshot
  * (see plan/04-competition-job.md). Wrapped by the scheduled data-service
  * Lambda in production and triggered manually from the dev server.
  *
  * On failure the previous snapshot stays in place and the failure propagates
  * to the caller; retries, scheduling and alerting live in the infrastructure
  * around this.
  */
class CompetitionJob(
    persistence: Persistence,
    competitionData: CompetitionData
) {
  def fetchCompetition(competitionId: CompetitionId): Try[Unit] =
    for {
      competitionStats <- competitionData.fetchCompetitionStats(competitionId)
      _ <- persistence.saveCompetitionStats(competitionStats)
    } yield ()
}
