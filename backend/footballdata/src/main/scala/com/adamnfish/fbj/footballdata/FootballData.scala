package com.adamnfish.fbj.footballdata

import com.adamnfish.fbj.models.{CompetitionId, CompetitionStats}
import com.adamnfish.fbj.services.CompetitionData

import java.time.Instant
import scala.util.Try

class FootballData(apiKey: String) extends CompetitionData {
  val baseUrl = "https://api.football-data.org/v4"
  def matchesUrl(competition: String) =
    s"$baseUrl/competitions/$competition/matches"

  override def fetchCompetitionStats(
      competitionId: CompetitionId
  ): Try[CompetitionStats] = {
    // fetch football data
    // convert it to our CompetitionStats type
    ???
  }

  def convertDataToStats(
      matchesResponse: MatchesResponse,
      when: Instant
  ): CompetitionStats = {
    ???
  }
}
