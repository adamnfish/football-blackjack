package com.adamnfish.fbj.footballdata

/*
 * Models for the football-data.org "matches" API response.
 */

case class MatchesResponse(
    competition: FootballDataCompetition,
    matches: List[FootballDataMatch]
)

case class FootballDataCompetition(
    id: Int,
    name: String,
    code: String
)

case class FootballDataMatch(
    id: Int,
    status: MatchStatus,
    matchday: Int,
    stage: Stage,
    // populated for group stage matches, None for knockout matches
    group: Option[String],
    homeTeam: FootballDataTeam,
    awayTeam: FootballDataTeam,
    score: MatchScore
)

case class FootballDataTeam(
    id: Int,
    name: String,
    shortName: String,
    // three letter abbreviation, e.g. "MEX" - readable team identifier
    tla: String,
    crest: String
)

case class MatchScore(
    winner: Option[MatchWinner],
    duration: MatchDuration,
    fullTime: ScoreLine,
    halfTime: ScoreLine
)

// goals scored by each side, `None` before/during a match
case class ScoreLine(
    home: Option[Int],
    away: Option[Int]
)

enum MatchStatus {
  case Scheduled
  case Timed
  case InPlay
  case Paused
  case Finished
  case Postponed
  case Suspended
  case Cancelled
}

// stages of the tournament, in progression order
enum Stage {
  case GroupStage
  case Last32
  case Last16
  case QuarterFinals
  case SemiFinals
  case ThirdPlace
  case Final
}

enum MatchWinner {
  case HomeTeam
  case AwayTeam
  case Draw
}

enum MatchDuration {
  case Regular
  case ExtraTime
  case PenaltyShootout
}
