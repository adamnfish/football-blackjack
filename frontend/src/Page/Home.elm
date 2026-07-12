module Page.Home exposing (Model, Msg, OutMsg(..), init, update, view)

{-| Home page: the create-game form. `CreateGame` is slim (names + settings);
the creator picks their teams straight afterwards on the same selection
screen joiners use.
-}

import Api exposing (ApiError)
import Domain exposing (..)
import Element exposing (..)
import Element.Font as Font
import View


type alias Model =
    { playerName : String
    , gameName : String
    , goalLimit : String
    , teamCount : String
    , competitionCode : String
    , submitting : Bool
    , error : Maybe String
    }


type Msg
    = PlayerNameChanged String
    | GameNameChanged String
    | GoalLimitChanged String
    | TeamCountChanged String
    | CompetitionCodeChanged String
    | SubmitCreate
    | CreateGameResponded (Result ApiError Api.GameCreated)


type OutMsg
    = GameCreated Api.GameCreated


init : ( Model, Cmd Msg )
init =
    ( { playerName = ""
      , gameName = ""
      , goalLimit = "25"
      , teamCount = "4"
      , competitionCode = "WC"
      , submitting = False
      , error = Nothing
      }
    , Cmd.none
    )


update : Msg -> Model -> ( Model, Cmd Msg, Maybe OutMsg )
update msg model =
    case msg of
        PlayerNameChanged value ->
            ( { model | playerName = value }, Cmd.none, Nothing )

        GameNameChanged value ->
            ( { model | gameName = value }, Cmd.none, Nothing )

        GoalLimitChanged value ->
            ( { model | goalLimit = value }, Cmd.none, Nothing )

        TeamCountChanged value ->
            ( { model | teamCount = value }, Cmd.none, Nothing )

        CompetitionCodeChanged value ->
            ( { model | competitionCode = value }, Cmd.none, Nothing )

        SubmitCreate ->
            case createRequest model of
                Ok request ->
                    ( { model | submitting = True, error = Nothing }
                    , Api.createGame request CreateGameResponded
                    , Nothing
                    )

                Err problem ->
                    ( { model | error = Just problem }, Cmd.none, Nothing )

        CreateGameResponded (Ok created) ->
            ( { model | submitting = False }
            , Cmd.none
            , Just (GameCreated created)
            )

        CreateGameResponded (Err apiError) ->
            ( { model | submitting = False, error = Just (Api.errorMessage apiError) }
            , Cmd.none
            , Nothing
            )


createRequest : Model -> Result String Api.CreateGameRequest
createRequest model =
    if String.isEmpty (String.trim model.playerName) then
        Err "Enter your name"

    else if String.isEmpty (String.trim model.gameName) then
        Err "Enter a name for the game"

    else
        case ( String.toInt model.goalLimit, String.toInt model.teamCount ) of
            ( Nothing, _ ) ->
                Err "Goal limit must be a number"

            ( _, Nothing ) ->
                Err "Teams per player must be a number"

            ( Just goalLimit, Just teamCount ) ->
                Ok
                    { gameName = GameName (String.trim model.gameName)
                    , gameSettings = { goalLimit = goalLimit, teamCount = teamCount }
                    , competitionCode = CompetitionCode (String.trim model.competitionCode)
                    , playerName = PlayerName (String.trim model.playerName)
                    }


view : Model -> Element Msg
view model =
    column
        [ centerX
        , paddingXY 20 60
        , spacing 24
        , width (fill |> maximum 480)
        ]
        [ el [ centerX, Font.size 40, Font.bold ] (text "Football Blackjack")
        , paragraph [ Font.color View.colors.muted, Font.center ]
            [ text "Create a game and share the link with your players." ]
        , View.textField
            { testIdValue = "create-player-name"
            , label = "Your name"
            , value = model.playerName
            , onChange = PlayerNameChanged
            }
        , View.textField
            { testIdValue = "create-game-name"
            , label = "Game name"
            , value = model.gameName
            , onChange = GameNameChanged
            }
        , row [ spacing 16, width fill ]
            [ View.textField
                { testIdValue = "create-goal-limit"
                , label = "Goal target"
                , value = model.goalLimit
                , onChange = GoalLimitChanged
                }
            , View.textField
                { testIdValue = "create-team-count"
                , label = "Teams per player"
                , value = model.teamCount
                , onChange = TeamCountChanged
                }
            ]
        , View.textField
            { testIdValue = "create-competition-code"
            , label = "Competition code"
            , value = model.competitionCode
            , onChange = CompetitionCodeChanged
            }
        , case model.error of
            Just problem ->
                View.errorText "create-error" problem

            Nothing ->
                none
        , View.primaryButton
            { testIdValue = "create-submit"
            , label =
                if model.submitting then
                    "Creating..."

                else
                    "Create game"
            , onPress =
                if model.submitting then
                    Nothing

                else
                    Just SubmitCreate
            }
        ]
