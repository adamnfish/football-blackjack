# 04 — Competition job

**Status: designed**

## Goal

Implement `CompetitionJob.fetchCompetition`
(`backend/common/src/main/scala/com/adamnfish/fbj/CompetitionJob.scala`): fetch
fresh stats for a competition via `CompetitionData` and persist them via
`Persistence`. This is the logic the scheduled data-service Lambda wraps, and the
dev server can trigger manually.

## Current state

- Class skeleton taking `Persistence` and `CompetitionData`; method `???`
- Both collaborator traits defined; designs done
  ([01](01-football-data.md), [02](02-persistence.md))

## Depends on

- [01-football-data](01-football-data.md) — `CompetitionData` implementation
- [02-persistence](02-persistence.md) — `saveCompetitionStats`

## Decided

- **Which competitions**: the configured competition list (same configuration the
  API uses — see [03-api](03-api.md)); the scheduled Lambda iterates it (in
  practice: one competition).
- **Writes**: every run appends a new snapshot partitioned by load time — no
  skip-unchanged logic ([02-persistence](02-persistence.md)).
- **Cadence: every 15–30 minutes** while a tournament is on (single EventBridge
  rate rule, enabled around tournaments — [08-infrastructure](08-infrastructure.md)).
  Totals update a few times per match; trivially inside the free-tier rate limit.
  Match-aware scheduling stays a possible later enhancement.
- **Failure behaviour**: on fetch/decode/persist failure, stale stats stay in
  place and the `Try` failure propagates so the Lambda invocation errors. A
  CloudWatch **alarm on sustained/consecutive failures** (SNS → email) catches
  real breakage (dead API key, format change) without paging on transient blips,
  which self-heal on the next run.

## Approach

- `fetchCompetition(competitionId)`: `competitionData.fetchCompetitionStats(id)`
  → `persistence.saveCompetitionStats(stats)`; the whole thing is the composition
  of two `Try`s. Nothing else — retries, scheduling, and alerting live in the
  infrastructure around it.
- Tests: stub `CompetitionData` + in-memory `Persistence` — success writes the
  snapshot; failure of either collaborator propagates and writes nothing (or
  leaves the previous latest intact).

## Notes

- **Accepted trade-off**: the entry auto-lock ([03-api](03-api.md)) derives from
  stored stats, so it engages up to one fetch-interval (~15–30 min) after the
  opening kick-off. Admins can lock manually at kick-off if that window matters;
  goals in the opening minutes are rare enough that this is acceptable.
