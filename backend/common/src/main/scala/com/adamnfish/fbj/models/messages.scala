package com.adamnfish.fbj.models

import io.circe.{Codec, Decoder}

enum Request derives Codec {
  case Ping
  case CreateGame(
      gameName: GameName,
      gameSettings: GameSettings,
      competitionCode: CompetitionCode,
      playerName: PlayerName
  )
  case JoinGame(gameId: GameId, playerName: PlayerName, teams: List[TeamId])
  case EditTeams(gameId: GameId, teams: List[TeamId], auth: Auth)
  case LockGame(gameId: GameId, auth: Auth)
  case UnlockGame(gameId: GameId, auth: Auth)
  case FetchGameInfo(gameId: GameId)
  case FetchPlayerKeys(gameId: GameId, auth: Auth)
}
object Request {
  // Per-case decoders: the operation in the URL selects the case, so request
  // bodies hold just that case's fields (see plan/03-api.md).
  given Decoder[Request.CreateGame] = Decoder.derived
  given Decoder[Request.JoinGame] = Decoder.derived
  given Decoder[Request.EditTeams] = Decoder.derived
  given Decoder[Request.LockGame] = Decoder.derived
  given Decoder[Request.UnlockGame] = Decoder.derived
  given Decoder[Request.FetchGameInfo] = Decoder.derived
  given Decoder[Request.FetchPlayerKeys] = Decoder.derived
}

enum Response derives Codec {
  case Ping()
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
  case TeamsEdited(teams: List[TeamId], playerId: PlayerId)
  case GameLocked()
  case GameUnlocked()
  case GameInfoFetched(
      game: Game,
      competitionStats: CompetitionStats
  )
  case PlayerKeysFetched(playerKeys: Map[PlayerId, PlayerKey])
}

enum Errors derives Codec {
  case ValidationError(field: String, message: String)
  case ValidationErrors(message: String, fields: List[(String, String)])

  case GameNotFound
  case CompetitionNotFound

  // auth & lock state
  case Unauthorized
  case GameLocked

  // game logic
  case TeamSelectionTaken
  case PlayerNameTaken
}

/** Carries an expected domain failure ([[Errors]]) through the `Try` error
  * channel, so the Lambda and dev server can pattern-match it out into an HTTP
  * response (see plan/03-api.md).
  */
final case class ApiError(errors: Errors) extends Exception(errors.toString)

case class Auth(
    playerKey: PlayerKey
) derives Codec
