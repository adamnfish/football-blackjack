module View exposing (colors, layout)

{-| Shared elm-ui shell and palette.
-}

import Element exposing (..)
import Element.Background as Background
import Element.Font as Font
import Html exposing (Html)


colors :
    { background : Color
    , surface : Color
    , text : Color
    , muted : Color
    , accent : Color
    , danger : Color
    }
colors =
    { background = rgb255 15 23 42
    , surface = rgb255 30 41 59
    , text = rgb255 226 232 240
    , muted = rgb255 148 163 184
    , accent = rgb255 56 189 248
    , danger = rgb255 248 113 113
    }


layout : Element msg -> Html msg
layout content =
    Element.layout
        [ Background.color colors.background
        , Font.color colors.text
        ]
        content
