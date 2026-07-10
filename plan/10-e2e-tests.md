# 10 — End-to-end tests

**Status: designed · Phase 1 (minimal suite), then grows in lockstep with every increment**

## Goal

A browser-driven end-to-end suite that exercises the real app — create a game,
join it, pick teams, view results — runnable against the **local** app, the
deployed **test** environment, or (restricted) **production**, producing
**screenshots** as a first-class output for quick visual checks of design
changes.

## Current state

- Nothing yet

## Depends on

- [06-dev-server](06-dev-server.md) — the local target: DynamoDB Local,
  fixture data, simulated clock, seed endpoint
- [07-frontend](07-frontend.md) — flows to drive (add stable test ids as views
  are built)
- [09-cicd](09-cicd.md) — runs it on PRs (local) and via the e2e-test workflow
  (deployed environments)

## Decided

- **Tooling: Playwright + TypeScript**, as an `e2e` package in the pnpm
  workspace — first-class screenshots/traces and multiple browser contexts for
  multi-player flows.
- **The suite encodes the requirements**: every API endpoint / frontend flow
  lands together with the Playwright scenario that specifies it. This is also
  where the frontend's wiring (`update`, messages, HTTP, ports, subscriptions)
  gets its coverage, in situ against the real API — see the test strategy in
  [00-overview](00-overview.md).
- **Targets** via config/env (base URL + capability flags):
  - **local**: DynamoDB Local (Docker) + dev server (sbt) + Vite with the `/api`
    proxy; full suite, including simulated-clock scenarios — pre-tournament,
    mid-group, knockouts, finished states are all reproducible
    ([06-dev-server](06-dev-server.md))
  - **test**: full suite; live data, so stats-dependent assertions are
    structural rather than value-exact. Once live stats show the tournament has
    started, the auto-lock rejects joins/edits ([03-api](03-api.md)) — join-flow
    scenarios must go through admin unlock, and the broader between-tournaments
    question is tracked in [00-overview](00-overview.md)'s open questions
  - **prod: read-only + create smoke** — load the app, fetch a known
    long-lived game, and create one clearly-named smoke game
    (`smoke-{run id}`) to prove the write path; never joins or mutates real
    games
- **Screenshots**: per-step/per-state screenshots of the user flows uploaded as
  CI artifacts — the visual regression record for design changes; automated
  golden-image diffing deliberately deferred until the design stabilises.

## Approach

- Core flows: create game → creator selection → share/join as second player
  (second browser context) → selection conflict (`TeamSelectionTaken`) →
  lock/unlock as admin → game view across simulated tournament states →
  personal-link auth (localStorage, key stripped from URL) → admin re-share.
- Local orchestration: a compose/script bringing up DynamoDB Local, the dev
  server, and Vite; Playwright's `webServer` config can own frontend startup.
- Capability flags gate what runs where (clock control and seeding exist only
  locally; write scope restricted on prod).
- Smoke-game naming makes any production residue identifiable; cleanup is
  unnecessary (games are unlisted and free) but possible later.

## Notes

- The minimal suite is a **phase 1 deliverable**, not an afterthought: page
  loads (screenshot) and `POST /api/ping` returns 200, running against the stub
  dev server locally and against deployed environments via the e2e-test
  workflow. It pays for itself through the whole build.
