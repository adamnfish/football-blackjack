package com.adamnfish.fbj.footballdata

trait FootballData {}
object FootballData {
  val baseUrl = "https://api.football-data.org/v4"
  def matchesUrl(competition: String) =
    s"$baseUrl/competitions/$competition/matches"

  def apply(): FootballData = new FootballData {}
}
