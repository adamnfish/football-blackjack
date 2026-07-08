# 08 — Infrastructure (CDK)

**Status: designed**

## Goal

Fill in the CDK app under `infrastructure/` to provision everything production
needs, twice (test and production stages).

## Current state

- `infrastructure/lib/main.ts`: empty `FootballBlackjackStack`; CDK app boots
- TypeScript + `aws-cdk-lib` configured (`cdk.json`, `tsconfig.json`)

## Depends on

- [02-persistence](02-persistence.md) — decided: DynamoDB, two tables
- [05-lambdas](05-lambdas.md) — decided: HTTP API (v2), fat jars on java21,
  deploy-time SSM → env vars
- Feeds [09-cicd](09-cicd.md) — OIDC provider + deploy roles live here

## Decided

- **Stay with CDK** — the project leans on exactly what CDK automates (Lambda jar
  assets, `BucketDeployment`, deploy-time SSM resolution, per-stage
  parameterisation); pokerdot remains an architecture reference, not a template
  source.
- **Custom domain from day one** — join links are shared with friends. Route53
  zone + ACM cert (us-east-1, for CloudFront), one subdomain per stage (e.g.
  `test.…` / apex or `www`). Choosing/owning the domain is the one manual
  prerequisite.
- **Single AWS account, two stages** — `FootballBlackjack-test` / `-prod` stacks
  side by side, separated by naming and per-stage deploy roles.
- **API behind CloudFront** — the distribution serves the SPA from S3 and routes
  `/api/*` to the HTTP API origin: same-origin everywhere, no CORS, matching the
  Vite-proxy dev setup.

## Approach

### Stacks

- **Per-stage app stack** (`FootballBlackjack-{stage}`), stage passed via CDK
  context/props:
  - two DynamoDB tables (on-demand): games (PK `gameId`), competition-stats
    (PK `competitionId`, SK `timestamp`); `RemovalPolicy.RETAIN` on prod
  - API Lambda (assembly jar via `Code.fromAsset`) + HTTP API with the
    `POST /api/{operation}` proxy route
  - data-service Lambda + EventBridge rate rule (15–30 min), with the rule's
    enabled state and the competition list as per-stage config — switched off
    between tournaments
  - webroot S3 bucket (private, OAC) + CloudFront distribution: default behaviour
    → S3 (SPA fallback for client routes → `index.html`), `/api/*` behaviour →
    HTTP API origin (caching disabled); stage domain + cert
  - `BucketDeployment` publishing `frontend/dist` (frontend build runs before
    `cdk deploy`)
  - env vars into the Lambdas: table names, competition config, football-data
    API key resolved from SSM at deploy time
  - operations: log retention (~1 month), CloudWatch alarm on sustained
    data-service failures → SNS → email ([04-competition-job](04-competition-job.md))
- **Once-per-account CI stack** (deployed manually, rarely changes): GitHub OIDC
  identity provider + per-stage deploy roles trusted for this repo/environment
  ([09-cicd](09-cicd.md)).

### Prerequisites (manual, one-off)

- Domain choice; Route53 hosted zone; CDK bootstrap of the account; the SSM
  parameter holding the football-data API key; SNS topic email subscription
  confirmation.

## Notes

- Deploy order within a release: sbt assembly + frontend build first, then
  `cdk deploy` picks up both artifacts.
- Nothing here blocks local development; this piece lands alongside
  [05-lambdas](05-lambdas.md) when the backend is ready to ship.
