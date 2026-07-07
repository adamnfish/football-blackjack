package com.adamnfish.fbj.services

import com.adamnfish.fbj.models.{CompetitionId, CompetitionStats}

import scala.util.Try

trait CompetitionData {
  def fetchCompetitionStats(competitionId: CompetitionId): Try[CompetitionStats]
}
