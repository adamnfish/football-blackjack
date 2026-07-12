Football BlackJack
==================

Why not mix up the standard sweepstake model, by playing BlackJack with your friends, with major football tournaments.

## The rules

Your task is to pick any 4 teams from the 48 that you think will get a combined goals total of 25.
If you score 26 or more, you are 'bust' .

- You need to pick a unique combination of 4 teams based on a first-come-first-serve basis
- In the unlikely event of a tie, the winner will be decided by (in order):
    1. the player who's teams got furthest through the tournament
    2. the player who's chosen team(s) have the best goal difference
- goals exclude penalty shootouts (but include extra time and open-play penalties)
- Deadline is before kick-off of the opening game, or when the game's admin chooses to lock game entries

## How to play

### Creating a game

To get started, go to Football BlackJack and create a new game. Choose the number of teams each player should select (
default 4) and the target goal count (default 25). Send the game's join link to your friends so everyone can select
their teams.

### Joining a game

To join an existing game, ask the game's creator for the join link, and then choose your teams.

### During the tournament

Football BlackJack will keep track of the goals each team scores, and provide charts and analysis for your friends' team
selection.

## Development

### Architecture

Football BlackJack is a SPA statically served out of S3/CloudFront that is written in [elm](https://elm-lang.org/)
and [typescript](https://www.typescriptlang.org/). It lives under [frontend/](frontend/).

The backend is split into two parts:

1. AWS Lambda function behind API Gateway that handles the SPA's backend
2. AWS Lambda that runs on a schedule to keep the game data up to date

These Lambda functions are Scala projects under [backend/](backend/), which depend on a shared [common](backend/common/)
project that contains the backend's logic.

The AWS infrastructure is provisioned by a CDK app under [infrastructure/](infrastructure/).

### Running locally

```
pnpm install               # once, from the repository root
sbt devServer/run          # API on http://localhost:9090
pnpm --dir frontend dev    # frontend on http://localhost:5173, proxies /api to the dev server
pnpm --dir e2e test        # Playwright e2e tests (expects the dev server to be running)
```

See [docs/dev-server.md](docs/dev-server.md) for more detail.

### Frontend

Provides web UIs for the game, team selection, results tables and game analysis.

### Backend

#### API Lambda

Wraps the [API](backend/common/src/main/scala/com/adamnfish/fbj/API.scala) defined in common with a Lambda function
handler, and plugs in the AWS-backed production persistence layer.

#### Football data Lambda

Wraps common's [CompetitionJob](backend/common/src/main/scala/com/adamnfish/fbj/CompetitionJob.scala) in a Lambda

#### Devserver

A webserver for running the API locally. Also serves a simple UI for triggering competition job runs.

### Infrastructure

Provides the webroot S3 bucket, CDN, Lambda backends (with API Gateway / event invocation), configuration, and so on.
