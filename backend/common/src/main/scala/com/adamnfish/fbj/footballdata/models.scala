package com.adamnfish.fbj.footballdata

import io.circe.Decoder

/*
 * Models for the football-data.org "matches" API response.
 */

case class MatchesResponse(
    competition: FootballDataCompetition,
    matches: List[FootballDataMatch]
) derives Decoder

case class FootballDataCompetition(
    id: Int,
    name: String,
    code: String
) derives Decoder

case class FootballDataMatch(
    id: Int,
    status: MatchStatus,
    // null for knockout matches, populated for group stage matches
    matchday: Option[Int],
    stage: Stage,
    // populated for group stage matches, None for knockout matches
    group: Option[String],
    homeTeam: FootballDataTeam,
    awayTeam: FootballDataTeam,
    score: MatchScore
) derives Decoder

// team fields are all null when a knockout match's participants
// haven't been determined yet (e.g. a semi-final not yet played)
case class FootballDataTeam(
    id: Option[Int],
    name: Option[String],
    shortName: Option[String],
    // three letter abbreviation, e.g. "MEX" - readable team identifier
    tla: Option[String],
    crest: Option[String]
) derives Decoder

case class MatchScore(
    winner: Option[MatchWinner],
    duration: MatchDuration,
    fullTime: ScoreLine,
    halfTime: ScoreLine
) derives Decoder

// goals scored by each side, `None` before/during a match
case class ScoreLine(
    home: Option[Int],
    away: Option[Int]
) derives Decoder

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
object MatchStatus {
  given Decoder[MatchStatus] = Decoder.decodeString.emap {
    case "SCHEDULED" => Right(Scheduled)
    case "TIMED"     => Right(Timed)
    case "IN_PLAY"   => Right(InPlay)
    case "PAUSED"    => Right(Paused)
    case "FINISHED"  => Right(Finished)
    case "POSTPONED" => Right(Postponed)
    case "SUSPENDED" => Right(Suspended)
    case "CANCELLED" => Right(Cancelled)
    case value       => Left(s"Unknown match status: $value")
  }
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
object Stage {
  given Decoder[Stage] = Decoder.decodeString.emap {
    case "GROUP_STAGE"    => Right(GroupStage)
    case "LAST_32"        => Right(Last32)
    case "LAST_16"        => Right(Last16)
    case "QUARTER_FINALS" => Right(QuarterFinals)
    case "SEMI_FINALS"    => Right(SemiFinals)
    case "THIRD_PLACE"    => Right(ThirdPlace)
    case "FINAL"          => Right(Final)
    case value            => Left(s"Unknown stage: $value")
  }
}

enum MatchWinner {
  case HomeTeam
  case AwayTeam
  case Draw
}
object MatchWinner {
  given Decoder[MatchWinner] = Decoder.decodeString.emap {
    case "HOME_TEAM" => Right(HomeTeam)
    case "AWAY_TEAM" => Right(AwayTeam)
    case "DRAW"      => Right(Draw)
    case value       => Left(s"Unknown match winner: $value")
  }
}

enum MatchDuration {
  case Regular
  case ExtraTime
  case PenaltyShootout
}
object MatchDuration {
  given Decoder[MatchDuration] = Decoder.decodeString.emap {
    case "REGULAR"          => Right(Regular)
    case "EXTRA_TIME"       => Right(ExtraTime)
    case "PENALTY_SHOOTOUT" => Right(PenaltyShootout)
    case value              => Left(s"Unknown match duration: $value")
  }
}
