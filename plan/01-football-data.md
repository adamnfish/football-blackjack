# 01 — football-data fetch and conversion

**Status: designed · Phase 3**

## Goal

Complete the `footballdata` module so it fully implements `CompetitionData`:

1. `FootballData.fetchCompetitionStats` — call the football-data.org v4 matches
   endpoint over HTTP (sttp is already a dependency) and decode the response
2. `FootballData.convertDataToStats` — pure conversion from a decoded
   `MatchesResponse` to our `CompetitionStats` domain type

## Current state

- `backend/footballdata/src/main/scala/com/adamnfish/fbj/footballdata/models.scala` —
  complete decoders for the matches response (statuses, stages, scores, undetermined
  teams), thoroughly tested against real fixtures in `src/test/resources/`
  (`matches.json`: full 2026 World Cup, 104 matches; `matches_match-in-play.json`)
- `FootballData.scala` — class skeleton with the base URL and both methods `???`
- Target type `CompetitionStats` (in `common`): per-`Team` map of `TeamStats(score, progress, status)`

## Depends on

Nothing technically — fixtures and both sides' models exist. In the
walking-skeleton order ([00-overview](00-overview.md)) this is **phase 3**: the
app is already deployed and playable locally on canned `CompetitionStats` stub
data; this piece replaces the stubs with real conversion, and unlocks the dev
server's simulated clock ([06-dev-server](06-dev-server.md)), which rewrites
`MatchesResponse` fixtures and needs `convertDataToStats` to turn them into stats.

## Decided

- **Goal counting** (rules: include extra time and open-play penalties, exclude
  shootouts). The fixture shows how the v4 fields relate — for shootout match
  537415: `regularTime` 1–1, `extraTime` 0–0, `penalties` 3–4, and `fullTime` 4–5.
  So `fullTime` is the *grand total including shootout goals*, and the component
  fields (only present when `duration` isn't `REGULAR`) are independent of each
  other. Goals for our game are therefore **`regularTime + extraTime` when those
  fields are present, otherwise `fullTime`**. `MatchScore` needs `regularTime`,
  `extraTime`, and `penalties` added as `Option[ScoreLine]` fields, with fixture
  tests covering the shootout case.

## Approach

### Model changes

- `MatchScore` gains `regularTime`, `extraTime`, `penalties` as `Option[ScoreLine]`
  (absent for regular-duration matches; the components are independent, `fullTime`
  is the grand total including shootout goals).
- `Team.crestUrl` becomes `Option[String]` (football-data's `crest` is nullable);
  the frontend shows a placeholder when absent.

### Conversion (`convertDataToStats` — pure)

- **Team discovery**: collect every determined team across all matches (all 48
  appear in the group stage). `shortName` → `TeamName.short`, `name` →
  `TeamName.long`, `tla` → `TeamTLA`, id stringified into `TeamId`.
- **Counting statuses**: a match contributes goals/appearances when its score is
  present and status is `Finished`, `InPlay`, `Paused`, or `Suspended` — i.e.
  **live scores count** (stats are snapshots; totals ticking up mid-match is part
  of the fun). `Scheduled`/`Timed`/`Postponed`/`Cancelled` contribute nothing.
- **Goals**: for each counted match, per side:
  `regularTime + extraTime` when those fields are present, otherwise `fullTime`
  (this excludes shootout goals per the rules). Sums give `Score(goalsFor,
  goalsAgainst)`; goal difference for tie-breaks falls out of that. Teams with no
  counted matches get `Score(0, 0)`.
- **Progress** — strongest of two signals, because football-data populates
  knockout brackets with a lag (the pending-knockouts fixture has all Last16
  matches finished but only 3 of 4 QF matches populated):
  1. appearing (determined) in a match at stage S ⇒ reached S
  2. winning a *finished* knockout match at stage S (via `score.winner`) ⇒ reached
     the stage after S
  - Mapping: no started group matches ⇒ `NotStarted`; group stage ⇒
    `Group(startedMatchCount)`; `Last32`/`Last16`/`QuarterFinals`/`SemiFinals`
    reached ⇒ `Knockout(32/16/8/4)`; reaching the final (won a semi, or appears in
    the final) ⇒ `Knockout(2)`. `TopFour(rank)` only once actually resolved: final
    winner ⇒ 1, final loser ⇒ 2, third-place winner ⇒ 3, third-place loser ⇒ 4.
    Semi-final losers stay `Knockout(4)` until the third-place match resolves.
  - Ordering (for tie-breaks) — note the interleaving: the third-place match
    usually finishes *before* the final, so `TopFour(3)`/`TopFour(4)` can coexist
    with finalists still at `Knockout(2)`, and finalists rank above them:
    `NotStarted < Group(n) < Knockout(32) < Knockout(16) < Knockout(8) <
    Knockout(4) < TopFour(4) < TopFour(3) < Knockout(2) < TopFour(2) < TopFour(1)`.
- **Status**:
  - `Eliminated` on losing a finished `Last32`/`Last16`/`QuarterFinals` match,
    losing the third-place match, or losing the final.
  - Semi-final losers stay `Playing` until the third-place match finishes (they
    still have goals to score).
  - Group-stage elimination is **inferred from the Last32 bracket**: a team still
    at group-stage progress is `Eliminated` once all 16 Last32 matches have both
    teams determined and the team isn't among them. No group-standings logic; the
    cost is a short "still Playing" window between the groups ending and
    football-data filling the bracket.
  - The champion is never `Eliminated`.

### Fetch (`fetchCompetitionStats`)

- sttp4 synchronous `DefaultSyncBackend`, GET
  `/v4/competitions/{competitionId}/matches` with the `X-Auth-Token` header.
- Non-2xx responses and circe decode failures become `Try` failures with
  informative messages; `CompetitionStats.timestamp` is the fetch time.
- One request per invocation, so the free-tier rate limit (10 req/min) is a
  non-issue.

### Tests (fixture-driven, munit, in the `ModelsTest` style)

- Extend `ModelsTest` for the new score fields (shootout match 537415 exposes
  `regularTime`/`extraTime`/`penalties`).
- New conversion test suite:
  - `matches.json` (mid-tournament): goal totals incl. the shootout-exclusion rule,
    knockout progress, eliminated knockout losers
  - `matches_pending-knowckouts-and-progress.json`: reached-QF-via-win with an
    unpopulated bracket slot; group-elimination inference from the full R32 bracket
    (fix the filename typo → `matches_pending-knockouts-and-progress.json` when
    wiring it up)
  - `matches_match-in-play.json`: live goals counted for the in-play Last16 match
  - `TopFour` ranks need a completed-tournament fixture — derive one by completing
    the semis/third-place/final in a copy of `matches.json`
- Property tests (munit-scalacheck) where properties express the rules better
  than examples — candidates: every determined team in the input appears in the
  output; goal totals are non-negative and shootout goals are never counted,
  whatever the score shape; `Ordering[Progress]` is a total order consistent
  with the documented progression.

## Notes

- Conversion is pure — implement as a fold over matches per team, keeping
  `FootballData` free of any state beyond the API key.
- The `Progress` ordering may deserve a tested `Ordering[Progress]` in `common`,
  since game tie-breaks ([03-api](03-api.md)/[07-frontend](07-frontend.md)) depend
  on it.
