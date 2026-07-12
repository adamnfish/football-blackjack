package com.adamnfish.fbj

import com.adamnfish.fbj.models.*
import com.adamnfish.fbj.services.{CompetitionData, InMemoryPersistence}
import munit.FunSuite

import java.time.Instant
import scala.util.{Failure, Success, Try}

class CompetitionJobTest extends FunSuite with APITestSupport {
  class StubData(result: Try[CompetitionStats]) extends CompetitionData {
    override def fetchCompetitionStats(
        competitionId: CompetitionId
    ): Try[CompetitionStats] = result
  }

  test("a successful run writes the fetched snapshot") {
    val persistence = new InMemoryPersistence()
    val job =
      new CompetitionJob(persistence, new StubData(Success(midGroupStats)))
    assertEquals(job.fetchCompetition(competition.id), Success(()))
    assertEquals(
      persistence.loadCompetitionStats(competition.id),
      Success(Some(midGroupStats))
    )
  }

  test("a fetch failure propagates and writes nothing") {
    val persistence = new InMemoryPersistence()
    persistence.saveCompetitionStats(preTournamentStats).get
    val failure = new RuntimeException("football-data is down")
    val job = new CompetitionJob(persistence, new StubData(Failure(failure)))
    assertEquals(job.fetchCompetition(competition.id), Failure(failure))
    assertEquals(
      persistence.loadCompetitionStats(competition.id),
      Success(Some(preTournamentStats)),
      "the previous latest snapshot stays in place"
    )
  }

  test("a persistence failure propagates") {
    val failure = new RuntimeException("store is down")
    val persistence = new InMemoryPersistence() {
      override def saveCompetitionStats(
          competitionStats: CompetitionStats
      ): Try[CompetitionStats] = Failure(failure)
    }
    val job =
      new CompetitionJob(persistence, new StubData(Success(midGroupStats)))
    assertEquals(job.fetchCompetition(competition.id), Failure(failure))
  }
}
