# 02 — Persistence implementations

**Status: in-memory built · Phase 4 (DynamoDB)**

## Goal

Provide implementations of the `Persistence` trait
(`backend/common/src/main/scala/com/adamnfish/fbj/services/Persistence.scala`):

1. An **in-memory** implementation for tests and the dev server
2. A **production** implementation backed by an AWS data store, used by both Lambdas

The trait is whole-document load/save for two record types: `Game` (keyed by
`GameId`) and `CompetitionStats` (keyed by `CompetitionId`).

## Current state

- Trait finalised per the approach below: `Versioned[GameRecord]` loads/saves,
  `Try[Option[...]]` reads, conditional saves failing with
  `ConcurrentModification`
- `InMemoryPersistence` in `common` main sources, used by the API integration
  tests and the dev server
- `PersistenceContractTests` behavioural suite runs against the in-memory
  implementation; ready to run against the DynamoDB adapter when it exists
- `Dependencies.scala` has `awsCrtClient` defined but no store-specific AWS SDK
  module yet; the DynamoDB implementation (phase 4) is unstarted

## Depends on

Nothing for the in-memory implementation, which is needed in **phase 2** — it
backs the API integration tests and the dev server. The DynamoDB implementation
is **phase 4**, and feeds into [05-lambdas](05-lambdas.md) and
[08-infrastructure](08-infrastructure.md).

## Decided

- **Stats history**: competition stats are saved **partitioned by load time** (this
  is why `CompetitionStats` carries an `Instant`), but reads only ever need the
  latest snapshot for now. No requirement to query history today — the point is to
  leave the door open (e.g. future time-series charts). Practical shape: writes are
  append-only keyed by `(CompetitionId, timestamp)`; `loadCompetitionStats` returns
  the most recent. This constrains the store design below (e.g. DynamoDB sort key
  on timestamp with a descending-limit-1 query, or timestamped S3 keys plus a
  "latest" pointer).

- **Production store: DynamoDB** — conditional writes enforce the
  first-come-first-serve rules properly, and the append-only stats history is a
  textbook partition-key + sort-key fit. Two plain tables, no single-table design.
- **Concurrency: optimistic versioning** via a `Versioned[A](value: A, version: Int)`
  envelope in the trait; `saveGame` is a conditional write on the expected version.
- **Not-found: `Try[Option[...]]`** — absence is a normal result; the API maps
  `None` to `Errors.GameNotFound`/`CompetitionNotFound`. `Try` failures are
  reserved for real problems (network, corrupt data, version conflict).
- **Serialization**: items store the record as a JSON blob using the existing circe
  codecs, alongside the key attributes and `version`.

## Approach

### Trait changes (`services/Persistence.scala`)

```scala
case class Versioned[A](value: A, version: Int)

// the persisted unit: the public Game plus server-side secrets
// (player keys must never ride on Game itself, which is returned
// whole in API responses — see 03-api)
case class GameRecord(game: Game, playerKeys: Map[PlayerId, PlayerKey])

trait Persistence {
  def loadGame(gameId: GameId): Try[Option[Versioned[GameRecord]]]
  def saveGame(game: Versioned[GameRecord]): Try[Versioned[GameRecord]]

  def loadCompetitionStats(competitionId: CompetitionId): Try[Option[CompetitionStats]]
  def saveCompetitionStats(competitionStats: CompetitionStats): Try[CompetitionStats]
}
```

- `saveGame(Versioned(record, n))` writes version `n + 1` conditional on the stored
  version still being `n`, and returns the new envelope. `version = 0` means
  "create": the write is conditional on the game not existing. A conflict fails
  the `Try` with a dedicated `ConcurrentModification` exception the API layer can
  catch (retry the read-modify-write, or surface `TeamSelectionTaken` etc.).
- `loadCompetitionStats` returns the **latest** snapshot; `saveCompetitionStats`
  is an unconditional append keyed by `(competitionId, timestamp)` — no
  versioning needed.

### DynamoDB implementation

- New sbt module `backend/dynamodb` (pattern as `footballdata`: an adapter module
  implementing a `common` trait), depended on by the `api` and `data-service`
  Lambdas. Keeps AWS SDK deps out of `common`. Uses AWS SDK v2 `dynamodb` +
  `awsCrtClient` (already in `Dependencies.scala`; the build's netty/apache
  exclusions anticipate this).
- **games table**: PK `gameId` (S); attributes `version` (N), `json` (S — the
  circe-encoded `GameRecord`).
- **competition-stats table**: PK `competitionId` (N), SK `timestamp` (S,
  ISO-8601 instant — sorts lexicographically = chronologically); attribute `json`
  (S). Latest = query, `ScanIndexForward = false`, `Limit = 1`.
- Table names passed in via constructor (env vars in the Lambdas, from CDK).

### In-memory implementation

- Lives in `common` main sources: a synchronized map that enforces exactly the
  same version-check semantics. Used by `common` tests (which must not need
  Docker) and by the dev server through phases 2–3; the dev server swaps to the
  real DynamoDB adapter against DynamoDB Local once it exists in phase 4
  ([06-dev-server](06-dev-server.md)).

### Tests

- A **contract test suite** written against the `Persistence` trait: round-trips,
  latest-stats-wins, version conflict on stale save, create-vs-exists. Runs
  against the in-memory implementation in `common`; the same suite runs against
  DynamoDB Local (Docker, as set up for the dev server) as an integration test
  for the real adapter. This is the house pattern (test strategy in
  [00-overview](00-overview.md)): effect services stay thin, and one behavioural
  suite runs against every implementation of the trait.
- Unit tests for the DynamoDB item ↔ model mapping.

## Notes

- Traffic is tiny (a friends sweepstake); DynamoDB on-demand is effectively free.
- ID generation (`GameId` as short join code? `PlayerKey` as UUID?) is an API
  concern — deferred to [03-api](03-api.md); DynamoDB's create-condition handles
  any collision.
- The trait change ripples into [03-api](03-api.md) (endpoints handle
  `Option` + `Versioned` + conflict retries) and [08-infrastructure](08-infrastructure.md)
  (two tables in CDK).
