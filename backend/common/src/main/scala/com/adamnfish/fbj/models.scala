package com.adamnfish.fbj

/* Players */

case class Player(
    id: String,
    name: String,
    selection: List[String]
)

/* Tournament countries */

case class Country(
    code: String,
    name: String,
    flag: String
)

case class Score(
    goalsFor: Int,
    goalsAgainst: Int
)

enum Progress {
  case Group(matchCount: Int)
  case Knockout(size: Int)
  case TopFour(rank: Int)
}
