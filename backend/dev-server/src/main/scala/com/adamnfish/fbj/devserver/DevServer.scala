package com.adamnfish.fbj.devserver

import com.adamnfish.fbj.models.*
import com.adamnfish.fbj.services.InMemoryPersistence
import com.adamnfish.fbj.{API, CompetitionJob, HttpMapping}
import io.circe.Json
import io.circe.syntax.*

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/** Local webserver exposing the same API the Lambda serves in production, for
  * frontend development and the e2e suite (via the Vite proxy).
  *
  * Phase 2 shape (plan/06-dev-server.md): API.dispatch behind
  * `POST /api/{operation}` via the shared HTTP mapping, in-memory persistence,
  * a stub CompetitionData serving canned stats for several tournament states,
  * and the `/dev` control panel with job trigger and demo-game seeding. The
  * simulated clock and DynamoDB Local arrive in phases 3-4.
  */
object DevServer extends cask.MainRoutes {
  override def port: Int = 9090

  val competition: Competition = DevFixtures.competition
  val persistence = new InMemoryPersistence()
  val competitionData = new StubCompetitionData(competition.id)
  val competitionJob = new CompetitionJob(persistence, competitionData)
  val api = new API(persistence, List(competition))

  override def main(args: Array[String]): Unit = {
    // seed pre-tournament stats so a locally created game works immediately
    competitionJob.fetchCompetition(competition.id).get
    super.main(args)
    println(s"Dev server listening on http://$host:$port")
    println(s"Control panel on http://$host:$port/dev")
  }

  @cask.post("/api/:operation")
  def apiRoute(
      request: cask.Request,
      operation: String
  ): cask.Response[String] = {
    val response = HttpMapping.toHttpResponse(
      api.dispatch(operation, request.text())
    )
    cask.Response(
      response.body,
      statusCode = response.status,
      headers = Seq("Content-Type" -> HttpMapping.jsonContentType)
    )
  }

  @cask.get("/dev")
  def devPanel(): cask.Response[String] = {
    val latestStats = persistence.loadCompetitionStats(competition.id).get
    val statsSummary = latestStats match {
      case Some(stats) =>
        val started =
          if (com.adamnfish.fbj.API.competitionStarted(stats)) "started"
          else "not started"
        s"latest snapshot ${stats.timestamp} ($started)"
      case None =>
        "no stats in the store"
    }
    val stateOptions = TournamentState.values
      .map { state =>
        val selected =
          if (state == competitionData.state) " selected" else ""
        s"""<option value="${state.id}"$selected>${state.id}</option>"""
      }
      .mkString("\n")
    htmlResponse(
      s"""<h1>Football Blackjack dev server</h1>
         |<p>Competition: ${competition.name.name} (${competition.code.code}, id ${competition.id.id})</p>
         |<p>Stats: $statsSummary</p>
         |<h2>Competition job</h2>
         |<p>Fetches stats for the selected tournament state from the stub
         |CompetitionData and saves them as the latest snapshot.</p>
         |<form method="post" action="/dev/job">
         |  <label>Tournament state
         |    <select name="state">
         |$stateOptions
         |    </select>
         |  </label>
         |  <button type="submit">Run competition job</button>
         |</form>
         |<h2>Demo game</h2>
         |<p>Creates a game with four players and their team selections
         |(unlocking it first if the tournament has started).</p>
         |<form method="post" action="/dev/seed-game">
         |  <button type="submit">Seed demo game</button>
         |</form>
         |""".stripMargin
    )
  }

  @cask.postForm("/dev/job")
  def runJob(state: String): cask.Response[String] =
    TournamentState.fromId(state) match {
      case Some(tournamentState) =>
        competitionData.state = tournamentState
        competitionJob.fetchCompetition(competition.id) match {
          case Success(()) =>
            seeOther("/dev")
          case Failure(err) =>
            cask.Response(s"competition job failed: $err", statusCode = 500)
        }
      case None =>
        cask.Response(s"unknown tournament state: $state", statusCode = 400)
    }

  @cask.post("/dev/seed-game")
  def seedGame(): cask.Response[String] =
    seedDemoGame() match {
      case Success((game, players)) =>
        val playerRows = players
          .map { case (player, playerKey) =>
            s"""<tr>
               |  <td>${player.name.name}</td>
               |  <td><code>${player.id.id}</code></td>
               |  <td><code>${playerKey.key}</code></td>
               |</tr>""".stripMargin
          }
          .mkString("\n")
        htmlResponse(
          s"""<h1>Demo game seeded</h1>
             |<p>Game code: <code>${game.id.id}</code> (${game.gameName.name})</p>
             |<table border="1" cellpadding="4">
             |  <tr><th>Player</th><th>Player id</th><th>Player key</th></tr>
             |$playerRows
             |</table>
             |<p><a href="/dev">Back to the dev panel</a></p>
             |""".stripMargin
        )
      case Failure(err) =>
        cask.Response(s"seeding failed: $err", statusCode = 500)
    }

  /** Creates a demo game through the real API: a creator who then picks teams,
    * plus three joiners with distinct selections. If the tournament has started
    * the game is unlocked first so the joins are allowed.
    */
  private def seedDemoGame(): Try[(Game, List[(Player, PlayerKey)])] = {
    def teamIds(teams: Team*): Json =
      teams.map(_.id).asJson

    import DevFixtures.{
      argentina,
      brazil,
      england,
      france,
      germany,
      japan,
      senegal,
      spain
    }
    val selections = List(
      ("Bob", teamIds(germany, spain, japan, senegal)),
      ("Carol", teamIds(england, france, germany, spain)),
      ("Dave", teamIds(brazil, argentina, japan, senegal))
    )
    for {
      created <- dispatchAs[Response.GameCreated](
        "create-game",
        Json.obj(
          "gameName" -> GameName("Demo game").asJson,
          "gameSettings" -> GameSettings(25, 4).asJson,
          "competitionCode" -> competition.code.asJson,
          "playerName" -> PlayerName("Alice").asJson
        )
      )
      gameId = created.game.id
      adminAuth = Auth(created.playerKey).asJson
      // the auto-lock engages once the tournament has started; unlock so the
      // demo joins work in every tournament state
      _ <-
        if (com.adamnfish.fbj.API.competitionStarted(created.competitionStats))
          dispatchAs[Response.GameUnlocked](
            "unlock-game",
            Json.obj("gameId" -> gameId.asJson, "auth" -> adminAuth)
          )
        else Success(())
      _ <- dispatchAs[Response.TeamsEdited](
        "edit-teams",
        Json.obj(
          "gameId" -> gameId.asJson,
          "teams" -> teamIds(england, france, brazil, argentina),
          "auth" -> adminAuth
        )
      )
      joined <- selections.foldLeft(
        Try(List.empty[(Player, PlayerKey)])
      ) { case (soFar, (name, teams)) =>
        for {
          players <- soFar
          joined <- dispatchAs[Response.GameJoined](
            "join-game",
            Json.obj(
              "gameId" -> gameId.asJson,
              "playerName" -> PlayerName(name).asJson,
              "teams" -> teams
            )
          )
        } yield players :+ (joined.player, joined.playerKey)
      }
      finalState <- dispatchAs[Response.GameInfoFetched](
        "fetch-game-info",
        Json.obj("gameId" -> gameId.asJson)
      )
    } yield (
      finalState.game,
      (created.player, created.playerKey) :: joined
    )
  }

  private def dispatchAs[A <: Response: ClassTag](
      operation: String,
      body: Json
  ): Try[A] =
    api.dispatch(operation, body.noSpaces).flatMap {
      case expected: A => Success(expected)
      case other       =>
        Failure(new Exception(s"unexpected response for $operation: $other"))
    }

  private def htmlResponse(body: String): cask.Response[String] =
    cask.Response(
      s"<!doctype html><html><head><title>Football Blackjack dev</title></head><body>$body</body></html>",
      headers = Seq("Content-Type" -> "text/html; charset=utf-8")
    )

  private def seeOther(location: String): cask.Response[String] =
    cask.Response(
      "",
      statusCode = 303,
      headers = Seq("Location" -> location)
    )

  initialize()
}
