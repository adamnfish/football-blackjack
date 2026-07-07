package com.adamnfish.fbj.models

import io.circe.Codec

case class GameId(id: String) derives Codec
case class GameName(name: String) derives Codec

case class PlayerId(id: String) derives Codec
case class PlayerKey(key: String) derives Codec
case class PlayerName(name: String) derives Codec

case class TeamId(id: String) derives Codec
case class TeamTLA(tla: String) derives Codec

case class TeamName(
    short: String,
    long: String
) derives Codec

case class CompetitionId(id: Int) derives Codec
case class CompetitionCode(code: String) derives Codec
case class CompetitionName(name: String) derives Codec
