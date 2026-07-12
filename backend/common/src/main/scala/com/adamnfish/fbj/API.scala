package com.adamnfish.fbj

import com.adamnfish.fbj.models.*
import com.adamnfish.fbj.services.{
  ConcurrentModification,
  GameRecord,
  Persistence,
  Versioned
}
import io.circe.Decoder
import io.circe.parser.decode

import java.util.UUID
import scala.util.{Failure, Random, Success, Try}

/** The game's API: `dispatch(operation, body)` selects the endpoint and each
  * endpoint decodes its own payload (see plan/03-api.md).
  *
  * Competitions come from configuration; their stats come from the store,
  * written there by the competition job.
  */
class API(persistence: Persistence, competitions: List[Competition]) {
  def dispatch(operation: String, body: String): Try[Response] =
    operation match {
      case "ping"              => ping(body)
      case "create-game"       => createGame(body)
      case "join-game"         => joinGame(body)
      case "edit-teams"        => editTeams(body)
      case "lock-game"         => lockGame(body)
      case "unlock-game"       => unlockGame(body)
      case "fetch-game-info"   => fetchGameInfo(body)
      case "fetch-player-keys" => fetchPlayerKeys(body)
      case unknown             =>
        API.fail(
          Errors.ValidationError("operation", s"unknown operation: $unknown")
        )
    }

  // API endpoints

  def ping(body: String): Try[Response.Ping] =
    Success(Response.Ping())

  def createGame(body: String): Try[Response.GameCreated] =
    for {
      request <- API.decodeBody[Request.CreateGame](body)
      _ <- API.validate(
        API.requireField(
          request.gameName.name.trim.nonEmpty,
          "gameName",
          "game name is required"
        ) ++ API.requireField(
          request.playerName.name.trim.nonEmpty,
          "playerName",
          "player name is required"
        ) ++ API.requireField(
          request.gameSettings.goalLimit >= 1,
          "goalLimit",
          "goal limit must be at least 1"
        ) ++ API.requireField(
          request.gameSettings.teamCount >= 1,
          "teamCount",
          "team count must be at least 1"
        )
      )
      competition <- API.orFail(
        competitions.find(_.code == request.competitionCode),
        Errors.CompetitionNotFound
      )
      competitionStats <- loadStats(competition.id)
      player = Player(newPlayerId(), request.playerName, Nil)
      playerKey = newPlayerKey()
      game = Game(
        newGameId(),
        request.gameName,
        request.gameSettings,
        LockState.Auto,
        List(player),
        player.id,
        competition
      )
      saved <- createGameRecord(GameRecord(game, Map(player.id -> playerKey)))
    } yield Response.GameCreated(
      saved.value.game,
      player,
      playerKey,
      competitionStats
    )

  def joinGame(body: String): Try[Response.GameJoined] =
    ???
  def editTeams(body: String): Try[Response.TeamsEdited] =
    ???
  def lockGame(body: String): Try[Response.GameLocked] =
    ???
  def unlockGame(body: String): Try[Response.GameUnlocked] =
    ???

  def fetchGameInfo(body: String): Try[Response.GameInfoFetched] =
    for {
      request <- API.decodeBody[Request.FetchGameInfo](body)
      record <- loadGame(request.gameId)
      competitionStats <- loadStats(record.value.game.competition.id)
    } yield Response.GameInfoFetched(record.value.game, competitionStats)

  def fetchPlayerKeys(body: String): Try[Response.PlayerKeysFetched] =
    ???

  // persistence helpers

  private def loadGame(gameId: GameId): Try[Versioned[GameRecord]] =
    persistence
      .loadGame(gameId)
      .flatMap(API.orFail(_, Errors.GameNotFound))

  private def loadStats(competitionId: CompetitionId): Try[CompetitionStats] =
    persistence
      .loadCompetitionStats(competitionId)
      .flatMap(API.orFail(_, Errors.CompetitionNotFound))

  /** Creates the record (version 0); on a join-code collision, retries once
    * with a fresh code.
    */
  private def createGameRecord(
      record: GameRecord
  ): Try[Versioned[GameRecord]] =
    persistence.saveGame(Versioned(record, 0)).recoverWith {
      case _: ConcurrentModification =>
        persistence.saveGame(
          Versioned(
            record.copy(game = record.game.copy(id = newGameId())),
            0
          )
        )
    }

  // id generation

  private def newGameId(): GameId =
    GameId(
      List
        .fill(API.gameCodeLength)(
          API.gameCodeAlphabet(Random.nextInt(API.gameCodeAlphabet.length))
        )
        .mkString
    )

  private def newPlayerId(): PlayerId =
    PlayerId(UUID.randomUUID().toString)

  private def newPlayerKey(): PlayerKey =
    PlayerKey(UUID.randomUUID().toString)
}

object API {

  /** Short human-friendly join codes: lowercase, no ambiguous characters. */
  private val gameCodeAlphabet = "abcdefghjkmnpqrstuvwxyz23456789"
  private val gameCodeLength = 6

  private[fbj] def fail[A](errors: Errors): Try[A] =
    Failure(ApiError(errors))

  private[fbj] def orFail[A](maybe: Option[A], errors: Errors): Try[A] =
    maybe.fold(fail[A](errors))(Success(_))

  private[fbj] def decodeBody[A: Decoder](body: String): Try[A] =
    decode[A](body) match {
      case Right(request) => Success(request)
      case Left(err)      =>
        fail(Errors.ValidationError("body", s"could not parse request: $err"))
    }

  private[fbj] def requireField(
      valid: Boolean,
      field: String,
      message: String
  ): List[(String, String)] =
    if (valid) Nil else List(field -> message)

  /** Fails with the accumulated field problems: a single problem is a
    * `ValidationError`, several are `ValidationErrors`.
    */
  private[fbj] def validate(problems: List[(String, String)]): Try[Unit] =
    problems match {
      case Nil                     => Success(())
      case (field, message) :: Nil =>
        fail(Errors.ValidationError(field, message))
      case many =>
        fail(Errors.ValidationErrors("invalid request", many))
    }
}
