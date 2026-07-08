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
| End-to-end tests | TBD | TBD (Playwright?) | Browser tests + screenshots against local dev server or deployed test/prod |

## Current state

Done and tested:

- Domain models with circe codecs: `backend/common/src/main/scala/com/adamnfish/fbj/models/`
- API message types (`Request`/`Response`/`Errors`): `models/messages.scala`
- football-data.org response decoders with real fixtures: `backend/footballdata/`

Stubbed (`???`):

- `API.scala` — dispatch + 7 endpoints
- `CompetitionJob.scala`
- `FootballData.fetchCompetitionStats` / `convertDataToStats`

Missing entirely:

- Any `Persistence` implementation
- `backend/api`, `backend/data-service`, `backend/dev-server` (declared in `build.sbt`, directories don't exist)
- Frontend beyond a hello-world Elm shell
- CDK resources (empty stack class)

## Dependency graph and suggested build order

```
footballdata conversion ─┐
                         ├─→ competition job ─→ data-service Lambda ─┐
persistence (in-memory) ─┤                                           ├─→ infrastructure
                         ├─→ API logic ─┬─→ api Lambda ──────────────┘
                         │              └─→ dev server ─→ frontend
persistence (production) ┘
```

1. **[01 football-data](01-football-data.md)** — pure conversion logic first; fixtures and models already exist
2. **[02 persistence](02-persistence.md)** — in-memory implementation first; production store once decided
3. **[03 API](03-api.md)** — game logic in `common`
4. **[04 competition job](04-competition-job.md)** — small orchestration piece
5. **[06 dev server](06-dev-server.md)** — unlocks end-to-end local development
6. **[07 frontend](07-frontend.md)** — built against the dev server
7. **[05 lambdas](05-lambdas.md)** — production wiring for API and job
8. **[08 infrastructure](08-infrastructure.md)** — CDK, deployment
9. **[09 CI/CD](09-cicd.md)** — GitHub Actions + OIDC, test/production environments
10. **[10 e2e tests](10-e2e-tests.md)** — browser suite with screenshots; a minimal version is worth starting as soon as the dev server + first frontend flow exist

Order is a guide, not a straitjacket — 05–10 can interleave once 01–04 are in place.

## Established conventions & cross-cutting decisions

- Scala 3 (3.8.4), wrapper case classes for identifiers (`GameId`, `PlayerKey`, …) in `models/wrappers.scala`
- JSON via circe `derives Codec` / `derives Decoder`; custom decoders for enums with wire-format names (see `footballdata/models.scala`)
- **Error handling: `scala.util.Try` throughout**, with a custom exception type carrying the `Errors` enum for expected domain failures (decided; Cats Effect / tagless final is the fallback if concurrency ever demands it — see [03-api](03-api.md))
- **Store: DynamoDB**, two plain tables (games; competition-stats), items as circe JSON blobs, optimistic versioning via a `Versioned` envelope in the `Persistence` trait (decided — see [02-persistence](02-persistence.md))
- **Stats history**: competition stats are written append-only, partitioned by load time; only the latest snapshot is read for now (decided — see [02-persistence](02-persistence.md))
- **Config**: minimal; football-data API key in SSM Parameter Store, surfaced as env vars ([05-lambdas](05-lambdas.md)); local equivalent in [06-dev-server](06-dev-server.md)
- **Access control**: capability-by-link — player key in a personal URL, spreadsheet-with-edit-link level trust; lost links recovered via admin re-sharing (decided — see [03-api](03-api.md) / [07-frontend](07-frontend.md))
- **Wire format**: `POST /api/{operation}` (kebab-case) with bare per-request JSON payloads (decided — see [03-api](03-api.md))
- Tests with munit; JSON round-trip tests for models, fixture-driven tests for external formats; browser e2e with screenshots ([10-e2e-tests](10-e2e-tests.md))
- scalafmt configured (`.scalafmt.conf`)
- Frontend: Elm 0.19 + **elm-ui** (decided, known technology), Vite, pnpm workspace
- Deploys via GitHub Actions with OIDC to **test** and **production** GHA environments ([09-cicd](09-cicd.md)); [pokerdot](https://github.com/adamnfish/pokerdot) is the reference project for CI/CD and e2e shape

## Status

| Doc | Piece | Status |
|---|---|---|
| [01-football-data](01-football-data.md) | football-data fetch + conversion | designed |
| [02-persistence](02-persistence.md) | persistence implementations | designed |
| [03-api](03-api.md) | API game logic | designed |
| [04-competition-job](04-competition-job.md) | competition job | designed |
| [05-lambdas](05-lambdas.md) | Lambda handlers | designed |
| [06-dev-server](06-dev-server.md) | local dev server | designed |
| [07-frontend](07-frontend.md) | Elm SPA | designed |
| [08-infrastructure](08-infrastructure.md) | CDK infrastructure | designed |
| [09-cicd](09-cicd.md) | GitHub Actions CI/CD | designed |
| [10-e2e-tests](10-e2e-tests.md) | end-to-end tests | designed |
