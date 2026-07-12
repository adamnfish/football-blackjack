package com.adamnfish.fbj.services

import com.adamnfish.fbj.models.*

import java.time.Instant
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

/** In-memory Persistence for tests and the dev server (phases 2-3).
  *
  * Enforces exactly the version-check semantics of the production store: a save
  * of `Versioned(record, n)` writes version `n + 1` conditional on the stored
  * version still being `n`; version 0 means create, conditional on the game not
  * existing. Stats writes are append-only keyed by `(competitionId,
  * timestamp)`; loads return the latest snapshot.
  */
class InMemoryPersistence extends Persistence {
  private val games = mutable.Map.empty[GameId, Versioned[GameRecord]]
  private val stats =
    mutable.Map.empty[(CompetitionId, Instant), CompetitionStats]

  override def loadGame(gameId: GameId): Try[Option[Versioned[GameRecord]]] =
    synchronized {
      Success(games.get(gameId))
    }

  override def saveGame(
      game: Versioned[GameRecord]
  ): Try[Versioned[GameRecord]] =
    synchronized {
      val gameId = game.value.game.id
      val stored = games.get(gameId)
      (game.version, stored) match {
        case (0, Some(_)) =>
          Failure(
            new ConcurrentModification(
              s"create failed: game ${gameId.id} already exists"
            )
          )
        case (0, None) =>
          val saved = Versioned(game.value, 1)
          games.update(gameId, saved)
          Success(saved)
        case (expected, Some(Versioned(_, storedVersion)))
            if storedVersion == expected =>
          val saved = Versioned(game.value, expected + 1)
          games.update(gameId, saved)
          Success(saved)
        case (expected, Some(Versioned(_, storedVersion))) =>
          Failure(
            new ConcurrentModification(
              s"save failed: game ${gameId.id} is at version $storedVersion, expected $expected"
            )
          )
        case (expected, None) =>
          Failure(
            new ConcurrentModification(
              s"save failed: game ${gameId.id} does not exist, expected version $expected"
            )
          )
      }
    }

  override def loadCompetitionStats(
      competitionId: CompetitionId
  ): Try[Option[CompetitionStats]] =
    synchronized {
      Success(
        stats
          .collect {
            case ((id, _), competitionStats) if id == competitionId =>
              competitionStats
          }
          .maxByOption(_.timestamp)
      )
    }

  override def saveCompetitionStats(
      competitionStats: CompetitionStats
  ): Try[CompetitionStats] =
    synchronized {
      stats.update(
        (competitionStats.id, competitionStats.timestamp),
        competitionStats
      )
      Success(competitionStats)
    }
}
