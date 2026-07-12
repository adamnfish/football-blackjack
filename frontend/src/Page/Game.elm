module Page.Game exposing (Model, Msg, init, update, view)

{-| Game page: join/selection/game view arrive with the phase 2 flows; for now
it shows the game id from the route. The player key (from a personal link or
localStorage) is threaded through ready for those flows.
-}

import Domain exposing (GameId, PlayerKey)
import Element exposing (..)
import Element.Font as Font
import View


type alias Model =
    { gameId : GameId
    , playerKey : Maybe PlayerKey
    }


type Msg
    = NoOp


init : GameId -> Maybe PlayerKey -> ( Model, Cmd Msg )
init gameId playerKey =
    ( { gameId = gameId, playerKey = playerKey }, Cmd.none )


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        NoOp ->
            ( model, Cmd.none )


view : Model -> Element Msg
view model =
    column [ centerX, centerY, spacing 12 ]
        [ el [ centerX, Font.size 32, Font.bold ]
            (text ("Game " ++ Domain.gameIdToString model.gameId))
        , el [ centerX, Font.color View.colors.muted ]
            (text "Coming soon")
        ]
