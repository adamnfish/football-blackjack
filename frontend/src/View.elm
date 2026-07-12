module View exposing
    ( colors
    , errorText
    , heading
    , layout
    , primaryButton
    , testId
    , textField
    )

{-| Shared elm-ui shell, palette and controls.
-}

import Element exposing (..)
import Element.Background as Background
import Element.Border as Border
import Element.Font as Font
import Element.Input as Input
import Html exposing (Html)
import Html.Attributes


colors :
    { background : Color
    , surface : Color
    , text : Color
    , muted : Color
    , accent : Color
    , accentText : Color
    , danger : Color
    }
colors =
    { background = rgb255 15 23 42
    , surface = rgb255 30 41 59
    , text = rgb255 226 232 240
    , muted = rgb255 148 163 184
    , accent = rgb255 56 189 248
    , accentText = rgb255 8 47 73
    , danger = rgb255 248 113 113
    }


layout : Element msg -> Html msg
layout content =
    Element.layout
        [ Background.color colors.background
        , Font.color colors.text
        ]
        content


{-| A stable hook for the Playwright suite.
-}
testId : String -> Attribute msg
testId id =
    htmlAttribute (Html.Attributes.attribute "data-testid" id)


heading : String -> Element msg
heading label =
    el [ Font.size 32, Font.bold ] (text label)


textField :
    { testIdValue : String
    , label : String
    , value : String
    , onChange : String -> msg
    }
    -> Element msg
textField config =
    Input.text
        [ testId config.testIdValue
        , Background.color colors.surface
        , Border.color colors.muted
        , Border.width 1
        , Border.rounded 4
        , padding 10
        ]
        { onChange = config.onChange
        , text = config.value
        , placeholder = Nothing
        , label =
            Input.labelAbove [ Font.size 14, Font.color colors.muted ]
                (text config.label)
        }


primaryButton :
    { testIdValue : String
    , label : String
    , onPress : Maybe msg
    }
    -> Element msg
primaryButton config =
    Input.button
        [ testId config.testIdValue
        , Background.color colors.accent
        , Font.color colors.accentText
        , Font.bold
        , paddingXY 20 12
        , Border.rounded 4
        , alpha
            (if config.onPress == Nothing then
                0.5

             else
                1
            )
        ]
        { onPress = config.onPress
        , label = text config.label
        }


errorText : String -> String -> Element msg
errorText testIdValue message =
    paragraph
        [ testId testIdValue
        , Font.color colors.danger
        , Font.size 16
        ]
        [ text message ]
