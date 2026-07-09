# 05 — Lambda handlers (api, data-service)

**Status: designed · Phase 1 (api stub) / Phase 5 (real wiring + data-service)**

## Goal

Create the two production entry-point modules declared in `build.sbt` but not yet
on disk:

1. `backend/api` (sbt `api`) — Lambda handler behind API Gateway; decodes the HTTP
   event, calls `API.dispatch(operation, body)`, encodes the response/errors;
   wires in the DynamoDB `Persistence`
2. `backend/data-service` (sbt `dataService`) — scheduled Lambda; wires
   `FootballData` + DynamoDB `Persistence` into `CompetitionJob`

## Current state

- Modules configured in `build.sbt` with `lambdaCore`/`lambdaEvents` dependencies
  and netty/apache client exclusions (pointing at `awsCrtClient` for AWS calls)
- No source directories yet

## Phase 1 slice — the walking skeleton's API

The `api` module is created in phase 1 with a stub handler: it accepts any
`POST /api/{operation}` event and returns 200 `"ok"`, giving the skeleton its
real wire shape (routing, packaging, deployment) with no logic. sbt-assembly and
the module's assembly settings land now, and CDK consumes the jar from day one.
The `data-service` module isn't created until phase 5.

Everything below describes the full (phase 5) version; the stub grows into it
without the surrounding infrastructure changing.

## Depends on

- Phase 1 stub: nothing but the build setup (sbt-assembly)
- Full version: [03-api](03-api.md) / [04-competition-job](04-competition-job.md) —
  the logic being wrapped; the shared HTTP mapping in `common` (operation
  extraction, `Errors` → status/body) that the dev server also uses
- Full version: [02-persistence](02-persistence.md) — the `dynamodb` adapter module
- Feeds [08-infrastructure](08-infrastructure.md)

## Decided

- **API Gateway: HTTP API (v2)** with a single proxy route for
  `POST /api/{operation}`; the handler works with `APIGatewayV2HTTPEvent`. CORS is
  expected to be moot (same-origin behind CloudFront —
  [08-infrastructure](08-infrastructure.md)).
- **Packaging: sbt-assembly fat jars on the java21 managed runtime**, deployed via
  CDK `Code.fromAsset`. JVM cold starts are acceptable at this scale; SnapStart or
  native image remain later options without re-architecting.
- **Configuration: deploy-time** — CDK resolves the football-data API key from SSM
  Parameter Store during deploy and injects it (plus table names and the
  configured competition list) as Lambda environment variables. Key rotation =
  redeploy, which is fine for a free-tier key.
- **Scheduled event input: none** — the EventBridge payload stays empty; the
  data-service Lambda reads the configured competition list from its environment
  (the same configuration the API uses) and iterates it.

## Approach

- **api handler**: implement `RequestHandler[APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse]`;
  extract the operation path parameter and body, delegate to the shared HTTP
  mapping in `common` → `API.dispatch`, translate the result (`Response` → 200 +
  JSON; domain exception → mapped status + `Errors` JSON; unexpected failure →
  500 + logged stack trace). Wiring (DynamoDB client, table names, competition
  config) happens once in the handler's constructor so warm invocations reuse it.
- **data-service handler**: `RequestHandler[ScheduledEvent, Unit]`; construct
  `FootballData(apiKey)` + DynamoDB persistence, run
  `CompetitionJob.fetchCompetition` per configured competition, throw on failure
  so the invocation errors (feeding the CloudWatch alarm from
  [04-competition-job](04-competition-job.md)).
- **Build**: add sbt-assembly to `project/plugins.sbt`; assembly settings on the
  two Lambda modules (merge strategy for the usual META-INF noise); CDK points at
  the assembled jars.
- Handlers stay thin — event parsing and dependency wiring only; everything
  interesting is already tested in `common`/`footballdata`/`dynamodb`.

## Tests

- The HTTP mapping (operation → endpoint, `Errors` → status codes) lives in
  `common` and is unit-tested there once, covering both Lambda and dev server.
- Handler-level: a light test constructing the handler with in-memory
  persistence and a synthetic `APIGatewayV2HTTPEvent`, asserting event→response
  translation (status codes, JSON bodies) without any AWS calls.
