# Phase 2 — status (2026-07-12)

First tranche of phase 2 built, plus the e2e-test workflow from phase 1. Seven
PRs across three independent chains, reviewed and verified, ready to merge.

## PR chains

Backend (stacked — merge in order; GitHub retargets each as its base merges):

1. [#16](https://github.com/adamnfish/football-blackjack/pull/16)
   `phase2-persistence` — `Versioned`/`GameRecord` trait changes, in-memory
   implementation, contract test suite ([02-persistence](02-persistence.md))
2. [#18](https://github.com/adamnfish/football-blackjack/pull/18)
   `phase2-api-core` — dispatch, shared HTTP mapping in `common`, `createGame`,
   `fetchGameInfo` ([03-api](03-api.md))
3. [#20](https://github.com/adamnfish/football-blackjack/pull/20)
   `phase2-api-endpoints` — `joinGame`, `editTeams`, `lockGame`/`unlockGame`,
   `fetchPlayerKeys`; lock-state matrix, conflict retry, property tests
4. [#21](https://github.com/adamnfish/football-blackjack/pull/21)
   `phase2-dev-server` — dev server serves real `API.dispatch`; canned stats
   fixtures (pre-tournament, mid-group, knockouts, finished); `/dev` panel with
   job trigger and demo-game seeding ([06-dev-server](06-dev-server.md))

Frontend (stacked, independent of the backend chain):

5. [#19](https://github.com/adamnfish/football-blackjack/pull/19)
   `phase2-frontend-skeleton` — `Browser.application`, routing, `Api` module
   (all 8 operations), ports + localStorage key handling, elm-test in CI
   ([07-frontend](07-frontend.md)). Complete and verified.
6. [#22](https://github.com/adamnfish/football-blackjack/pull/22)
   `phase2-frontend-flows` — create/join/selection flows, golden-sample codec
   fixtures (`fixtures/api/`), Playwright scenarios with per-step screenshots.
   Complete and verified: the e2e suite passed 4/4 against a local merge with
   `phase2-dev-server`, with all 9 step screenshots.

Standalone:

7. [#17](https://github.com/adamnfish/football-blackjack/pull/17)
   `e2e-test-workflow` — runs the smoke subset against deployed environments;
   stage domain read from SSM at runtime ([09-cicd](09-cicd.md)). Adds scoped
   `ssm:GetParameter` to the deploy roles, so the CI stack needs a manual
   redeploy before first dispatch.

## Notes for review

- `EditTeams` gained a `gameId` field — the modelled request had no way to
  locate the game (auth carries only the player key). Recorded in #20.
- `PlayerKeysFetched` returns `Map[PlayerId, PlayerKey]` with the raw id
  string as the JSON key.
- Join codes: 6 chars, unambiguous lowercase alphabet, one collision retry.
- Elm codecs were aligned with the actual Scala code on the backend branches,
  verified by golden-sample tests (munit side lands after the backend chain
  merges).
- Known one-line conflict in `e2e/tests/api.spec.ts` between #17, #21 and #22
  (#17 tags the ping test `@smoke`, #21 and #22 change the body assertion). The
  merged result needs the `@smoke` tag and #22's tolerant assertion.
- All seven PRs reviewed 2026-07-12: no correctness issues found.

## Next steps

1. ~~Re-verify #22's e2e suite against a local merge with `phase2-dev-server`~~
   Done: 4/4 passed with all 9 step screenshots. PRs marked ready.
2. Merge the chains; update [00-overview](00-overview.md) status table.
3. First dispatch deploy to test (outstanding from phase 1), CI stack redeploy,
   then dispatch the e2e-test workflow.
4. Remaining phase 2: full game view (leaderboard, 60s poll), analysis views,
   admin panel (lock/unlock, re-share links), munit golden tests, each with its
   Playwright scenario.
