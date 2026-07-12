module Route exposing (Route(..), fromUrl, toPath)

{-| URL routing: `/` (home/create) and `/game/{gameId}`, the latter with an
optional `?key={playerKey}` query parameter carried by personal links. `toPath`
never includes the key: the app strips it from the address bar after storing
it (see `Main`).
-}

import Domain exposing (GameId(..), PlayerKey(..))
import Url exposing (Url)
import Url.Parser as Parser exposing ((</>), (<?>), Parser, s)
import Url.Parser.Query as Query


type Route
    = Home
    | Game GameId (Maybe PlayerKey)
    | NotFound


parser : Parser (Route -> a) a
parser =
    Parser.oneOf
        [ Parser.map Home Parser.top
        , Parser.map
            (\gameId maybeKey -> Game (GameId gameId) (Maybe.map PlayerKey maybeKey))
            (s "game" </> Parser.string <?> Query.string "key")
        ]


fromUrl : Url -> Route
fromUrl url =
    Maybe.withDefault NotFound (Parser.parse parser url)


toPath : Route -> String
toPath route =
    case route of
        Home ->
            "/"

        Game (GameId gameId) _ ->
            "/game/" ++ Url.percentEncode gameId

        NotFound ->
            "/"
