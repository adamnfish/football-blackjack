package com.adamnfish.fbj.models

import com.adamnfish.fbj.*
import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.*

case class Game(
    id: GameId,
    gameName: GameName,
    gameSettings: GameSettings,
    locked: Boolean,
    players: List[Player],
    gameAdmin: PlayerId,
    competition: Competition
) derives Codec

case class GameSettings(
    goalLimit: Int,
    teamCount: Int
) derives Codec

case class Player(
    id: PlayerId,
    name: PlayerName,
    selection: List[TeamId]
) derives Codec

case class Competition(
    id: CompetitionId,
    code: CompetitionCode,
    name: CompetitionName
) derives Codec

case class CompetitionStats(
    id: CompetitionId,
    timestamp: Long,
    teams: Map[Team, TeamStats]
) derives Codec

case class Team(
    id: TeamId,
    tla: TeamTLA,
    name: TeamName,
    crestUrl: String
) derives Codec
object Team {
  // Team is used as an object key in `Competition.teams`
  given KeyEncoder[Team] = KeyEncoder.instance(_.asJson.noSpaces)
  given KeyDecoder[Team] = KeyDecoder.instance(decode[Team](_).toOption)
}

case class TeamStats(
    score: Score,
    progress: Progress,
    status: Status
) derives Codec

case class Score(
    goalsFor: Int,
    goalsAgainst: Int
) derives Codec

enum Progress derives Codec {
  case NotStarted
  case Group(matchCount: Int)
  case Knockout(size: Int)
  case TopFour(rank: Int)
}

enum Status derives Codec {
  case Eliminated
  case Playing
}
