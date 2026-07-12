module ApiCodecTest exposing (suite)

{-| Golden-sample codec tests: the JSON files in `fixtures/api/` are shared
with the Scala side, so both codecs are asserted against the same wire
format. Request encoders are compared structurally (key order and whitespace
insensitive); response and error decoders are checked against expected
values.
-}

import Api
import Dict
import Domain exposing (..)
import Expect exposing (Expectation)
import Generated.Fixtures exposing (fixtures)
import Json.Decode as Decode exposing (Decoder)
import Json.Encode as Encode
import Test exposing (Test, describe, test)



-- Canonical JSON representation for structural comparison


type Json
    = JString String
    | JNumber Float
    | JBool Bool
    | JNull
    | JArray (List Json)
    | JObject (List ( String, Json ))


jsonDecoder : Decoder Json
jsonDecoder =
    Decode.oneOf
        [ Decode.map JString Decode.string
        , Decode.map JNumber Decode.float
        , Decode.map JBool Decode.bool
        , Decode.null JNull
        , Decode.map JArray (Decode.list (Decode.lazy (\_ -> jsonDecoder)))
        , Decode.map (\pairs -> JObject (List.sortBy Tuple.first pairs))
            (Decode.keyValuePairs (Decode.lazy (\_ -> jsonDecoder)))
        ]


withFixture : String -> (String -> Expectation) -> Expectation
withFixture name toExpectation =
    case Dict.get name fixtures of
        Just json ->
            toExpectation json

        Nothing ->
            Expect.fail ("missing fixture: " ++ name)


encodesAs : String -> Encode.Value -> Expectation
encodesAs name value =
    withFixture name
        (\json ->
            Expect.equal
                (Decode.decodeString jsonDecoder json
                    |> Result.mapError Decode.errorToString
                )
                (Decode.decodeValue jsonDecoder value
                    |> Result.mapError Decode.errorToString
                )
        )


decodesAs : String -> Decoder a -> a -> Expectation
decodesAs name decoder expected =
    withFixture name
        (\json ->
            Expect.equal
                (Decode.decodeString decoder json
                    |> Result.mapError Decode.errorToString
                )
                (Ok expected)
        )



-- The fixtures' shared cast


competition : Competition
competition =
    { id = CompetitionId 1
    , code = CompetitionCode "WC"
    , name = CompetitionName "World Cup"
    }


england : Team
england =
    { id = TeamId "england"
    , tla = TeamTLA "ENG"
    , name = { short = "England", long = "England National Team" }
    , crestUrl = "https://crests.example.com/england.svg"
    }


scotland : Team
scotland =
    { id = TeamId "scotland"
    , tla = TeamTLA "SCO"
    , name = { short = "Scotland", long = "Scotland National Team" }
    , crestUrl = ""
    }


france : Team
france =
    { id = TeamId "france"
    , tla = TeamTLA "FRA"
    , name = { short = "France", long = "France National Team" }
    , crestUrl = "https://crests.example.com/france.svg"
    }


brazil : Team
brazil =
    { id = TeamId "brazil"
    , tla = TeamTLA "BRA"
    , name = { short = "Brazil", long = "Brazil National Team" }
    , crestUrl = "https://crests.example.com/brazil.svg"
    }


notStarted : TeamStats
notStarted =
    { score = { goalsFor = 0, goalsAgainst = 0 }
    , progress = NotStarted
    , status = Playing
    }


alice : Player
alice =
    { id = PlayerId "p-1", name = PlayerName "Alice", selection = [] }


suite : Test
suite =
    describe "Api codecs against the shared golden samples"
        [ describe "request encoders"
            [ test "create-game" <|
                \_ ->
                    encodesAs "request-create-game"
                        (Api.createGameEncoder
                            { gameName = GameName "Office sweepstake"
                            , gameSettings = { goalLimit = 25, teamCount = 4 }
                            , competitionCode = CompetitionCode "WC"
                            , playerName = PlayerName "Alice"
                            }
                        )
            , test "join-game" <|
                \_ ->
                    encodesAs "request-join-game"
                        (Api.joinGameEncoder
                            { gameId = GameId "g-123"
                            , playerName = PlayerName "Bob"
                            , teams = [ TeamId "england", TeamId "brazil" ]
                            }
                        )
            , test "edit-teams" <|
                \_ ->
                    encodesAs "request-edit-teams"
                        (Api.editTeamsEncoder (GameId "g-123")
                            [ TeamId "england", TeamId "scotland" ]
                            (PlayerKey "key-abc")
                        )
            , test "lock-game" <|
                \_ ->
                    encodesAs "request-lock-game"
                        (Api.lockGameEncoder (GameId "g-123") (PlayerKey "key-abc"))
            , test "unlock-game" <|
                \_ ->
                    encodesAs "request-unlock-game"
                        (Api.unlockGameEncoder (GameId "g-123") (PlayerKey "key-abc"))
            , test "fetch-game-info" <|
                \_ ->
                    encodesAs "request-fetch-game-info"
                        (Api.fetchGameInfoEncoder (GameId "g-123"))
            , test "fetch-player-keys" <|
                \_ ->
                    encodesAs "request-fetch-player-keys"
                        (Api.fetchPlayerKeysEncoder (GameId "g-123") (PlayerKey "key-abc"))
            ]
        , describe "response decoders"
            [ test "game-created" <|
                \_ ->
                    decodesAs "response-game-created"
                        Api.gameCreatedDecoder
                        { game =
                            { id = GameId "g-123"
                            , gameName = GameName "Office sweepstake"
                            , gameSettings = { goalLimit = 25, teamCount = 4 }
                            , lockState = Auto
                            , players = [ alice ]
                            , gameAdmin = PlayerId "p-1"
                            , competition = competition
                            }
                        , player = alice
                        , playerKey = PlayerKey "key-abc"
                        , competitionStats =
                            { id = CompetitionId 1
                            , timestamp = "2026-06-11T18:00:00Z"
                            , teams = [ ( england, notStarted ), ( scotland, notStarted ) ]
                            }
                        }
            , test "game-joined" <|
                \_ ->
                    let
                        aliceWithTeams : Player
                        aliceWithTeams =
                            { alice | selection = [ TeamId "england", TeamId "scotland" ] }

                        bob : Player
                        bob =
                            { id = PlayerId "p-2"
                            , name = PlayerName "Bob"
                            , selection = [ TeamId "england", TeamId "brazil" ]
                            }
                    in
                    decodesAs "response-game-joined"
                        Api.gameJoinedDecoder
                        { game =
                            { id = GameId "g-123"
                            , gameName = GameName "Office sweepstake"
                            , gameSettings = { goalLimit = 25, teamCount = 2 }
                            , lockState = Auto
                            , players = [ aliceWithTeams, bob ]
                            , gameAdmin = PlayerId "p-1"
                            , competition = competition
                            }
                        , player = bob
                        , playerKey = PlayerKey "key-def"
                        , competitionStats =
                            { id = CompetitionId 1
                            , timestamp = "2026-06-11T18:00:00Z"
                            , teams = [ ( england, notStarted ) ]
                            }
                        }
            , test "teams-edited" <|
                \_ ->
                    decodesAs "response-teams-edited"
                        Api.teamsEditedDecoder
                        { teams = [ TeamId "england", TeamId "scotland" ]
                        , playerId = PlayerId "p-1"
                        }
            , test "game-info-fetched" <|
                \_ ->
                    decodesAs "response-game-info-fetched"
                        Api.gameInfoFetchedDecoder
                        { game =
                            { id = GameId "g-123"
                            , gameName = GameName "Office sweepstake"
                            , gameSettings = { goalLimit = 25, teamCount = 2 }
                            , lockState = Locked
                            , players =
                                [ { alice
                                    | selection = [ TeamId "england", TeamId "france" ]
                                  }
                                ]
                            , gameAdmin = PlayerId "p-1"
                            , competition = competition
                            }
                        , competitionStats =
                            { id = CompetitionId 1
                            , timestamp = "2026-07-02T20:30:00Z"
                            , teams =
                                [ ( england
                                  , { score = { goalsFor = 7, goalsAgainst = 2 }
                                    , progress = Group 3
                                    , status = Playing
                                    }
                                  )
                                , ( france
                                  , { score = { goalsFor = 11, goalsAgainst = 3 }
                                    , progress = TopFour 1
                                    , status = Playing
                                    }
                                  )
                                , ( brazil
                                  , { score = { goalsFor = 5, goalsAgainst = 4 }
                                    , progress = Knockout 8
                                    , status = Eliminated
                                    }
                                  )
                                ]
                            }
                        }
            , test "player-keys-fetched" <|
                \_ ->
                    decodesAs "response-player-keys-fetched"
                        Api.playerKeysFetchedDecoder
                        [ ( PlayerId "p-1", PlayerKey "key-abc" )
                        , ( PlayerId "p-2", PlayerKey "key-def" )
                        ]
            , test "game-locked" <|
                \_ ->
                    decodesAs "response-game-locked"
                        (Decode.field "GameLocked" (Decode.succeed ()))
                        ()
            , test "game-unlocked" <|
                \_ ->
                    decodesAs "response-game-unlocked"
                        (Decode.field "GameUnlocked" (Decode.succeed ()))
                        ()
            ]
        , describe "error decoder"
            [ test "validation-error" <|
                \_ ->
                    decodesAs "error-validation-error"
                        Api.errorsDecoder
                        (ValidationError
                            { field = "gameName", message = "game name is required" }
                        )
            , test "validation-errors" <|
                \_ ->
                    decodesAs "error-validation-errors"
                        Api.errorsDecoder
                        (ValidationErrors
                            { message = "invalid request"
                            , fields =
                                [ ( "gameName", "game name is required" )
                                , ( "teamCount", "team count must be at least 1" )
                                ]
                            }
                        )
            , test "game-not-found" <|
                \_ ->
                    decodesAs "error-game-not-found" Api.errorsDecoder GameNotFound
            , test "competition-not-found" <|
                \_ ->
                    decodesAs "error-competition-not-found"
                        Api.errorsDecoder
                        CompetitionNotFound
            , test "team-selection-taken" <|
                \_ ->
                    decodesAs "error-team-selection-taken"
                        Api.errorsDecoder
                        TeamSelectionTaken
            , test "player-name-taken" <|
                \_ ->
                    decodesAs "error-player-name-taken" Api.errorsDecoder PlayerNameTaken
            , test "game-locked" <|
                \_ ->
                    decodesAs "error-game-locked" Api.errorsDecoder GameLocked
            , test "unauthorized" <|
                \_ ->
                    decodesAs "error-unauthorized" Api.errorsDecoder Unauthorized
            ]
        ]
