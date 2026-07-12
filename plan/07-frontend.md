# 07 — Frontend SPA

**Status: create/join/selection flows built · remaining phase 2: game view, analysis views, admin panel**

## Goal

Build the Elm SPA under `frontend/`: create a game, join via link, pick teams,
watch the sweepstake unfold — leaderboard, analysis views, and the admin's
game-management controls.

## Current state

- Skeleton per the structure below: `Browser.application`, `Route`, `Api` (all
  8 operations with hand-written codecs), `Ports` + localStorage key handling,
  elm-test in CI
- Create/join/selection flows: create form on the home page, join form and
  selection screen on the game page's state machine, plus a placeholder game
  view (players and their selections)
- Codecs kept honest by golden-sample tests against `fixtures/api/` (elm-test
  side; the munit side lands now the backend model changes are merged)
- Each flow has its Playwright scenario with per-step screenshots
  ([10-e2e-tests](10-e2e-tests.md))
- Remaining, in build order: full game view (leaderboard, totals, bust, 60s
  poll + focus refresh), analysis views, admin panel (lock/unlock, re-share
  links)

## Depends on

- [06-dev-server](06-dev-server.md) — local API (Vite proxy to `/api/*`),
  simulated clock for exploring tournament states
- The wire contract from [03-api](03-api.md): `POST /api/{operation}`, bare
  payloads, circe-encoded responses/errors

## Decided

- **Styling: elm-ui** (already a dependency, known technology); revisit only if it
  fights the analysis views. Charts: terezka/elm-charts.
- **Links**: join link `/game/{gameId}`; personal link `/game/{gameId}?key={playerKey}`.
  On load the app stores the key in localStorage (keyed by game) and strips it
  from the address bar (`replaceUrl`). Lost link / new device = admin re-shares
  via `FetchPlayerKeys` ([03-api](03-api.md)).
- **V1 game page views** (leaderboard with totals, bust status and tie-break
  order is the core):
  - progress-to-target bars per player (bust-red past the target)
  - expandable per-player team breakdown (each pick's goals, stage, alive/out)
  - competition-wide teams overview (goals, stage reached, picked-by, still-in)
  - goal-race-over-time chart is explicitly **out of v1** — it needs the
    historical snapshots read path we deferred in [02-persistence](02-persistence.md)
- **Refresh: 60s poll** of `FetchGameInfo` while a game page is open, plus
  immediate refresh on tab focus.
- **Creator team selection: same selection screen as joiners**, entered right
  after create; `CreateGame` stays slim (name only) and there's a single
  selection UI. Views must tolerate a player with an empty selection (the
  creator, briefly — and any admin who hasn't picked yet).
- **Codecs: hand-written** Elm encoders/decoders mirroring the circe formats,
  kept honest by golden-sample tests on both sides (same JSON files asserted
  against in munit and elm-test).

## Approach

### Structure

- `Browser.application` with a `Route` module: `/` (home/create),
  `/game/{gameId}` (join/selection/game view depending on state). Page modules
  (`Page.Home`, `Page.Game`), an `Api` module (one function per operation,
  encoders/decoders, shared error decoding), and a `Ports` module (localStorage
  read/write of `{gameId → playerKey}`).
- The game page is a state machine: not-joined (+unlocked) → selection screen;
  joined → game view; admin (key matches `gameAdmin`) additionally sees
  lock/unlock and the re-share-links panel.

### Selection screen

- Team grid with crests (placeholder when `crestUrl` is absent), pick-count
  against `GameSettings.teamCount`, other players' selections visible (public by
  design) so takers can avoid taken combinations; `TeamSelectionTaken` from the
  server handled as a friendly inline error as the backstop for races.

### Game view

- Leaderboard ordered by the game rules: distance-below-target, then the
  tie-breaks (furthest progress via the `Ordering[Progress]` definition from
  `common`, then goal difference) — implemented in Elm, tested against the same
  golden expectations as the Scala side.
- Derived values (totals, bust) computed from `Game` + `CompetitionStats` in one
  tested `Game`-logic module.

### Tests

- elm-test covers pure logic only: codec golden tests (shared JSON sample files
  with the Scala side), derived-logic tests (totals, bust, leaderboard order
  incl. tie-break interleaving), route parsing.
- The wiring — `update`, message handling, HTTP calls, ports, subscriptions — is
  deliberately covered in situ against the real API by the Playwright suite
  ([10-e2e-tests](10-e2e-tests.md)), not unit-mocked. Corollary: keep logic out
  of views and `update`, in plain testable functions (test strategy in
  [00-overview](00-overview.md)).

## Notes

- Build order within this piece: skeleton/routing + Api module → create/join
  flows → selection screen → game view → analysis views → admin panel.
- The dev server's simulated clock is the workhorse here: every state the views
  must handle (pre-tournament, mid-group, partial brackets, finished) is
  reproducible on demand.
