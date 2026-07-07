package com.adamnfish.fbj

import com.adamnfish.fbj.models.Response
import com.adamnfish.fbj.services.Persistence

import scala.util.Try

class API(persistence: Persistence) {
  def dispatch(operation: String, body: String): Try[Response] =
    ???

  // API endpoints

  def ping(body: String): Try[Response.Ping] =
    ???
  def createGame(body: String): Try[Response.GameCreated] =
    ???
  def joinGame(body: String): Try[Response.GameJoined] =
    ???
  def editTeams(body: String): Try[Response.TeamsEdited] =
    ???
  def lockGame(body: String): Try[Response.GameLocked] =
    ???
  def unlockGame(body: String): Try[Response.GameUnlocked] =
    ???
  def fetchGameInfo(body: String): Try[Response.GameInfoFetched] =
    ???
}
