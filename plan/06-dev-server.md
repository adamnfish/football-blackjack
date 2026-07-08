# 06 — Dev server

**Status: designed**

## Goal

Create `backend/dev-server` (sbt `devServer`, declared in `build.sbt` but not on
disk): a local webserver that

1. serves the same API the Lambda exposes in production, so the frontend can be
   developed against `pnpm dev` + a running dev server
2. provides a simple UI to trigger `CompetitionJob` runs manually (per the README),
   seed demo games, and control a **simulated clock** for exploring the UI at any
   point in a tournament

## Current state

- Nothing on disk; sbt module declared with no extra dependencies

## Depends on

- [03-api](03-api.md) — the `API` it wraps and the operation/error wire contract
- [02-persistence](02-persistence.md) — the DynamoDB adapter (used against
  DynamoDB Local)
- [04-competition-job](04-competition-job.md) — for the trigger UI

## Decided

- **Server: Cask** — pleasant minimal routing for a handful of routes, one small
  dependency to add to the `devServer` module.
- **Competition data: JSON fixtures with a simulated clock.** A fixture-backed
  `CompetitionData` presents a *complete-tournament* fixture **as of a
  configurable "current time"**, so the UI can be explored at any tournament
  stage. The current time is inspectable and settable via a dev settings endpoint.
- **Persistence: the real DynamoDB adapter against DynamoDB Local in Docker** —
  exercises the production codecs, conditional writes, and table shapes during
  everyday development (the unmaintained sbt-dynamodb plugin is avoided;
  `amazon/dynamodb-local` runs via docker compose). The in-memory implementation
  remains for `common` unit tests, which must not need Docker.
- **Frontend connection: Vite proxy** — `vite.config.mjs` proxies `/api/*` to the
  dev server; the browser sees one origin, no CORS anywhere, matching the intended
  same-origin production setup.

## Approach

### Routes

- `POST /api/{operation}` → `API.dispatch(operation, body)` — the shared HTTP
  mapping (operation extraction, `Errors` → status/body) lives in `common` so the
  Lambda handler and dev server cannot drift.
- `GET /dev` — server-rendered HTML control panel: current simulated time, run
  the competition job, seed a demo game (creates a game + a few players with
  selections — invaluable for frontend work and e2e tests).
- `POST /dev/job` — trigger `CompetitionJob.fetchCompetition`.
- `GET/POST /dev/time` — read/set the simulated "current time".

### Simulated clock & fixture data source

- `FixtureCompetitionData(fixture, clock)` loads a complete-tournament fixture
  and rewrites it as of the simulated instant `T` before conversion:
  - matches finishing before `T` keep status/scores; a match spanning `T` becomes
    `IN_PLAY` (scores kept — close enough for UI exploration); later matches
    become `TIMED` with null scores
  - knockout team slots are hidden (nulled) until the previous stage has fully
    finished before `T`, simulating football-data's bracket population at stage
    granularity (the checked-in partial-bracket fixture still covers the finer
    lag case in unit tests)
- `convertDataToStats(matchesResponse, when = T)` already takes the instant —
  simulated time flows into `CompetitionStats.timestamp` naturally. The API
  itself has no clock (auto-lock derives from stats), so simulation lives
  entirely in this module.

### DynamoDB Local

- `docker-compose.yml` (repo root or `backend/dev-server/`) running
  `amazon/dynamodb-local`; the dev server creates the two tables on startup if
  absent. State survives server restarts (container keeps running); a compose
  volume can make it survive container restarts if that proves useful.

### Configuration

- Configured competition (code/id) and fixture choice via flags/env with sensible
  defaults; no football-data API key needed locally (the real client is only
  exercised in deployed environments — revisit if a local smoke-test need arises).

## Notes

- The dev-server module gains dependencies: Cask, plus `dynamodb` (the adapter
  module) and `footballdata` (fixture models/conversion).
- The simulated clock makes the e2e suite ([10-e2e-tests](10-e2e-tests.md))
  dramatically more useful locally: screenshots of pre-tournament, mid-group,
  knockout, and finished states are all reproducible on demand.
