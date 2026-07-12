module Main exposing (main)

import Api
import Browser
import Browser.Navigation as Nav
import Dict exposing (Dict)
import Domain exposing (GameId, PlayerKey(..))
import Element
import Json.Decode as Decode
import Page.Game
import Page.Home
import Ports
import Route exposing (Route)
import Url exposing (Url)
import View


main : Program Decode.Value Model Msg
main =
    Browser.application
        { init = init
        , update = update
        , subscriptions = \_ -> Sub.none
        , view = view
        , onUrlRequest = LinkClicked
        , onUrlChange = UrlChanged
        }



-- MODEL


type alias Model =
    { navKey : Nav.Key
    , playerKeys : Dict String String
    , page : Page
    }


type Page
    = HomePage Page.Home.Model
    | GamePage Page.Game.Model
    | NotFoundPage


init : Decode.Value -> Url -> Nav.Key -> ( Model, Cmd Msg )
init flags url navKey =
    let
        playerKeys : Dict String String
        playerKeys =
            flags
                |> Decode.decodeValue
                    (Decode.field "playerKeys" (Decode.dict Decode.string))
                |> Result.withDefault Dict.empty

        model : Model
        model =
            { navKey = navKey
            , playerKeys = playerKeys
            , page = NotFoundPage
            }
    in
    case Route.fromUrl url of
        Route.Game gameId (Just key) ->
            -- A personal link: store the key (keyed by game) and strip it
            -- from the address bar before entering the page.
            let
                ( updated, cmd ) =
                    enterRoute (Route.Game gameId (Just key))
                        { model | playerKeys = storeKey gameId key model.playerKeys }
            in
            ( updated
            , Cmd.batch
                [ Ports.storePlayerKey
                    { gameId = Domain.gameIdToString gameId
                    , playerKey = Domain.playerKeyToString key
                    }
                , Nav.replaceUrl navKey (Route.toPath (Route.Game gameId Nothing))
                , cmd
                ]
            )

        route ->
            enterRoute route model


storeKey : GameId -> PlayerKey -> Dict String String -> Dict String String
storeKey gameId key playerKeys =
    Dict.insert
        (Domain.gameIdToString gameId)
        (Domain.playerKeyToString key)
        playerKeys


storedKey : GameId -> Dict String String -> Maybe PlayerKey
storedKey gameId playerKeys =
    Dict.get (Domain.gameIdToString gameId) playerKeys
        |> Maybe.map PlayerKey


enterRoute : Route -> Model -> ( Model, Cmd Msg )
enterRoute route model =
    case route of
        Route.Home ->
            let
                ( pageModel, pageCmd ) =
                    Page.Home.init
            in
            ( { model | page = HomePage pageModel }, Cmd.map HomeMsg pageCmd )

        Route.Game gameId _ ->
            case model.page of
                GamePage pageModel ->
                    if Page.Game.gameId pageModel == gameId then
                        -- Already on this game (e.g. the pushUrl after
                        -- create): keep the page state.
                        ( model, Cmd.none )

                    else
                        enterGame gameId model

                _ ->
                    enterGame gameId model

        Route.NotFound ->
            ( { model | page = NotFoundPage }, Cmd.none )


enterGame : GameId -> Model -> ( Model, Cmd Msg )
enterGame gameId model =
    let
        ( pageModel, pageCmd ) =
            Page.Game.init gameId (storedKey gameId model.playerKeys)
    in
    ( { model | page = GamePage pageModel }, Cmd.map GameMsg pageCmd )



-- UPDATE


type Msg
    = LinkClicked Browser.UrlRequest
    | UrlChanged Url
    | HomeMsg Page.Home.Msg
    | GameMsg Page.Game.Msg


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case ( msg, model.page ) of
        ( LinkClicked urlRequest, _ ) ->
            case urlRequest of
                Browser.Internal url ->
                    ( model, Nav.pushUrl model.navKey (Url.toString url) )

                Browser.External href ->
                    ( model, Nav.load href )

        ( UrlChanged url, _ ) ->
            enterRoute (Route.fromUrl url) model

        ( HomeMsg pageMsg, HomePage pageModel ) ->
            let
                ( updated, pageCmd, outMsg ) =
                    Page.Home.update pageMsg pageModel

                withPage : Model
                withPage =
                    { model | page = HomePage updated }
            in
            case outMsg of
                Just (Page.Home.GameCreated created) ->
                    gameCreated created withPage

                Nothing ->
                    ( withPage, Cmd.map HomeMsg pageCmd )

        ( GameMsg pageMsg, GamePage pageModel ) ->
            let
                ( updated, pageCmd, outMsg ) =
                    Page.Game.update pageMsg pageModel

                withPage : Model
                withPage =
                    { model | page = GamePage updated }
            in
            case outMsg of
                Just (Page.Game.KeyAcquired gameId key) ->
                    ( { withPage | playerKeys = storeKey gameId key withPage.playerKeys }
                    , Cmd.batch
                        [ Cmd.map GameMsg pageCmd
                        , Ports.storePlayerKey
                            { gameId = Domain.gameIdToString gameId
                            , playerKey = Domain.playerKeyToString key
                            }
                        ]
                    )

                Nothing ->
                    ( withPage, Cmd.map GameMsg pageCmd )

        _ ->
            ( model, Cmd.none )


{-| After create the creator goes straight to the selection screen: store
their key, seed the game page from the response, and move the address bar to
the game's join path.
-}
gameCreated : Api.GameCreated -> Model -> ( Model, Cmd Msg )
gameCreated created model =
    let
        gameId : GameId
        gameId =
            created.game.id

        ( pageModel, pageCmd ) =
            Page.Game.initFromCreate created
    in
    ( { model
        | playerKeys = storeKey gameId created.playerKey model.playerKeys
        , page = GamePage pageModel
      }
    , Cmd.batch
        [ Cmd.map GameMsg pageCmd
        , Ports.storePlayerKey
            { gameId = Domain.gameIdToString gameId
            , playerKey = Domain.playerKeyToString created.playerKey
            }
        , Nav.pushUrl model.navKey (Route.toPath (Route.Game gameId Nothing))
        ]
    )



-- VIEW


view : Model -> Browser.Document Msg
view model =
    { title = "Football Blackjack"
    , body =
        [ View.layout
            (case model.page of
                HomePage pageModel ->
                    Element.map HomeMsg (Page.Home.view pageModel)

                GamePage pageModel ->
                    Element.map GameMsg (Page.Game.view pageModel)

                NotFoundPage ->
                    notFoundView
            )
        ]
    }


notFoundView : Element.Element Msg
notFoundView =
    Element.el [ Element.centerX, Element.centerY ]
        (Element.text "Page not found")
