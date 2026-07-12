package com.adamnfish.fbj.services

import com.adamnfish.fbj.models.*

import scala.util.Try

/** Optimistic-versioning envelope for persisted records. */
case class Versioned[A](value: A, version: Int)

/** The persisted unit for a game: the public Game plus server-side secrets.
  * Player keys must never ride on Game itself, which is returned whole in API
  * responses.
  */
case class GameRecord(game: Game, playerKeys: Map[PlayerId, PlayerKey])

/** A conditional save failed its version check: the stored version did not
  * match the expected one, or a create (version 0) found the record already
  * present.
  */
final class ConcurrentModification(message: String) extends Exception(message)

trait Persistence {
  // blackjack games
  def loadGame(gameId: GameId): Try[Option[Versioned[GameRecord]]]

  /** Saves `Versioned(record, n)` as version `n + 1`, conditional on the stored
    * version still being `n`. Version 0 means "create": the write is
    * conditional on the game not existing. A conflict fails the `Try` with
    * [[ConcurrentModification]].
    */
  def saveGame(game: Versioned[GameRecord]): Try[Versioned[GameRecord]]

  // football competition stats
  /** Returns the latest snapshot for the competition, if any exist. */
  def loadCompetitionStats(
      competitionId: CompetitionId
  ): Try[Option[CompetitionStats]]

  /** Unconditional append keyed by `(competitionId, timestamp)`. */
  def saveCompetitionStats(
      competitionStats: CompetitionStats
  ): Try[CompetitionStats]
}
