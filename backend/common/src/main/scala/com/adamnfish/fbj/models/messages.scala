package com.adamnfish.fbj.models

import io.circe.Codec

enum Request derives Codec {
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
  case UnlockGame(gameId: GameId, auth: Auth)
  case FetchGameInfo(gameId: GameId)
}

enum Response derives Codec {
  case Ping
  case GameCreated(
      game: Game,
      player: Player,
      playerKey: PlayerKey,
      competitionStats: CompetitionStats
  )
  case GameJoined(
      game: Game,
      player: Player,
      playerKey: PlayerKey,
      competitionStats: CompetitionStats
  )
  case AnotherPlayerJoined(
      players: List[Player]
  )
  case TeamsEdited(teams: List[TeamId], playerId: PlayerId)
  case GameLocked()
  case GameUnlocked()
  case GameInfoFetched(
      game: Game,
      competitionStats: CompetitionStats
  )
}

enum Errors derives Codec {
  case ValidationError(field: String, message: String)
  case ValidationErrors(message: String, fields: List[(String, String)])

  case GameNotFound
  case CompetitionNotFound

  // game logic
  case TeamSelectionTaken
  case PlayerNameTaken
}

case class Auth(
    playerKey: PlayerKey
) derives Codec
