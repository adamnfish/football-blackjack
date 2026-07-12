module Domain exposing
    ( Competition
    , CompetitionCode(..)
    , CompetitionId(..)
    , CompetitionName(..)
    , CompetitionStats
    , Errors(..)
    , Game
    , GameId(..)
    , GameName(..)
    , GameSettings
    , LockState(..)
    , Player
    , PlayerId(..)
    , PlayerKey(..)
    , PlayerName(..)
    , Progress(..)
    , Score
    , Status(..)
    , Team
    , TeamId(..)
    , TeamName
    , TeamStats
    , TeamTLA(..)
    , gameIdToString
    , playerKeyToString
    )

{-| Domain types mirroring the Scala models in
`backend/common/src/main/scala/com/adamnfish/fbj/models/`. The JSON codecs
live in `Api`.
-}

-- Identifier wrappers (models/wrappers.scala)


type GameId
    = GameId String


type GameName
    = GameName String


type PlayerId
    = PlayerId String


type PlayerKey
    = PlayerKey String


type PlayerName
    = PlayerName String


type TeamId
    = TeamId String


type TeamTLA
    = TeamTLA String


type alias TeamName =
    { short : String
    , long : String
    }


type CompetitionId
    = CompetitionId Int


type CompetitionCode
    = CompetitionCode String


type CompetitionName
    = CompetitionName String


gameIdToString : GameId -> String
gameIdToString (GameId id) =
    id


playerKeyToString : PlayerKey -> String
playerKeyToString (PlayerKey key) =
    key



-- Game models (models/game.scala)


type alias Game =
    { id : GameId
    , gameName : GameName
    , gameSettings : GameSettings
    , lockState : LockState
    , players : List Player
    , gameAdmin : PlayerId
    , competition : Competition
    }


type LockState
    = Auto
    | Locked
    | Unlocked


type alias GameSettings =
    { goalLimit : Int
    , teamCount : Int
    }


type alias Player =
    { id : PlayerId
    , name : PlayerName
    , selection : List TeamId
    }


type alias Competition =
    { id : CompetitionId
    , code : CompetitionCode
    , name : CompetitionName
    }


type alias CompetitionStats =
    { id : CompetitionId
    , timestamp : String
    , teams : List ( Team, TeamStats )
    }


type alias Team =
    { id : TeamId
    , tla : TeamTLA
    , name : TeamName
    , crestUrl : String
    }


type alias TeamStats =
    { score : Score
    , progress : Progress
    , status : Status
    }


type alias Score =
    { goalsFor : Int
    , goalsAgainst : Int
    }


type Progress
    = NotStarted
    | Group Int
    | Knockout Int
    | TopFour Int


type Status
    = Eliminated
    | Playing



-- API errors (models/messages.scala)


type Errors
    = ValidationError { field : String, message : String }
    | ValidationErrors { message : String, fields : List ( String, String ) }
    | GameNotFound
    | CompetitionNotFound
    | TeamSelectionTaken
    | PlayerNameTaken
    | GameLocked
    | Unauthorized
