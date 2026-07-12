package com.adamnfish.fbj.models

import com.adamnfish.fbj.*
import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.*

import java.time.Instant

case class Game(
    id: GameId,
    gameName: GameName,
    gameSettings: GameSettings,
    lockState: LockState,
    players: List[Player],
    gameAdmin: PlayerId,
    competition: Competition
) derives Codec

/** Whether players can join the game or edit their team selections.
  *
  * The effective lock is `Locked`, or `Auto` once the competition's stats show
  * the tournament has started. `Unlocked` is an explicit admin override of the
  * auto-lock.
  */
enum LockState derives Codec {
  case Auto
  case Locked
  case Unlocked
}

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
    timestamp: Instant,
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
