package com.adamnfish.fbj.footballdata

import io.circe.parser.decode

import scala.io.Source

class ModelsTest extends munit.FunSuite {
  def matchesJson: String = {
    val source = Source.fromResource("matches.json")
    try source.mkString
    finally source.close()
  }

  def decoded: MatchesResponse =
    decode[MatchesResponse](matchesJson) match {
      case Right(response) => response
      case Left(error)     =>
        fail(s"Failed to decode matches.json: $error")
    }

  test("matches.json decodes successfully") {
    decode[MatchesResponse](matchesJson) match {
      case Right(_)    => // success
      case Left(error) => fail(s"Failed to decode matches.json: $error")
    }
  }

  test("competition details are parsed") {
    val competition = decoded.competition
    assertEquals(competition.id, 2000)
    assertEquals(competition.name, "FIFA World Cup")
    assertEquals(competition.code, "WC")
  }

  test("all matches are parsed") {
    assertEquals(decoded.matches.length, 104)
  }

  test("matches are split across the expected stages") {
    val countsByStage =
      decoded.matches.groupBy(_.stage).view.mapValues(_.length).toMap
    assertEquals(countsByStage(Stage.GroupStage), 72)
    assertEquals(countsByStage(Stage.Last32), 16)
    assertEquals(countsByStage(Stage.Last16), 8)
    assertEquals(countsByStage(Stage.QuarterFinals), 4)
    assertEquals(countsByStage(Stage.SemiFinals), 2)
    assertEquals(countsByStage(Stage.ThirdPlace), 1)
    assertEquals(countsByStage(Stage.Final), 1)
  }

  test("a finished group stage match is parsed in full") {
    val maybeMatch = decoded.matches.find(_.id == 537327)
    assert(maybeMatch.isDefined, "expected to find match 537327")
    val theMatch = maybeMatch.get

    assertEquals(theMatch.status, MatchStatus.Finished)
    assertEquals(theMatch.matchday, Some(1))
    assertEquals(theMatch.stage, Stage.GroupStage)
    assertEquals(theMatch.group, Some("GROUP_A"))
  }

  test("a finished group stage match's home team is parsed") {
    val theMatch = decoded.matches.find(_.id == 537327).get
    val homeTeam = theMatch.homeTeam

    assertEquals(homeTeam.id, Some(769))
    assertEquals(homeTeam.name, Some("Mexico"))
    assertEquals(homeTeam.shortName, Some("Mexico"))
    assertEquals(homeTeam.tla, Some("MEX"))
    assertEquals(
      homeTeam.crest,
      Some("https://crests.football-data.org/769.svg")
    )
  }

  test("a finished group stage match's away team is parsed") {
    val theMatch = decoded.matches.find(_.id == 537327).get
    val awayTeam = theMatch.awayTeam

    assertEquals(awayTeam.id, Some(774))
    assertEquals(awayTeam.name, Some("South Africa"))
    assertEquals(awayTeam.shortName, Some("South Africa"))
    assertEquals(awayTeam.tla, Some("RSA"))
    assertEquals(
      awayTeam.crest,
      Some("https://crests.football-data.org/9396.svg")
    )
  }

  test("a finished group stage match's score is parsed") {
    val theMatch = decoded.matches.find(_.id == 537327).get
    val score = theMatch.score

    assertEquals(score.winner, Some(MatchWinner.HomeTeam))
    assertEquals(score.duration, MatchDuration.Regular)
    assertEquals(score.fullTime, ScoreLine(Some(2), Some(0)))
    assertEquals(score.halfTime, ScoreLine(Some(1), Some(0)))
  }

  test("a penalty shootout knockout match's score is parsed") {
    val theMatch = decoded.matches.find(_.id == 537415).get
    val score = theMatch.score

    assertEquals(theMatch.stage, Stage.Last32)
    assertEquals(theMatch.matchday, None)
    assertEquals(theMatch.group, None)
    assertEquals(score.winner, Some(MatchWinner.AwayTeam))
    assertEquals(score.duration, MatchDuration.PenaltyShootout)
    assertEquals(score.fullTime, ScoreLine(Some(4), Some(5)))
  }

  test("an undetermined future final match's teams are parsed as None") {
    val theMatch = decoded.matches.find(_.id == 537390).get

    assertEquals(theMatch.status, MatchStatus.Timed)
    assertEquals(theMatch.stage, Stage.Final)
    assertEquals(
      theMatch.homeTeam,
      FootballDataTeam(None, None, None, None, None)
    )
    assertEquals(
      theMatch.awayTeam,
      FootballDataTeam(None, None, None, None, None)
    )
  }

  test("an undetermined future final match's score is parsed as None") {
    val theMatch = decoded.matches.find(_.id == 537390).get
    val score = theMatch.score

    assertEquals(score.winner, None)
    assertEquals(score.duration, MatchDuration.Regular)
    assertEquals(score.fullTime, ScoreLine(None, None))
    assertEquals(score.halfTime, ScoreLine(None, None))
  }

  test("only FINISHED and TIMED statuses appear in the fixture") {
    val statuses = decoded.matches.map(_.status).toSet
    assertEquals(statuses, Set(MatchStatus.Finished, MatchStatus.Timed))
  }
}
