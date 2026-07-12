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
    API.withConflictRetry {
      for {
        request <- API.decodeBody[Request.JoinGame](body)
        record <- loadGame(request.gameId)
        game = record.value.game
        competitionStats <- loadStats(game.competition.id)
        _ <- checkUnlocked(game, competitionStats)
        _ <- API.validate(
          API.requireField(
            request.playerName.name.trim.nonEmpty,
            "playerName",
            "player name is required"
          ) ++ selectionProblems(
            request.teams,
            game.gameSettings,
            competitionStats
          )
        )
        _ <- checkPlayerNameAvailable(game, request.playerName)
        _ <- checkSelectionAvailable(game, request.teams, editingPlayer = None)
        player = Player(newPlayerId(), request.playerName, request.teams)
        playerKey = newPlayerKey()
        updated = GameRecord(
          game.copy(players = game.players :+ player),
          record.value.playerKeys + (player.id -> playerKey)
        )
        saved <- persistence.saveGame(Versioned(updated, record.version))
      } yield Response.GameJoined(
        saved.value.game,
        player,
        playerKey,
        competitionStats
      )
    }

  def editTeams(body: String): Try[Response.TeamsEdited] =
    API.withConflictRetry {
      for {
        request <- API.decodeBody[Request.EditTeams](body)
        record <- loadGame(request.gameId)
        game = record.value.game
        player <- authenticate(record.value, request.auth)
        competitionStats <- loadStats(game.competition.id)
        _ <- checkUnlocked(game, competitionStats)
        _ <- API.validate(
          selectionProblems(request.teams, game.gameSettings, competitionStats)
        )
        _ <- checkSelectionAvailable(
          game,
          request.teams,
          editingPlayer = Some(player.id)
        )
        updated = record.value.copy(game =
          game.copy(players = game.players.map { p =>
            if (p.id == player.id) p.copy(selection = request.teams) else p
          })
        )
        _ <- persistence.saveGame(Versioned(updated, record.version))
      } yield Response.TeamsEdited(request.teams, player.id)
    }

  def lockGame(body: String): Try[Response.GameLocked] =
    API.withConflictRetry {
      for {
        request <- API.decodeBody[Request.LockGame](body)
        record <- loadGame(request.gameId)
        _ <- authenticateAdmin(record.value, request.auth)
        updated = record.value.copy(game =
          record.value.game.copy(lockState = LockState.Locked)
        )
        _ <- persistence.saveGame(Versioned(updated, record.version))
      } yield Response.GameLocked()
    }

  def unlockGame(body: String): Try[Response.GameUnlocked] =
    API.withConflictRetry {
      for {
        request <- API.decodeBody[Request.UnlockGame](body)
        record <- loadGame(request.gameId)
        _ <- authenticateAdmin(record.value, request.auth)
        updated = record.value.copy(game =
          record.value.game.copy(lockState = LockState.Unlocked)
        )
        _ <- persistence.saveGame(Versioned(updated, record.version))
      } yield Response.GameUnlocked()
    }

  def fetchGameInfo(body: String): Try[Response.GameInfoFetched] =
    for {
      request <- API.decodeBody[Request.FetchGameInfo](body)
      record <- loadGame(request.gameId)
      competitionStats <- loadStats(record.value.game.competition.id)
    } yield Response.GameInfoFetched(record.value.game, competitionStats)

  def fetchPlayerKeys(body: String): Try[Response.PlayerKeysFetched] =
    for {
      request <- API.decodeBody[Request.FetchPlayerKeys](body)
      record <- loadGame(request.gameId)
      _ <- authenticateAdmin(record.value, request.auth)
    } yield Response.PlayerKeysFetched(record.value.playerKeys)

  // auth, lock and game-logic checks

  /** The player this key belongs to, or `Unauthorized`. */
  private def authenticate(record: GameRecord, auth: Auth): Try[Player] =
    API.orFail(
      record.playerKeys
        .collectFirst {
          case (playerId, playerKey) if playerKey == auth.playerKey => playerId
        }
        .flatMap(playerId => record.game.players.find(_.id == playerId)),
      Errors.Unauthorized
    )

  /** As `authenticate`, and the player must be the game's admin. */
  private def authenticateAdmin(record: GameRecord, auth: Auth): Try[Player] =
    authenticate(record, auth).flatMap { player =>
      if (player.id == record.game.gameAdmin) Success(player)
      else API.fail(Errors.Unauthorized)
    }

  /** Fails with `GameLocked` if the game's effective lock is engaged: `Locked`,
    * or `Auto` once the stats show the competition has started.
    */
  private def checkUnlocked(
      game: Game,
      competitionStats: CompetitionStats
  ): Try[Unit] = {
    val locked = game.lockState match {
      case LockState.Locked   => true
      case LockState.Unlocked => false
      case LockState.Auto     => API.competitionStarted(competitionStats)
    }
    if (locked) API.fail(Errors.GameLocked) else Success(())
  }

  /** Field problems with a team selection: size, distinctness, membership of
    * the competition.
    */
  private def selectionProblems(
      teams: List[TeamId],
      gameSettings: GameSettings,
      competitionStats: CompetitionStats
  ): List[(String, String)] = {
    val competitionTeamIds = competitionStats.teams.keySet.map(_.id)
    val unknown = teams.filterNot(competitionTeamIds.contains)
    API.requireField(
      teams.length == gameSettings.teamCount,
      "teams",
      s"you must pick exactly ${gameSettings.teamCount} teams"
    ) ++ API.requireField(
      teams.distinct.length == teams.length,
      "teams",
      "teams must all be different"
    ) ++ API.requireField(
      unknown.isEmpty,
      "teams",
      s"teams are not in this competition: ${unknown.map(_.id).mkString(", ")}"
    )
  }

  private def checkPlayerNameAvailable(
      game: Game,
      playerName: PlayerName
  ): Try[Unit] =
    if (
      game.players.exists(
        _.name.name.trim.equalsIgnoreCase(
          playerName.name.trim
        )
      )
    )
      API.fail(Errors.PlayerNameTaken)
    else Success(())

  /** Fails with `TeamSelectionTaken` if another player already holds this
    * combination of teams, as a set (order-insensitive). An editing player's
    * own selection does not count against them.
    */
  private def checkSelectionAvailable(
      game: Game,
      teams: List[TeamId],
      editingPlayer: Option[PlayerId]
  ): Try[Unit] = {
    val selection = teams.toSet
    val taken = game.players.exists { player =>
      editingPlayer.forall(_ != player.id) &&
      player.selection.nonEmpty &&
      player.selection.toSet == selection
    }
    if (taken) API.fail(Errors.TeamSelectionTaken) else Success(())
  }

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

  /** Whether the stats show the tournament has started: any team's progress
    * beyond `NotStarted`.
    */
  def competitionStarted(competitionStats: CompetitionStats): Boolean =
    competitionStats.teams.values.exists(_.progress != Progress.NotStarted)

  /** Retries a read-validate-save sequence once on a version conflict: the
    * retry either succeeds against the fresh state or yields the honest domain
    * error.
    */
  private def withConflictRetry[A](attempt: => Try[A]): Try[A] =
    attempt.recoverWith { case _: ConcurrentModification => attempt }

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
