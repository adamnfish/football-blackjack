module Page.Game exposing
    ( Model
    , Msg
    , OutMsg(..)
    , gameId
    , init
    , initFromCreate
    , update
    , view
    )

{-| Game page state machine: not-joined (and unlocked) leads through the join
form to the selection screen; joined shows the game view. The creator enters
the same selection screen straight after create (via `initFromCreate`).

Which player we are is only known in-session (from the create/join
responses): a player key alone cannot be matched to a player, so arriving by
personal link shows the game view without self-identification.

-}

import Api exposing (ApiError(..))
import Domain exposing (..)
import Element exposing (..)
import Element.Background as Background
import Element.Border as Border
import Element.Events as Events
import Element.Font as Font
import Html.Attributes
import View


type alias Model =
    { gameId : GameId
    , playerKey : Maybe PlayerKey
    , self : Maybe Player
    , state : State
    }


type State
    = Loading
    | LoadFailed String
    | Loaded LoadedGame


type alias LoadedGame =
    { game : Game
    , stats : CompetitionStats
    , stage : Stage
    }


type Stage
    = Viewing
    | Joining { name : String, error : Maybe String }
    | Selecting SelectionForm


type alias SelectionForm =
    { intent : SelectionIntent
    , selected : List TeamId
    , submitting : Bool
    , error : Maybe String
    }


type SelectionIntent
    = JoiningAs PlayerName
    | EditingSelection PlayerKey


type Msg
    = GameInfoResponded (Result ApiError Api.GameInfoFetched)
    | JoinNameChanged String
    | JoinNameSubmitted
    | TeamToggled TeamId
    | SelectionSubmitted
    | JoinResponded (Result ApiError Api.GameJoined)
    | EditTeamsResponded (Result ApiError Api.TeamsEdited)
    | PickTeamsClicked


type OutMsg
    = KeyAcquired GameId PlayerKey


gameId : Model -> GameId
gameId model =
    model.gameId


init : GameId -> Maybe PlayerKey -> ( Model, Cmd Msg )
init id playerKey =
    ( { gameId = id
      , playerKey = playerKey
      , self = Nothing
      , state = Loading
      }
    , Api.fetchGameInfo id GameInfoResponded
    )


{-| The creator goes straight into the selection screen with the freshly
created game; no fetch needed.
-}
initFromCreate : Api.GameCreated -> ( Model, Cmd Msg )
initFromCreate created =
    ( { gameId = created.game.id
      , playerKey = Just created.playerKey
      , self = Just created.player
      , state =
            Loaded
                { game = created.game
                , stats = created.competitionStats
                , stage = Selecting (selectionForm (EditingSelection created.playerKey))
                }
      }
    , Cmd.none
    )


selectionForm : SelectionIntent -> SelectionForm
selectionForm intent =
    { intent = intent
    , selected = []
    , submitting = False
    , error = Nothing
    }


update : Msg -> Model -> ( Model, Cmd Msg, Maybe OutMsg )
update msg model =
    case msg of
        GameInfoResponded (Ok info) ->
            ( { model
                | state =
                    Loaded
                        { game = info.game
                        , stats = info.competitionStats
                        , stage = arrivalStage model info.game info.competitionStats
                        }
              }
            , Cmd.none
            , Nothing
            )

        GameInfoResponded (Err apiError) ->
            ( { model | state = LoadFailed (Api.errorMessage apiError) }
            , Cmd.none
            , Nothing
            )

        JoinNameChanged value ->
            ( updateStage model
                (\stage ->
                    case stage of
                        Joining form ->
                            Joining { form | name = value }

                        other ->
                            other
                )
            , Cmd.none
            , Nothing
            )

        JoinNameSubmitted ->
            ( updateStage model
                (\stage ->
                    case stage of
                        Joining form ->
                            if String.isEmpty (String.trim form.name) then
                                Joining { form | error = Just "Enter your name" }

                            else
                                Selecting
                                    (selectionForm
                                        (JoiningAs (PlayerName (String.trim form.name)))
                                    )

                        other ->
                            other
                )
            , Cmd.none
            , Nothing
            )

        TeamToggled teamId ->
            ( withLoaded model
                (\loaded ->
                    case loaded.stage of
                        Selecting form ->
                            { loaded
                                | stage =
                                    Selecting (toggleTeam loaded.game teamId form)
                            }

                        _ ->
                            loaded
                )
            , Cmd.none
            , Nothing
            )

        SelectionSubmitted ->
            submitSelection model

        JoinResponded (Ok joined) ->
            ( { model
                | playerKey = Just joined.playerKey
                , self = Just joined.player
                , state =
                    Loaded
                        { game = joined.game
                        , stats = joined.competitionStats
                        , stage = Viewing
                        }
              }
            , Cmd.none
            , Just (KeyAcquired joined.game.id joined.playerKey)
            )

        JoinResponded (Err apiError) ->
            ( selectionFailed model apiError, Cmd.none, Nothing )

        EditTeamsResponded (Ok edited) ->
            ( withLoaded
                { model | self = Maybe.map (setSelection edited) model.self }
                (\loaded ->
                    { loaded
                        | game = applyEdit edited loaded.game
                        , stage = Viewing
                    }
                )
            , Cmd.none
            , Nothing
            )

        EditTeamsResponded (Err apiError) ->
            ( selectionFailed model apiError, Cmd.none, Nothing )

        PickTeamsClicked ->
            case model.playerKey of
                Just key ->
                    ( updateStage model
                        (\stage ->
                            case stage of
                                Viewing ->
                                    Selecting (selectionForm (EditingSelection key))

                                other ->
                                    other
                        )
                    , Cmd.none
                    , Nothing
                    )

                Nothing ->
                    ( model, Cmd.none, Nothing )


{-| The stage to enter after fetching game info on arrival: players with a
stored key (or a locked game's visitors) see the game view; anyone else is
offered the join flow.
-}
arrivalStage : Model -> Game -> CompetitionStats -> Stage
arrivalStage model game stats =
    if model.playerKey /= Nothing then
        Viewing

    else if isEffectivelyLocked game stats then
        Viewing

    else
        Joining { name = "", error = Nothing }


toggleTeam : Game -> TeamId -> SelectionForm -> SelectionForm
toggleTeam game teamId form =
    if List.member teamId form.selected then
        { form | selected = List.filter ((/=) teamId) form.selected }

    else if List.length form.selected < game.gameSettings.teamCount then
        { form | selected = form.selected ++ [ teamId ] }

    else
        form


submitSelection : Model -> ( Model, Cmd Msg, Maybe OutMsg )
submitSelection model =
    case model.state of
        Loaded loaded ->
            case loaded.stage of
                Selecting form ->
                    if List.length form.selected /= loaded.game.gameSettings.teamCount then
                        ( setStage model
                            (Selecting
                                { form
                                    | error =
                                        Just
                                            ("Pick "
                                                ++ String.fromInt loaded.game.gameSettings.teamCount
                                                ++ " teams"
                                            )
                                }
                            )
                        , Cmd.none
                        , Nothing
                        )

                    else
                        ( setStage model (Selecting { form | submitting = True, error = Nothing })
                        , case form.intent of
                            JoiningAs playerName ->
                                Api.joinGame
                                    { gameId = model.gameId
                                    , playerName = playerName
                                    , teams = form.selected
                                    }
                                    JoinResponded

                            EditingSelection key ->
                                Api.editTeams model.gameId form.selected key EditTeamsResponded
                        , Nothing
                        )

                _ ->
                    ( model, Cmd.none, Nothing )

        _ ->
            ( model, Cmd.none, Nothing )


{-| A failed join/edit lands back on the selection screen with a friendly
message; a taken name goes back to the join form instead.
-}
selectionFailed : Model -> ApiError -> Model
selectionFailed model apiError =
    updateStage model
        (\stage ->
            case stage of
                Selecting form ->
                    case ( apiError, form.intent ) of
                        ( ApplicationError PlayerNameTaken, JoiningAs playerName ) ->
                            Joining
                                { name = playerNameToString playerName
                                , error = Just (Api.errorMessage apiError)
                                }

                        ( ApplicationError TeamSelectionTaken, _ ) ->
                            Selecting
                                { form
                                    | submitting = False
                                    , error =
                                        Just
                                            ("That exact combination of teams is already taken"
                                                ++ " - change at least one team and try again."
                                            )
                                }

                        _ ->
                            Selecting
                                { form
                                    | submitting = False
                                    , error = Just (Api.errorMessage apiError)
                                }

                other ->
                    other
        )


setSelection : Api.TeamsEdited -> Player -> Player
setSelection edited player =
    if player.id == edited.playerId then
        { player | selection = edited.teams }

    else
        player


applyEdit : Api.TeamsEdited -> Game -> Game
applyEdit edited game =
    { game | players = List.map (setSelection edited) game.players }


updateStage : Model -> (Stage -> Stage) -> Model
updateStage model transform =
    withLoaded model (\loaded -> { loaded | stage = transform loaded.stage })


setStage : Model -> Stage -> Model
setStage model stage =
    updateStage model (always stage)


withLoaded : Model -> (LoadedGame -> LoadedGame) -> Model
withLoaded model transform =
    case model.state of
        Loaded loaded ->
            { model | state = Loaded (transform loaded) }

        _ ->
            model



-- VIEW


view : Model -> Element Msg
view model =
    case model.state of
        Loading ->
            el [ centerX, centerY, Font.color View.colors.muted ]
                (text "Loading game...")

        LoadFailed message ->
            column [ centerX, centerY, spacing 12 ]
                [ el [ centerX, Font.size 24, Font.bold ] (text "Could not load game")
                , el
                    [ centerX
                    , Font.color View.colors.muted
                    , View.testId "game-load-error"
                    ]
                    (text message)
                ]

        Loaded loaded ->
            column
                [ centerX
                , paddingXY 20 40
                , spacing 24
                , width (fill |> maximum 900)
                ]
                (header loaded.game
                    :: (case loaded.stage of
                            Viewing ->
                                gameView model loaded

                            Joining form ->
                                joinView form

                            Selecting form ->
                                selectionView loaded form
                       )
                )


header : Game -> Element Msg
header game =
    column [ spacing 8, centerX ]
        [ el
            [ centerX
            , Font.size 32
            , Font.bold
            , View.testId "game-title"
            ]
            (text (gameNameToString game.gameName))
        , el [ centerX, Font.color View.colors.muted, Font.size 16 ]
            (text (competitionNameToString game.competition.name))
        ]


competitionNameToString : CompetitionName -> String
competitionNameToString (CompetitionName name) =
    name



-- Join form


joinView : { name : String, error : Maybe String } -> List (Element Msg)
joinView form =
    [ column [ centerX, spacing 16, width (fill |> maximum 480) ]
        [ paragraph [ Font.center, Font.color View.colors.muted ]
            [ text "You've been invited to join this game." ]
        , View.textField
            { testIdValue = "join-player-name"
            , label = "Your name"
            , value = form.name
            , onChange = JoinNameChanged
            }
        , case form.error of
            Just problem ->
                View.errorText "join-error" problem

            Nothing ->
                none
        , View.primaryButton
            { testIdValue = "join-continue"
            , label = "Choose teams"
            , onPress = Just JoinNameSubmitted
            }
        ]
    ]



-- Selection screen


selectionView : LoadedGame -> SelectionForm -> List (Element Msg)
selectionView loaded form =
    let
        teamCount : Int
        teamCount =
            loaded.game.gameSettings.teamCount
    in
    [ el [ centerX, Font.size 20, View.testId "selection-count" ]
        (text
            ("Picked "
                ++ String.fromInt (List.length form.selected)
                ++ " of "
                ++ String.fromInt teamCount
                ++ " teams"
            )
        )
    , teamGrid loaded form
    , case form.error of
        Just problem ->
            el [ centerX ] (View.errorText "selection-error" problem)

        Nothing ->
            none
    , el [ centerX ]
        (View.primaryButton
            { testIdValue = "selection-submit"
            , label =
                if form.submitting then
                    "Saving..."

                else
                    "Confirm teams"
            , onPress =
                if form.submitting then
                    Nothing

                else
                    Just SelectionSubmitted
            }
        )
    , otherSelections loaded
    ]


teamGrid : LoadedGame -> SelectionForm -> Element Msg
teamGrid loaded form =
    wrappedRow [ spacing 12, width fill ]
        (loaded.stats.teams
            |> List.map Tuple.first
            |> List.sortBy (\team -> team.name.short)
            |> List.map (teamCard form)
        )


teamCard : SelectionForm -> Team -> Element Msg
teamCard form team =
    let
        selected : Bool
        selected =
            List.member team.id form.selected
    in
    column
        [ View.testId "team-card"
        , htmlAttributeDataTeamId team.id
        , Events.onClick (TeamToggled team.id)
        , pointer
        , width (px 140)
        , padding 12
        , spacing 8
        , Border.rounded 6
        , Border.width 2
        , Border.color
            (if selected then
                View.colors.accent

             else
                View.colors.surface
            )
        , Background.color View.colors.surface
        ]
        [ crest team
        , el [ centerX, Font.size 16 ] (text team.name.short)
        , el [ centerX, Font.size 12, Font.color View.colors.muted ]
            (text (teamTlaToString team.tla))
        ]


htmlAttributeDataTeamId : TeamId -> Attribute msg
htmlAttributeDataTeamId teamId =
    htmlAttribute
        (Html.Attributes.attribute "data-team-id" (teamIdToString teamId))


teamTlaToString : TeamTLA -> String
teamTlaToString (TeamTLA tla) =
    tla


{-| Team crest, or a TLA placeholder when there is no crest URL.
-}
crest : Team -> Element msg
crest team =
    if String.isEmpty team.crestUrl then
        el
            [ centerX
            , width (px 48)
            , height (px 48)
            , Border.rounded 24
            , Background.color View.colors.background
            , Font.size 14
            , Font.color View.colors.muted
            ]
            (el [ centerX, centerY ] (text (teamTlaToString team.tla)))

    else
        image [ centerX, width (px 48), height (px 48) ]
            { src = team.crestUrl
            , description = team.name.short ++ " crest"
            }


otherSelections : LoadedGame -> Element Msg
otherSelections loaded =
    let
        playersWithSelections : List Player
        playersWithSelections =
            List.filter (\player -> not (List.isEmpty player.selection))
                loaded.game.players
    in
    if List.isEmpty playersWithSelections then
        none

    else
        column [ spacing 8, width fill ]
            (el [ Font.size 18, Font.bold ] (text "Already picked")
                :: List.map (playerSelectionRow loaded.stats) playersWithSelections
            )



-- Game view (placeholder: players and their selections)


gameView : Model -> LoadedGame -> List (Element Msg)
gameView model loaded =
    [ el [ centerX, Font.color View.colors.muted, View.testId "game-view" ]
        (text (lockDescription loaded))
    , column [ spacing 8, width fill ]
        (el [ Font.size 18, Font.bold ] (text "Players")
            :: List.map (playerSelectionRow loaded.stats) loaded.game.players
        )
    , pickTeamsPrompt model loaded
    ]


lockDescription : LoadedGame -> String
lockDescription loaded =
    if isEffectivelyLocked loaded.game loaded.stats then
        "The game is locked - selections are final."

    else
        "The game is open - share this page's link to invite players."


{-| The creator (or any in-session player) who has not picked yet gets a way
into the selection screen from the game view.
-}
pickTeamsPrompt : Model -> LoadedGame -> Element Msg
pickTeamsPrompt model loaded =
    case model.self of
        Just self ->
            if
                List.isEmpty self.selection
                    && not (isEffectivelyLocked loaded.game loaded.stats)
                    && (model.playerKey /= Nothing)
            then
                el [ centerX ]
                    (View.primaryButton
                        { testIdValue = "pick-teams"
                        , label = "Pick your teams"
                        , onPress = Just PickTeamsClicked
                        }
                    )

            else
                none

        Nothing ->
            none


playerSelectionRow : CompetitionStats -> Player -> Element Msg
playerSelectionRow stats player =
    row
        [ View.testId "player-row"
        , spacing 16
        , width fill
        , padding 12
        , Background.color View.colors.surface
        , Border.rounded 6
        ]
        [ el [ Font.bold ] (text (playerNameToString player.name))
        , el [ Font.color View.colors.muted, Font.size 16 ]
            (text (selectionSummary stats player))
        ]


selectionSummary : CompetitionStats -> Player -> String
selectionSummary stats player =
    if List.isEmpty player.selection then
        "No teams picked yet"

    else
        player.selection
            |> List.map
                (\teamId ->
                    findTeam teamId stats
                        |> Maybe.map (\team -> team.name.short)
                        |> Maybe.withDefault (teamIdToString teamId)
                )
            |> String.join ", "
