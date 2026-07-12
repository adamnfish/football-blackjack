package com.adamnfish.fbj.models

import io.circe.{Codec, KeyDecoder, KeyEncoder}

case class GameId(id: String) derives Codec
case class GameName(name: String) derives Codec

case class PlayerId(id: String) derives Codec
object PlayerId {
  // PlayerId is used as an object key in `Response.PlayerKeysFetched`
  given KeyEncoder[PlayerId] = KeyEncoder.instance(_.id)
  given KeyDecoder[PlayerId] = KeyDecoder.instance(id => Some(PlayerId(id)))
}
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
