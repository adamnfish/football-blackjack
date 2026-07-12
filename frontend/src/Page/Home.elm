module Page.Home exposing (Model, Msg, init, update, view)

{-| Home page: the create-game form arrives with the phase 2 flows; for now
this is the hello-world placeholder.
-}

import Element exposing (..)
import Element.Font as Font


type alias Model =
    {}


type Msg
    = NoOp


init : ( Model, Cmd Msg )
init =
    ( {}, Cmd.none )


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        NoOp ->
            ( model, Cmd.none )


view : Model -> Element Msg
view _ =
    el
        [ centerX
        , centerY
        , Font.size 48
        , Font.bold
        ]
        (text "Hello, world!")
