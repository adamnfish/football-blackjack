package com.adamnfish.fbj.models

import com.adamnfish.fbj.models.Progress.NotStarted

enum Request {
  case Ping
  case CreateGame(
      gameName: GameName,
      gameSettings: GameSettings,
      competitionCode: CompetitionCode,
      playerName: PlayerName
  )
  case JoinGame(gameId: GameId, playerName: PlayerName, teams: List[TeamId])
  case EditTeams(teams: List[TeamId], auth: Auth)
  case LockGame(gameId: GameId, auth: Auth)
  case FetchGameInfo(gameId: GameId)
}

enum Response {
  case Ping
  case GameCreated(
      game: Game,
      player: Player,
      playerKey: PlayerKey
  )
  case GameJoined(
      game: Game,
      player: Player,
      playerKey: PlayerKey
  )
  case TeamsEdited(teams: List[TeamId])
  case GameLocked()
  case GameInfoFetched(
      game: Game
  )
}

case class Auth(
    playerKey: PlayerKey
)

case class Game(
    gameId: GameId,
    gameName: GameName,
    gameSettings: GameSettings,
    locked: Boolean,
    players: List[Player],
    gameAdmin: PlayerId,
    competition: Competition
)

case class GameSettings(
    goalLimit: Int,
    teamCount: Int
)

case class Player(
    id: PlayerId,
    name: PlayerName,
    selection: List[TeamId]
)

/* Competition countries */

case class Competition(
    code: CompetitionCode,
    name: CompetitionName,
    teams: Map[Team, TeamStats]
)

case class Team(
    teamId: TeamId,
    name: TeamName,
    crestUrl: String
)

case class TeamStats(
    score: Score,
    progress: Progress,
    status: Status
)

case class Score(
    goalsFor: Int,
    goalsAgainst: Int
)

enum Progress {
  case NotStarted
  case Group(matchCount: Int)
  case Knockout(size: Int)
  case TopFour(rank: Int)
}

enum Status {
  case Eliminated
  case Playing
}

// wrapper types

opaque type GameId = String
object GameId {
  def apply(id: String): GameId = id
  extension (gameId: GameId) {
    def id: String = gameId
  }
}

opaque type GameName = String
object GameName {
  def apply(name: String): GameName = name
  extension (gameName: GameName) {
    def name: String = gameName
  }
}

opaque type PlayerId = String
object PlayerId {
  def apply(id: String): PlayerId = id
  extension (playerId: PlayerId) {
    def id: String = playerId
  }
}

opaque type PlayerKey = String
object PlayerKey {
  def apply(key: String): PlayerKey = key
  extension (playerKey: PlayerKey) {
    def key: String = playerKey
  }
}

opaque type PlayerName = String
object PlayerName {
  def apply(name: String): PlayerName = name
  extension (playerName: PlayerName) {
    def name: String = playerName
  }
}

opaque type TeamId = String
object TeamId {
  def apply(id: String): TeamId = id
  extension (teamId: TeamId) {
    def id: String = teamId
  }
}

case class TeamName(
    short: String,
    long: String
)

opaque type CompetitionCode = String
object CompetitionCode {
  def apply(code: String): CompetitionCode = code
  extension (competitionCode: CompetitionCode) {
    def code: String = competitionCode
  }
}

opaque type CompetitionName = String
object CompetitionName {
  def apply(name: String): CompetitionName = name
  extension (competitionName: CompetitionName) {
    def name: String = competitionName
  }
}
