port module Ports exposing (storePlayerKey)

{-| The JS side (`src/index.js`) persists `{gameId -> playerKey}` to
localStorage; the stored map comes back in via flags on init.
-}


port storePlayerKey : { gameId : String, playerKey : String } -> Cmd msg
