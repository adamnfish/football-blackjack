module View exposing (view)

import Element exposing (..)
import Element.Background as Background
import Element.Font as Font
import Html exposing (Html)


view : Html msg
view =
    layout
        [ Background.color (rgb255 15 23 42)
        , Font.color (rgb255 226 232 240)
        ]
        (el
            [ centerX
            , centerY
            , Font.size 48
            , Font.bold
            ]
            (text "Hello, world!")
        )
