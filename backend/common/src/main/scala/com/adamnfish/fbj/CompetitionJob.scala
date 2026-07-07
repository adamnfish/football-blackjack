package com.adamnfish.fbj

import com.adamnfish.fbj.models.CompetitionId
import com.adamnfish.fbj.services.{CompetitionData, Persistence}

import scala.util.Try

class CompetitionJob(
    persistence: Persistence,
    competitionData: CompetitionData
) {
  def fetchCompetition(competitionId: CompetitionId): Try[Unit] =
    ???
}
