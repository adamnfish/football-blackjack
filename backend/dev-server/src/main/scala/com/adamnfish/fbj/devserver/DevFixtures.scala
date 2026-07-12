package com.adamnfish.fbj.devserver

import com.adamnfish.fbj.models.*

import java.time.Instant

/** The tournament states the canned fixtures cover, standing in for the phase 3
  * simulated clock (see plan/06-dev-server.md).
  */
enum TournamentState {
  case PreTournament
  case MidGroup
  case Knockouts
  case Finished

  def id: String = this match {
    case PreTournament => "pre-tournament"
    case MidGroup      => "mid-group"
    case Knockouts     => "knockouts"
    case Finished      => "finished"
  }
}
object TournamentState {
  def fromId(id: String): Option[TournamentState] =
    values.find(_.id == id)
}

/** Canned CompetitionStats for each tournament state. */
object DevFixtures {
  val competition: Competition =
    Competition(
      CompetitionId(2000),
      CompetitionCode("WC"),
      CompetitionName("World Cup")
    )

  private def team(
      id: String,
      tla: String,
      short: String,
      long: String,
      crestId: Int
  ): Team =
    Team(
      TeamId(id),
      TeamTLA(tla),
      TeamName(short, long),
      s"https://crests.football-data.org/$crestId.svg"
    )

  val england: Team = team("england", "ENG", "England", "England", 770)
  val france: Team = team("france", "FRA", "France", "France", 773)
  val brazil: Team = team("brazil", "BRA", "Brazil", "Brazil", 764)
  val argentina: Team = team("argentina", "ARG", "Argentina", "Argentina", 762)
  val germany: Team = team("germany", "GER", "Germany", "Germany", 759)
  val spain: Team = team("spain", "ESP", "Spain", "Spain", 760)
  val japan: Team = team("japan", "JPN", "Japan", "Japan", 766)
  val senegal: Team = team("senegal", "SEN", "Senegal", "Senegal", 776)

  val teams: List[Team] =
    List(england, france, brazil, argentina, germany, spain, japan, senegal)

  def stats(
      state: TournamentState,
      competitionId: CompetitionId,
      timestamp: Instant
  ): CompetitionStats =
    CompetitionStats(competitionId, timestamp, teamStats(state))

  private def entry(
      team: Team,
      goalsFor: Int,
      goalsAgainst: Int,
      progress: Progress,
      status: Status
  ): (Team, TeamStats) =
    team -> TeamStats(Score(goalsFor, goalsAgainst), progress, status)

  private def teamStats(state: TournamentState): Map[Team, TeamStats] =
    state match {
      case TournamentState.PreTournament =>
        teams.map(entry(_, 0, 0, Progress.NotStarted, Status.Playing)).toMap
      case TournamentState.MidGroup =>
        Map(
          entry(england, 5, 1, Progress.Group(2), Status.Playing),
          entry(france, 4, 2, Progress.Group(2), Status.Playing),
          entry(brazil, 6, 0, Progress.Group(2), Status.Playing),
          entry(argentina, 3, 3, Progress.Group(2), Status.Playing),
          entry(germany, 2, 2, Progress.Group(2), Status.Playing),
          entry(spain, 7, 1, Progress.Group(2), Status.Playing),
          entry(japan, 2, 3, Progress.Group(1), Status.Playing),
          entry(senegal, 1, 4, Progress.Group(2), Status.Playing)
        )
      case TournamentState.Knockouts =>
        Map(
          entry(england, 9, 3, Progress.Knockout(8), Status.Playing),
          entry(france, 8, 4, Progress.Knockout(8), Status.Playing),
          entry(brazil, 10, 2, Progress.Knockout(4), Status.Playing),
          entry(argentina, 7, 5, Progress.Knockout(8), Status.Playing),
          entry(germany, 4, 4, Progress.Group(3), Status.Eliminated),
          entry(spain, 9, 3, Progress.Knockout(8), Status.Playing),
          entry(japan, 4, 6, Progress.Group(3), Status.Eliminated),
          entry(senegal, 2, 7, Progress.Group(3), Status.Eliminated)
        )
      case TournamentState.Finished =>
        Map(
          entry(brazil, 14, 3, Progress.TopFour(1), Status.Playing),
          entry(england, 12, 5, Progress.TopFour(2), Status.Eliminated),
          entry(france, 10, 6, Progress.TopFour(3), Status.Eliminated),
          entry(argentina, 9, 7, Progress.TopFour(4), Status.Eliminated),
          entry(spain, 9, 5, Progress.Knockout(8), Status.Eliminated),
          entry(germany, 4, 4, Progress.Group(3), Status.Eliminated),
          entry(japan, 4, 6, Progress.Group(3), Status.Eliminated),
          entry(senegal, 2, 7, Progress.Group(3), Status.Eliminated)
        )
    }
}
