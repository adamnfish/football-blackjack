module Api exposing
    ( ApiError(..)
    , CreateGameRequest
    , GameCreated
    , GameInfoFetched
    , GameJoined
    , JoinGameRequest
    , TeamsEdited
    , createGame
    , createGameEncoder
    , editTeams
    , editTeamsEncoder
    , errorMessage
    , errorsDecoder
    , fetchGameInfo
    , fetchGameInfoEncoder
    , gameCreatedDecoder
    , gameInfoFetchedDecoder
    , gameJoinedDecoder
    , joinGame
    , joinGameEncoder
    , lockGame
    , lockGameEncoder
    , ping
    , teamsEditedDecoder
    , unlockGame
    , unlockGameEncoder
    )

{-| One function per API operation (`POST /api/{operation}`, kebab-case), with
hand-written encoders/decoders mirroring the circe formats of the Scala models
(`backend/common`):

  - request bodies are the bare fields of the `Request` case
  - responses and errors are the circe encodings of the `Response`/`Errors`
    enums: `{"CaseName": {...fields}}`, singleton cases as `{"CaseName": {}}`
  - wrapper types encode as objects (`{"id": ...}`, `{"key": ...}`, ...)

The encoders/decoders are exposed for the golden-sample tests, which assert
against JSON files shared with the Scala side (`fixtures/api/`).

-}

import Domain exposing (..)
import Http
import Json.Decode as Decode exposing (Decoder)
import Json.Encode as Encode



-- Errors


type ApiError
    = ApplicationError Errors
    | TransportError String


{-| A short, user-facing description of an API failure. Pages give friendlier
context for the errors they expect (e.g. `TeamSelectionTaken` on the selection
screen); this is the fallback.
-}
errorMessage : ApiError -> String
errorMessage apiError =
    case apiError of
        TransportError message ->
            "Could not reach the server (" ++ message ++ ")"

        ApplicationError errors ->
            case errors of
                ValidationError { field, message } ->
                    field ++ ": " ++ message

                ValidationErrors { fields } ->
                    fields
                        |> List.map (\( field, message ) -> field ++ ": " ++ message)
                        |> String.join "; "

                GameNotFound ->
                    "Game not found"

                CompetitionNotFound ->
                    "Competition not found"

                TeamSelectionTaken ->
                    "That combination of teams is already taken"

                PlayerNameTaken ->
                    "That name is already taken in this game"

                GameLocked ->
                    "This game is locked"

                Unauthorized ->
                    "You are not allowed to do that"



-- Operations


ping : (Result ApiError () -> msg) -> Cmd msg
ping toMsg =
    post "ping" (Encode.object []) (Decode.succeed ()) toMsg


type alias CreateGameRequest =
    { gameName : GameName
    , gameSettings : GameSettings
    , competitionCode : CompetitionCode
    , playerName : PlayerName
    }


type alias GameCreated =
    { game : Game
    , player : Player
    , playerKey : PlayerKey
    , competitionStats : CompetitionStats
    }


createGame : CreateGameRequest -> (Result ApiError GameCreated -> msg) -> Cmd msg
createGame request toMsg =
    post "create-game" (createGameEncoder request) gameCreatedDecoder toMsg


type alias JoinGameRequest =
    { gameId : GameId
    , playerName : PlayerName
    , teams : List TeamId
    }


type alias GameJoined =
    { game : Game
    , player : Player
    , playerKey : PlayerKey
    , competitionStats : CompetitionStats
    }


joinGame : JoinGameRequest -> (Result ApiError GameJoined -> msg) -> Cmd msg
joinGame request toMsg =
    post "join-game" (joinGameEncoder request) gameJoinedDecoder toMsg


type alias TeamsEdited =
    { teams : List TeamId
    , playerId : PlayerId
    }


editTeams : List TeamId -> PlayerKey -> (Result ApiError TeamsEdited -> msg) -> Cmd msg
editTeams teams playerKey toMsg =
    post "edit-teams" (editTeamsEncoder teams playerKey) teamsEditedDecoder toMsg


lockGame : GameId -> PlayerKey -> (Result ApiError () -> msg) -> Cmd msg
lockGame gameId playerKey toMsg =
    post "lock-game"
        (lockGameEncoder gameId playerKey)
        (Decode.field "GameLocked" (Decode.succeed ()))
        toMsg


unlockGame : GameId -> PlayerKey -> (Result ApiError () -> msg) -> Cmd msg
unlockGame gameId playerKey toMsg =
    post "unlock-game"
        (unlockGameEncoder gameId playerKey)
        (Decode.field "GameUnlocked" (Decode.succeed ()))
        toMsg


type alias GameInfoFetched =
    { game : Game
    , competitionStats : CompetitionStats
    }


fetchGameInfo : GameId -> (Result ApiError GameInfoFetched -> msg) -> Cmd msg
fetchGameInfo gameId toMsg =
    post "fetch-game-info" (fetchGameInfoEncoder gameId) gameInfoFetchedDecoder toMsg



-- HTTP plumbing


post : String -> Encode.Value -> Decoder a -> (Result ApiError a -> msg) -> Cmd msg
post operation body decoder toMsg =
    Http.post
        { url = "/api/" ++ operation
        , body = Http.jsonBody body
        , expect = expectApiResponse decoder toMsg
        }


expectApiResponse : Decoder a -> (Result ApiError a -> msg) -> Http.Expect msg
expectApiResponse decoder toMsg =
    Http.expectStringResponse toMsg
        (\response ->
            case response of
                Http.BadUrl_ url ->
                    Err (TransportError ("bad url: " ++ url))

                Http.Timeout_ ->
                    Err (TransportError "timed out")

                Http.NetworkError_ ->
                    Err (TransportError "network error")

                Http.BadStatus_ metadata body ->
                    case Decode.decodeString errorsDecoder body of
                        Ok errors ->
                            Err (ApplicationError errors)

                        Err _ ->
                            Err
                                (TransportError
                                    ("unexpected response, status "
                                        ++ String.fromInt metadata.statusCode
                                    )
                                )

                Http.GoodStatus_ _ body ->
                    case Decode.decodeString decoder body of
                        Ok value ->
                            Ok value

                        Err decodeError ->
                            Err (TransportError (Decode.errorToString decodeError))
        )



-- Request encoders (bare payloads: the fields of the Request enum case)


createGameEncoder : CreateGameRequest -> Encode.Value
createGameEncoder request =
    Encode.object
        [ ( "gameName", gameNameEncoder request.gameName )
        , ( "gameSettings", gameSettingsEncoder request.gameSettings )
        , ( "competitionCode", competitionCodeEncoder request.competitionCode )
        , ( "playerName", playerNameEncoder request.playerName )
        ]


joinGameEncoder : JoinGameRequest -> Encode.Value
joinGameEncoder request =
    Encode.object
        [ ( "gameId", gameIdEncoder request.gameId )
        , ( "playerName", playerNameEncoder request.playerName )
        , ( "teams", Encode.list teamIdEncoder request.teams )
        ]


editTeamsEncoder : List TeamId -> PlayerKey -> Encode.Value
editTeamsEncoder teams playerKey =
    Encode.object
        [ ( "teams", Encode.list teamIdEncoder teams )
        , ( "auth", authEncoder playerKey )
        ]


lockGameEncoder : GameId -> PlayerKey -> Encode.Value
lockGameEncoder gameId playerKey =
    Encode.object
        [ ( "gameId", gameIdEncoder gameId )
        , ( "auth", authEncoder playerKey )
        ]


unlockGameEncoder : GameId -> PlayerKey -> Encode.Value
unlockGameEncoder gameId playerKey =
    Encode.object
        [ ( "gameId", gameIdEncoder gameId )
        , ( "auth", authEncoder playerKey )
        ]


fetchGameInfoEncoder : GameId -> Encode.Value
fetchGameInfoEncoder gameId =
    Encode.object [ ( "gameId", gameIdEncoder gameId ) ]


authEncoder : PlayerKey -> Encode.Value
authEncoder playerKey =
    Encode.object [ ( "playerKey", playerKeyEncoder playerKey ) ]



-- Wrapper encoders (wrappers.scala)


gameIdEncoder : GameId -> Encode.Value
gameIdEncoder (GameId id) =
    Encode.object [ ( "id", Encode.string id ) ]


gameNameEncoder : GameName -> Encode.Value
gameNameEncoder (GameName name) =
    Encode.object [ ( "name", Encode.string name ) ]


playerKeyEncoder : PlayerKey -> Encode.Value
playerKeyEncoder (PlayerKey key) =
    Encode.object [ ( "key", Encode.string key ) ]


playerNameEncoder : PlayerName -> Encode.Value
playerNameEncoder (PlayerName name) =
    Encode.object [ ( "name", Encode.string name ) ]


teamIdEncoder : TeamId -> Encode.Value
teamIdEncoder (TeamId id) =
    Encode.object [ ( "id", Encode.string id ) ]


competitionCodeEncoder : CompetitionCode -> Encode.Value
competitionCodeEncoder (CompetitionCode code) =
    Encode.object [ ( "code", Encode.string code ) ]


gameSettingsEncoder : GameSettings -> Encode.Value
gameSettingsEncoder settings =
    Encode.object
        [ ( "goalLimit", Encode.int settings.goalLimit )
        , ( "teamCount", Encode.int settings.teamCount )
        ]



-- Response decoders (the circe encoding of the Response enum)


gameCreatedDecoder : Decoder GameCreated
gameCreatedDecoder =
    Decode.field "GameCreated" gamePayloadDecoder


gameJoinedDecoder : Decoder GameJoined
gameJoinedDecoder =
    Decode.field "GameJoined" gamePayloadDecoder


gamePayloadDecoder : Decoder GameCreated
gamePayloadDecoder =
    Decode.map4 GameCreated
        (Decode.field "game" gameDecoder)
        (Decode.field "player" playerDecoder)
        (Decode.field "playerKey" playerKeyDecoder)
        (Decode.field "competitionStats" competitionStatsDecoder)


teamsEditedDecoder : Decoder TeamsEdited
teamsEditedDecoder =
    Decode.field "TeamsEdited"
        (Decode.map2 TeamsEdited
            (Decode.field "teams" (Decode.list teamIdDecoder))
            (Decode.field "playerId" playerIdDecoder)
        )


gameInfoFetchedDecoder : Decoder GameInfoFetched
gameInfoFetchedDecoder =
    Decode.field "GameInfoFetched"
        (Decode.map2 GameInfoFetched
            (Decode.field "game" gameDecoder)
            (Decode.field "competitionStats" competitionStatsDecoder)
        )



-- Error decoder (the circe encoding of the Errors enum)


errorsDecoder : Decoder Errors
errorsDecoder =
    Decode.oneOf
        [ Decode.field "ValidationError"
            (Decode.map2 (\field message -> ValidationError { field = field, message = message })
                (Decode.field "field" Decode.string)
                (Decode.field "message" Decode.string)
            )
        , Decode.field "ValidationErrors"
            (Decode.map2 (\message fields -> ValidationErrors { message = message, fields = fields })
                (Decode.field "message" Decode.string)
                (Decode.field "fields" (Decode.list stringPairDecoder))
            )
        , singletonCase "GameNotFound" GameNotFound
        , singletonCase "CompetitionNotFound" CompetitionNotFound
        , singletonCase "TeamSelectionTaken" TeamSelectionTaken
        , singletonCase "PlayerNameTaken" PlayerNameTaken
        , singletonCase "GameLocked" GameLocked
        , singletonCase "Unauthorized" Unauthorized
        ]


{-| A parameterless enum case: circe encodes these as `{"CaseName": {}}`.
-}
singletonCase : String -> a -> Decoder a
singletonCase name value =
    Decode.field name (Decode.succeed value)


stringPairDecoder : Decoder ( String, String )
stringPairDecoder =
    Decode.map2 Tuple.pair
        (Decode.index 0 Decode.string)
        (Decode.index 1 Decode.string)



-- Domain decoders (game.scala)


gameDecoder : Decoder Game
gameDecoder =
    Decode.map7 Game
        (Decode.field "id" gameIdDecoder)
        (Decode.field "gameName" gameNameDecoder)
        (Decode.field "gameSettings" gameSettingsDecoder)
        (Decode.field "lockState" lockStateDecoder)
        (Decode.field "players" (Decode.list playerDecoder))
        (Decode.field "gameAdmin" playerIdDecoder)
        (Decode.field "competition" competitionDecoder)


lockStateDecoder : Decoder LockState
lockStateDecoder =
    Decode.oneOf
        [ singletonCase "Auto" Auto
        , singletonCase "Locked" Locked
        , singletonCase "Unlocked" Unlocked
        ]


gameSettingsDecoder : Decoder GameSettings
gameSettingsDecoder =
    Decode.map2 GameSettings
        (Decode.field "goalLimit" Decode.int)
        (Decode.field "teamCount" Decode.int)


playerDecoder : Decoder Player
playerDecoder =
    Decode.map3 Player
        (Decode.field "id" playerIdDecoder)
        (Decode.field "name" playerNameDecoder)
        (Decode.field "selection" (Decode.list teamIdDecoder))


competitionDecoder : Decoder Competition
competitionDecoder =
    Decode.map3 Competition
        (Decode.field "id" competitionIdDecoder)
        (Decode.field "code" competitionCodeDecoder)
        (Decode.field "name" competitionNameDecoder)


competitionStatsDecoder : Decoder CompetitionStats
competitionStatsDecoder =
    Decode.map3 CompetitionStats
        (Decode.field "id" competitionIdDecoder)
        (Decode.field "timestamp" Decode.string)
        (Decode.field "teams" teamsMapDecoder)


{-| `CompetitionStats.teams` is a `Map[Team, TeamStats]`: circe encodes the
`Team` keys as their own JSON rendered to a string, so each key needs a second
decoding pass.
-}
teamsMapDecoder : Decoder (List ( Team, TeamStats ))
teamsMapDecoder =
    Decode.keyValuePairs teamStatsDecoder
        |> Decode.andThen
            (\pairs ->
                List.foldr
                    (\( key, stats ) acc ->
                        acc
                            |> Decode.andThen
                                (\decoded ->
                                    case Decode.decodeString teamDecoder key of
                                        Ok team ->
                                            Decode.succeed (( team, stats ) :: decoded)

                                        Err _ ->
                                            Decode.fail ("invalid Team key: " ++ key)
                                )
                    )
                    (Decode.succeed [])
                    pairs
            )


teamDecoder : Decoder Team
teamDecoder =
    Decode.map4 Team
        (Decode.field "id" teamIdDecoder)
        (Decode.field "tla" teamTlaDecoder)
        (Decode.field "name" teamNameDecoder)
        (Decode.field "crestUrl" Decode.string)


teamStatsDecoder : Decoder TeamStats
teamStatsDecoder =
    Decode.map3 TeamStats
        (Decode.field "score" scoreDecoder)
        (Decode.field "progress" progressDecoder)
        (Decode.field "status" statusDecoder)


scoreDecoder : Decoder Score
scoreDecoder =
    Decode.map2 Score
        (Decode.field "goalsFor" Decode.int)
        (Decode.field "goalsAgainst" Decode.int)


progressDecoder : Decoder Progress
progressDecoder =
    Decode.oneOf
        [ singletonCase "NotStarted" NotStarted
        , Decode.field "Group" (Decode.map Group (Decode.field "matchCount" Decode.int))
        , Decode.field "Knockout" (Decode.map Knockout (Decode.field "size" Decode.int))
        , Decode.field "TopFour" (Decode.map TopFour (Decode.field "rank" Decode.int))
        ]


statusDecoder : Decoder Status
statusDecoder =
    Decode.oneOf
        [ singletonCase "Eliminated" Eliminated
        , singletonCase "Playing" Playing
        ]



-- Wrapper decoders (wrappers.scala)


gameIdDecoder : Decoder GameId
gameIdDecoder =
    Decode.map GameId (Decode.field "id" Decode.string)


gameNameDecoder : Decoder GameName
gameNameDecoder =
    Decode.map GameName (Decode.field "name" Decode.string)


playerIdDecoder : Decoder PlayerId
playerIdDecoder =
    Decode.map PlayerId (Decode.field "id" Decode.string)


playerKeyDecoder : Decoder PlayerKey
playerKeyDecoder =
    Decode.map PlayerKey (Decode.field "key" Decode.string)


playerNameDecoder : Decoder PlayerName
playerNameDecoder =
    Decode.map PlayerName (Decode.field "name" Decode.string)


teamIdDecoder : Decoder TeamId
teamIdDecoder =
    Decode.map TeamId (Decode.field "id" Decode.string)


teamTlaDecoder : Decoder TeamTLA
teamTlaDecoder =
    Decode.map TeamTLA (Decode.field "tla" Decode.string)


teamNameDecoder : Decoder TeamName
teamNameDecoder =
    Decode.map2 TeamName
        (Decode.field "short" Decode.string)
        (Decode.field "long" Decode.string)


competitionIdDecoder : Decoder CompetitionId
competitionIdDecoder =
    Decode.map CompetitionId (Decode.field "id" Decode.int)


competitionCodeDecoder : Decoder CompetitionCode
competitionCodeDecoder =
    Decode.map CompetitionCode (Decode.field "code" Decode.string)


competitionNameDecoder : Decoder CompetitionName
competitionNameDecoder =
    Decode.map CompetitionName (Decode.field "name" Decode.string)
