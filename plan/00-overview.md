# Football Blackjack — build plan overview

Football Blackjack is a sweepstake game for major football tournaments: each player
picks N teams (default 4) aiming for a combined goal total of exactly the target
(default 25) without going over ("bust"). See the [README](../README.md) for the
full rules and intended architecture.

This directory holds the build plan: this overview, plus one framing document per
major task. Each task doc captures scope, current state, dependencies, and the key
design decisions to make — the detailed approach for each piece is designed in its
own session and written back into its doc.

## Architecture and module map

| Piece | Location | Build unit | Role |
|---|---|---|---|
| Domain models & API logic | `backend/common` | sbt `common` | Game/competition models, `Request`/`Response` messages, `API`, `CompetitionJob`, `Persistence` + `CompetitionData` traits |
| football-data.org client | `backend/footballdata` | sbt `footballdata` | Fetches matches from football-data.org, converts to `CompetitionStats`; implements `CompetitionData` |
| API Lambda | `backend/api` | sbt `api` | Lambda handler behind API Gateway, wraps `API`, plugs in production persistence |
| Data service Lambda | `backend/data-service` | sbt `dataService` | Scheduled Lambda wrapping `CompetitionJob` |
| Dev server | `backend/dev-server` | sbt `devServer` | Local webserver for the API + UI to trigger competition job runs |
| Frontend SPA | `frontend/` | pnpm workspace | Elm + elm-ui SPA, bundled with Vite, served from S3/CloudFront |
| Infrastructure | `infrastructure/` | CDK app | S3/CloudFront webroot, API Gateway + Lambdas, data store, config, GHA OIDC |
| CI/CD | `.github/workflows/` (TBD) | GitHub Actions | Build/test on PR; OIDC deploys to test & production GHA environments |
| End-to-end tests | `e2e/` (TBD) | pnpm workspace | Playwright suite against local dev server or deployed test/prod; screenshots are a first-class output |

## Current state

**Phase 0 is complete** (2026-07-10): certificates and SSM parameters created,
the CI stack (the two GHA deploy roles) deployed, `test`/`prod` GHA
environments configured, OIDC verified from both environments via the deploy
workflow skeleton. As-built record: [docs/bootstrap.md](../docs/bootstrap.md).

**Phase 1 is built and merged** (PRs #4–#7, #12): stub api Lambda, skeleton
app stacks, build-and-test and deploy workflows, stub dev server, minimal e2e
suite. The first dispatch deploy to test and its verification remain.

**Phase 2 first tranche is merged** (2026-07-12, PRs #16–#23 — as-built record:
[phase-2-status](phase-2-status.md)):

- in-memory `Persistence` + contract test suite ([02-persistence](02-persistence.md))
- `API.dispatch` and all endpoints, with the shared `HttpMapping`
  ([03-api](03-api.md))
- `CompetitionJob.fetchCompetition` core logic
  ([04-competition-job](04-competition-job.md))
- dev server serving the real API on canned stats fixtures, with the `/dev`
  panel ([06-dev-server](06-dev-server.md))
- frontend skeleton and the create/join/selection flows, verified by Playwright
  scenarios with per-step screenshots ([07-frontend](07-frontend.md),
  [10-e2e-tests](10-e2e-tests.md))
- the e2e-test workflow for deployed environments ([09-cicd](09-cicd.md)) — the
  CI stack needs a manual redeploy (new scoped `ssm:GetParameter`) before its
  first dispatch

**Next**: first dispatch deploy to test, CI stack redeploy, e2e-test workflow
dispatch; then the rest of phase 2 (full game view, analysis views, admin
panel, munit golden tests).

Done and tested (pre-skeleton foundations):

- Domain models with circe codecs: `backend/common/src/main/scala/com/adamnfish/fbj/models/`
- API message types (`Request`/`Response`/`Errors`): `models/messages.scala`
- football-data.org response decoders with real fixtures: `backend/footballdata/`

Stubbed (`???`):

- `FootballData.fetchCompetitionStats` / `convertDataToStats` (phase 3)
- The deployed api Lambda still answers 200 `"ok"`; real dispatch + store is
  phase 5

Missing entirely:

- DynamoDB `Persistence` implementation (phase 4)
- `backend/data-service` (declared in `build.sbt`, no sources until phase 5)
- Frontend game view (leaderboard), analysis views, admin panel

## Build strategy: walking skeleton

We deploy a working application that does nothing, then grow it without ever
breaking deployability. The skeleton has the full production shape — CloudFront +
S3 frontend, API Lambda behind `/api/*`, CI/CD deploying to test and production —
with a hello-world UI and an API that answers 200 `"ok"`. From there we work
backwards from the UI: dev server and e2e tests first, then the API and frontend
grow together, first on stub data and fixtures, then real conversions, then real
data from the persistence layer, with the competition job built alongside so the
store has data ready for the API to consume. The e2e suite is kept in sync
throughout — it encodes the requirements.

### Phase 0 — manual prerequisites

Domain choice + Route53 hosted zone (the one item with lead time); CDK bootstrap
of the account; the once-per-account CI stack (GitHub OIDC provider + per-stage
deploy roles, [08-infrastructure](08-infrastructure.md)); `test`/`prod` GHA
environments in repo settings ([09-cicd](09-cicd.md)). Step-by-step checklist:
[phase-0-runbook](phase-0-runbook.md).

**Done when** a GitHub Actions workflow can assume a deploy role via OIDC.
✅ **Complete 2026-07-10.**

### Phase 1 — walking skeleton

Stub api Lambda (real `POST /api/{operation}` wire shape, answers 200 `"ok"`,
sbt-assembly packaging — [05-lambdas](05-lambdas.md)); skeleton CDK stack (S3 +
OAC, CloudFront with SPA and `/api/*` behaviours, HTTP API, stage domain + cert,
`BucketDeployment`) with CloudFormation snapshot tests for both stages
([08-infrastructure](08-infrastructure.md)); the three
workflows ([09-cicd](09-cicd.md)); stub Cask dev server as the local e2e target
behind the Vite proxy ([06-dev-server](06-dev-server.md)); minimal Playwright
suite: page loads + screenshot, `/api/ping` is 200 ([10-e2e-tests](10-e2e-tests.md)).

**Done when** the test environment serves the hello-world frontend at its domain
and `POST /api/ping` returns 200 through CloudFront; PRs run build-and-test
including the local e2e suite with screenshot artifacts; a prod deploy works the
same way. **In progress: everything is built and merged; the first dispatch
deploy to test and its verification remain.**

### Phase 2 — working backwards from the UI

The dev server grows into a real local app ([06-dev-server](06-dev-server.md)):
`API.dispatch` behind the routes, in-memory persistence
([02-persistence](02-persistence.md)), a stub `CompetitionData` serving canned
`CompetitionStats` fixtures (several tournament states), `/dev` panel with job
trigger + demo-game seeding. API endpoints ([03-api](03-api.md)) and their
frontend flows ([07-frontend](07-frontend.md)) land one at a time, each specified
by a Playwright scenario.

**Done when** a full game can be played locally end to end on fixture data, with
the e2e suite covering every flow. (Deployed environments stay at skeleton
behaviour until phase 5 — the deployed API has no data layer yet.)
**In progress: first tranche merged (see Current state); remaining: full game
view, analysis views, admin panel, munit golden-sample tests.**

### Phase 3 — real conversions

Implement `FootballData` ([01-football-data](01-football-data.md)); the dev
server upgrades from canned stats to the fixture-rewriting
`FixtureCompetitionData` + simulated clock (which needs `convertDataToStats`),
making every tournament state reproducible in e2e.

**Done when** conversion passes its fixture + property tests and the dev server
runs on the simulated clock.

### Phase 4 — real persistence

DynamoDB adapter ([02-persistence](02-persistence.md)); the contract suite runs
against DynamoDB Local as well as in-memory; the dev server swaps in-memory for
DynamoDB Local; tables added to the CDK stack.

**Done when** the local app runs against DynamoDB Local and the contract suite
passes on both implementations.

### Phase 5 — production wiring

Competition job ([04-competition-job](04-competition-job.md)); data-service
Lambda + EventBridge schedule + SSM API key + failure alarm; the api Lambda swaps
its stub for real dispatch + DynamoDB ([05-lambdas](05-lambdas.md),
[08-infrastructure](08-infrastructure.md)).

**Done when** the deployed test environment plays a full game end to end on real
competition data and the e2e-test workflow passes its full suite against it.

Phases 3–5 can interleave; the invariant is that every merge keeps the app
deployable and the e2e suite green.

## Test strategy

Cross-cutting principles, inner to outer — each task doc's Tests section applies
them:

1. **Logic unit tests** — the bulk of testing. Side effects are separated from
   logic so the logic is pure and directly testable. Property tests
   (munit-scalacheck) wherever properties express the requirement better than
   specific values.
2. **Integration tests of the assembled app** — side effects live only in
   services that are **as thin as possible** (`Persistence`, `CompetitionData`,
   the HTTP mapping). Tests swap in test implementations of these services and
   drive the application through its requests and responses. Because the effect
   layers are thin, the application under test stays as close as possible to
   the one running in AWS.
3. **End-to-end tests (Playwright)** — real behaviours of the running
   application, and where the frontend/UI is captured: `update`, message
   handling, HTTP calls, ports and subscriptions are checked in situ against
   the real API rather than unit-mocked. Screenshots of the user flows are a
   first-class output serving as the visual regression record (automated
   screenshot diffing is a possible future addition). The suite grows in
   lockstep with functionality and encodes the requirements.
4. **Frontend unit tests (elm-test)** — pure logic only (derived game values,
   codecs via golden samples shared with the Scala side, routing). Elm's
   architecture makes the effect/logic separation natural; views and `update`
   should carry as little logic as possible, delegating to plain testable
   functions.

Plus, for infrastructure: **CDK snapshot tests** — the synth'd CloudFormation
for both stages is committed as vitest snapshots (asset hashes masked), so
expected infrastructure changes appear explicitly in diffs and unexpected ones
fail the build ([08-infrastructure](08-infrastructure.md)).

## Established conventions & cross-cutting decisions

- Scala 3 (3.8.4), wrapper case classes for identifiers (`GameId`, `PlayerKey`, …) in `models/wrappers.scala`
- JSON via circe `derives Codec` / `derives Decoder`; custom decoders for enums with wire-format names (see `footballdata/models.scala`)
- **Error handling: `scala.util.Try` throughout**, with a custom exception type carrying the `Errors` enum for expected domain failures (decided; Cats Effect / tagless final is the fallback if concurrency ever demands it — see [03-api](03-api.md))
- **Store: DynamoDB**, two plain tables (games; competition-stats), items as circe JSON blobs, optimistic versioning via a `Versioned` envelope in the `Persistence` trait (decided — see [02-persistence](02-persistence.md))
- **Stats history**: competition stats are written append-only, partitioned by load time; only the latest snapshot is read for now (decided — see [02-persistence](02-persistence.md))
- **Config**: minimal, discovered from SSM Parameter Store at deploy time — per-stage domain/zone/cert parameters ([08-infrastructure](08-infrastructure.md)) and the football-data API key ([05-lambdas](05-lambdas.md)); domain names are deliberately never written into the repo; GHA environments hold only the deploy role ARN and region; local equivalent in [06-dev-server](06-dev-server.md)
- **Access control**: capability-by-link — player key in a personal URL, spreadsheet-with-edit-link level trust; lost links recovered via admin re-sharing (decided — see [03-api](03-api.md) / [07-frontend](07-frontend.md))
- **Wire format**: `POST /api/{operation}` (kebab-case) with bare per-request JSON payloads (decided — see [03-api](03-api.md))
- Tests per the test strategy above: munit (+ munit-scalacheck for property tests); JSON round-trip tests for models, fixture-driven tests for external formats, browser e2e with screenshots ([10-e2e-tests](10-e2e-tests.md))
- Formatting enforced in CI: scalafmt (`.scalafmt.conf`) and elm-format ([09-cicd](09-cicd.md))
- Frontend: Elm 0.19 + **elm-ui** (decided, known technology), Vite, pnpm workspace
- **Stage naming: `test` and `prod`**, used consistently for stack names, the `stage` tag, and GHA environment names
- **Tagging**: every AWS resource carries `app: football-blackjack` and `stage: test|prod`, applied via CDK `Tags.of` at the app/stack level (decided — see [08-infrastructure](08-infrastructure.md))
- Deploys via GitHub Actions with OIDC to **test** and **prod** GHA environments ([09-cicd](09-cicd.md)); deploy roles only assume the unmodified account-wide CDK bootstrap roles, with access control resting on the OIDC trust conditions ([08-infrastructure](08-infrastructure.md)); [pokerdot](https://github.com/adamnfish/pokerdot) is the reference project for CI/CD and e2e shape

## Open questions

- **Competition lifecycle — needs its own design session.** The auto-lock means
  the app behaves differently before, during, and after a tournament: joins are
  auto-locked once stats show a start; after the final, games are permanently
  locked and stats frozen; between tournaments there is no valid competition to
  create games against (`createGame` requires stats in the store). What the app
  should do in each window — and how the next competition is configured and
  switched on (schedule rule, config, initial stats load) — needs thinking
  through as a whole. Touches [03-api](03-api.md),
  [04-competition-job](04-competition-job.md),
  [08-infrastructure](08-infrastructure.md), and [10-e2e-tests](10-e2e-tests.md)
  (the full suite against deployed test only passes mid-tournament or via admin
  unlock).
- **Operational docs (`docs/`, later).** A developer/operator runbook for
  questions that outlive the build plan: the phase-0 as-built record (the
  [runbook](phase-0-runbook.md)'s closing step) and the start/end-of-tournament
  procedures once the lifecycle question above is settled.

## Status

| Doc | Piece | Status | Phase(s) |
|---|---|---|---|
| [01-football-data](01-football-data.md) | football-data fetch + conversion | designed | 3 |
| [02-persistence](02-persistence.md) | persistence implementations | in-memory built | 2 (in-memory), 4 (DynamoDB) |
| [03-api](03-api.md) | API game logic | built | 2 |
| [04-competition-job](04-competition-job.md) | competition job | core logic built | 5 (Lambda wiring) |
| [05-lambdas](05-lambdas.md) | Lambda handlers | api stub merged | 1 (api stub), 5 (full) |
| [06-dev-server](06-dev-server.md) | local dev server | phase 2 server built | 2–4 (grows) |
| [07-frontend](07-frontend.md) | Elm SPA | create/join/selection flows built | 2 (game view, analysis, admin remain) |
| [08-infrastructure](08-infrastructure.md) | CDK infrastructure | CI stack deployed (manual redeploy pending); app stacks merged | 0–1 (skeleton), 4–5 (data layer) |
| [09-cicd](09-cicd.md) | GitHub Actions CI/CD | all three workflows built; first deploy dispatch pending | 1 |
| [10-e2e-tests](10-e2e-tests.md) | end-to-end tests | covers current flows | continuous |
