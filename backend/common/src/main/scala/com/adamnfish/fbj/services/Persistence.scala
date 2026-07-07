package com.adamnfish.fbj.services

import com.adamnfish.fbj.models.*

import scala.util.Try

trait Persistence {
  // blackjack games
  def loadGame(gameId: GameId): Try[Game]
  def saveGame(game: Game): Try[Game]

  // football competition stats
  def loadCompetitionStats(competitionId: CompetitionId): Try[CompetitionStats]
  def saveCompetitionStats(
      competitionStats: CompetitionStats
  ): Try[CompetitionStats]
}
