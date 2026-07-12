module RouteTest exposing (suite)

import Domain exposing (GameId(..), PlayerKey(..))
import Expect
import Route exposing (Route(..))
import Test exposing (Test, describe, test)
import Url


parse : String -> Maybe Route
parse url =
    Url.fromString url |> Maybe.map Route.fromUrl


suite : Test
suite =
    describe "Route"
        [ describe "fromUrl"
            [ test "root is Home" <|
                \_ ->
                    parse "https://example.com/"
                        |> Expect.equal (Just Home)
            , test "root with query parameters is still Home" <|
                \_ ->
                    parse "https://example.com/?utm=nonsense"
                        |> Expect.equal (Just Home)
            , test "game path carries the game id" <|
                \_ ->
                    parse "https://example.com/game/abc123"
                        |> Expect.equal (Just (Game (GameId "abc123") Nothing))
            , test "game path with a key carries the player key" <|
                \_ ->
                    parse "https://example.com/game/abc123?key=secret-key"
                        |> Expect.equal
                            (Just (Game (GameId "abc123") (Just (PlayerKey "secret-key"))))
            , test "game path with other query parameters has no key" <|
                \_ ->
                    parse "https://example.com/game/abc123?other=thing"
                        |> Expect.equal (Just (Game (GameId "abc123") Nothing))
            , test "unknown paths are NotFound" <|
                \_ ->
                    parse "https://example.com/nope"
                        |> Expect.equal (Just NotFound)
            , test "game path without an id is NotFound" <|
                \_ ->
                    parse "https://example.com/game"
                        |> Expect.equal (Just NotFound)
            ]
        , describe "toPath"
            [ test "Home is the root path" <|
                \_ ->
                    Route.toPath Home
                        |> Expect.equal "/"
            , test "Game is the join path" <|
                \_ ->
                    Route.toPath (Game (GameId "abc123") Nothing)
                        |> Expect.equal "/game/abc123"
            , test "Game never includes the player key" <|
                \_ ->
                    Route.toPath (Game (GameId "abc123") (Just (PlayerKey "secret-key")))
                        |> Expect.equal "/game/abc123"
            , test "Game percent-encodes the id" <|
                \_ ->
                    Route.toPath (Game (GameId "a b") Nothing)
                        |> Expect.equal "/game/a%20b"
            ]
        ]
