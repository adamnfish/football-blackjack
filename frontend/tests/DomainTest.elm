module DomainTest exposing (suite)

import Domain exposing (..)
import Expect
import Test exposing (Test, describe, test)


team : String -> Team
team id =
    { id = TeamId id
    , tla = TeamTLA (String.toUpper (String.left 3 id))
    , name = { short = id, long = id }
    , crestUrl = ""
    }


statsWith : List ( String, Progress ) -> CompetitionStats
statsWith teams =
    { id = CompetitionId 1
    , timestamp = "2026-06-11T18:00:00Z"
    , teams =
        List.map
            (\( id, progress ) ->
                ( team id
                , { score = { goalsFor = 0, goalsAgainst = 0 }
                  , progress = progress
                  , status = Playing
                  }
                )
            )
            teams
    }


game : LockState -> Game
game lockState =
    { id = GameId "g-1"
    , gameName = GameName "game"
    , gameSettings = { goalLimit = 25, teamCount = 4 }
    , lockState = lockState
    , players = []
    , gameAdmin = PlayerId "p-1"
    , competition =
        { id = CompetitionId 1
        , code = CompetitionCode "WC"
        , name = CompetitionName "World Cup"
        }
    }


preTournament : CompetitionStats
preTournament =
    statsWith [ ( "england", NotStarted ), ( "brazil", NotStarted ) ]


midTournament : CompetitionStats
midTournament =
    statsWith [ ( "england", Group 1 ), ( "brazil", NotStarted ) ]


suite : Test
suite =
    describe "Domain"
        [ describe "competitionHasStarted"
            [ test "not started while every team is NotStarted" <|
                \_ ->
                    competitionHasStarted preTournament
                        |> Expect.equal False
            , test "started once any team has progress" <|
                \_ ->
                    competitionHasStarted midTournament
                        |> Expect.equal True
            , test "not started with no teams" <|
                \_ ->
                    competitionHasStarted (statsWith [])
                        |> Expect.equal False
            ]
        , describe "isEffectivelyLocked"
            [ test "Auto is open before the tournament" <|
                \_ ->
                    isEffectivelyLocked (game Auto) preTournament
                        |> Expect.equal False
            , test "Auto locks once the tournament has started" <|
                \_ ->
                    isEffectivelyLocked (game Auto) midTournament
                        |> Expect.equal True
            , test "Locked is locked even before the tournament" <|
                \_ ->
                    isEffectivelyLocked (game Locked) preTournament
                        |> Expect.equal True
            , test "Unlocked overrides the auto-lock mid-tournament" <|
                \_ ->
                    isEffectivelyLocked (game Unlocked) midTournament
                        |> Expect.equal False
            ]
        , describe "findTeam"
            [ test "finds a team by id" <|
                \_ ->
                    findTeam (TeamId "england") midTournament
                        |> Maybe.map .id
                        |> Expect.equal (Just (TeamId "england"))
            , test "Nothing for an unknown id" <|
                \_ ->
                    findTeam (TeamId "narnia") midTournament
                        |> Expect.equal Nothing
            ]
        ]
