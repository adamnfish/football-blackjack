# 03 — API game logic

**Status: designed · Phase 2** (implemented incrementally, endpoint by endpoint)

## Goal

Implement `API` (`backend/common/src/main/scala/com/adamnfish/fbj/API.scala`): the
`dispatch(operation, body)` entry point and the endpoints. This is the heart of the
game and is shared by the API Lambda and the dev server.

## Current state

- `API` class skeleton: every method `???`; takes only `Persistence` as a dependency
- `Request`/`Response`/`Errors` enums fully modelled in `models/messages.scala`
- Domain models complete with codecs and round-trip tests

## Depends on

- [02-persistence](02-persistence.md) — the in-memory implementation and the
  decided trait: `Try[Option[Versioned[...]]]` loads, conditional saves failing
  with `ConcurrentModification`
- No football-data dependency: competitions come from configuration and stats from
  the store (see below)

## Decided

- **Error channel**: stick with `Try`, with our own exception type carrying the
  `Errors` value so expected domain failures travel inside the `Try` and the
  Lambda/dev server can pattern-match them out into HTTP responses. Keeping it
  simple deliberately; if concurrency needs ever force it, the fallback is Cats
  Effect with `MonadError` in a tagless-final structure — not required now.
- **Wire format: operation in path** — `POST /api/{operation}` (kebab-case, e.g.
  `create-game`) with the body holding just that request's fields;
  `dispatch(operation, body)` selects the endpoint and each endpoint decodes its
  own payload. Readable logs and per-route metrics; needs per-case decoders for
  the `Request` enum cases (they're products, so per-case semiauto derivation
  should work; hand-written decoders as fallback).
- **Lost keys / multi-device: admin can re-share links** — the game admin can
  retrieve every player's personal link and re-send it (same trust model as the
  shared spreadsheet). Multi-device is just "open your personal link on the other
  device".
- **Competitions come from configuration**: the API is constructed with the list
  of available competitions (in practice one at a time; populated manually for the
  next major tournament). `createGame` validates `competitionCode` against config
  and requires stats to already exist in the store (else `CompetitionNotFound`).
  Future enhancement, not now: richer competition config with date checks.
- **Entry deadline: derived from stats** — joins/edits are auto-rejected once the
  stored `CompetitionStats` show the tournament has started (any team's progress
  beyond `NotStarted`), with no clock or fixture knowledge. An explicit admin
  unlock overrides the auto-lock.
- **Incremental delivery (walking skeleton)**: endpoints land one at a time in
  phase 2, each together with the frontend flow that consumes it and the
  Playwright scenario that specifies it ([10-e2e-tests](10-e2e-tests.md)).
  Suggested order: ping → createGame → fetchGameInfo → editTeams (creator
  selection) → joinGame → lockGame/unlockGame → fetchPlayerKeys. Stats come from
  the store as designed — in phase 2 the store is seeded with canned
  `CompetitionStats` fixtures via the dev server.

## Approach

### Model & message changes

- **`GameRecord(game: Game, playerKeys: Map[PlayerId, PlayerKey])`** becomes the
  persisted unit (`Persistence` deals in `Versioned[GameRecord]` — amendment to
  [02-persistence](02-persistence.md)). Keys must live server-side for auth and
  admin re-sharing, but can never ride on `Game`, which is returned whole in
  public responses.
- **`Game.locked: Boolean` → `lockState: LockState`** enum: `Auto` (default —
  effective lock derived from competition stats), `Locked` (admin closed it
  early), `Unlocked` (admin explicitly reopened, overriding the auto-lock).
  Effective lock = `Locked`, or `Auto` when the competition has started.
- **New messages**: `FetchPlayerKeys(gameId, auth)` → `PlayerKeysFetched(...)`
  (admin only, powers link re-sharing).
- **New `Errors` cases**: `GameLocked`, `Unauthorized` (bad/missing player key, or
  non-admin calling an admin endpoint).

### Dispatch & endpoints

- `dispatch` maps the operation string to the endpoint; unknown operation is a
  validation-style failure. Each endpoint: decode payload → validate → load →
  apply game logic → conditional save → respond.
- **createGame**: validate name/settings (non-empty names, sensible `goalLimit`,
  `teamCount ≥ 1`), competition in config, load its latest stats (else
  `CompetitionNotFound`); generate ids (`GameId` = short human-friendly join code,
  `PlayerId`/`PlayerKey` = UUIDs); the creator becomes `gameAdmin` and the game's
  first player (with an empty selection — see Notes on how they pick teams); save
  with `version = 0` (create-condition).
- **joinGame**: load (→ `GameNotFound`), effective-lock check (→ `GameLocked`),
  validate: `PlayerNameTaken`, selection size == `teamCount`, teams distinct and
  members of the competition, combination unique **as a set** (order-insensitive)
  → `TeamSelectionTaken`; append player + key, conditional save.
- **editTeams**: authenticate key (→ `Unauthorized`), effective-lock check, same
  selection validations, conditional save.
- **lockGame / unlockGame**: admin key only; set `Locked` / `Unlocked`.
- **fetchGameInfo**: public by `gameId`; returns `Game` + latest stats
  (selections and names are public, per the trust model).
- **fetchPlayerKeys**: admin key only; returns players with their keys for
  re-sharing.
- **Conflict handling**: on `ConcurrentModification`, re-load and re-validate once
  — the retry either succeeds or yields the honest domain error.

### Validation

- Small per-request validation functions accumulating field problems into
  `ValidationErrors` (single-field failures use `ValidationError`), run before any
  writes.

### Tests

- Integration tests in the house style (test strategy in
  [00-overview](00-overview.md)): construct `API` with the in-memory
  `Persistence` (seeded with stats) and drive `dispatch` with operation + JSON
  body, asserting on the response/error — testing the assembled application's
  requests and responses rather than methods in isolation. Happy path + each
  error case per endpoint; lock-state matrix (Auto pre/post kick-off, Locked,
  Unlocked); a conflict test with a stubbed `Persistence` that fails the first
  save.
- Property tests where they sharpen the point: selection uniqueness is a set
  property (any permutation of a taken combination is rejected); validation
  rules hold for generated inputs, not just hand-picked examples.

## Notes

- Scoring/analysis (who's bust, leaderboard, tie-breaks) is derived client-side
  from `Game` + `CompetitionStats`; the `Ordering[Progress]` underpinning
  tie-breaks lives tested in `common` ([01-football-data](01-football-data.md))
  so the definition is canonical for any future server-side use.
- Creator team selection (decided in [07-frontend](07-frontend.md)): `CreateGame`
  stays slim (creator's name only); the creator picks teams immediately after via
  the same selection screen/`editTeams` call as joiners. The creator therefore
  briefly has an empty selection — game views and validation must tolerate that.
