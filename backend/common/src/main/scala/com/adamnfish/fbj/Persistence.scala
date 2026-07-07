package com.adamnfish.fbj

import com.adamnfish.fbj.models.{Auth, Competition, CompetitionId, CompetitionStats, Game, GameId}

import scala.util.Try

trait Persistence {
  // blackjack games
  def getGame(gameId: GameId): Try[Game]
  def saveGame(game: Game): Try[Game]
  
  // competition stats
  def getCompetitionStats(competitionId: CompetitionId): Try[CompetitionStats]
  def saveCompetitionStats(competitionStats: CompetitionStats): Try[CompetitionStats]
}
