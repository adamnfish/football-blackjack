# 08 — Infrastructure (CDK)

**Status: phase 0 complete (CI stack deployed 2026-07-10) · phase 1 skeleton stacks built (PR #5) · Phases 4–5 (data layer) still to come**

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
- **Custom domain from day one** — join links are shared with friends. One
  subdomain per stage under a parent domain whose Route53 zone already exists
  in this account; the stack creates each stage's alias records. ACM certs
  (us-east-1, for CloudFront) are provisioned **manually** per stage in phase 0
  and imported by ARN — no cross-region cert stack, no us-east-1 bootstrap.
- **Per-stage domain config is discovered from SSM, never stored in the repo
  or GitHub** — `/football-blackjack/{stage}/domain-name` / `hosted-zone-id` /
  `certificate-arn`, created manually in phase 0 and resolved at **deploy
  time** (`valueForStringParameter` → CloudFormation SSM-typed parameters,
  the same idiom as the football-data API key in
  [05-lambdas](05-lambdas.md)). Domain names therefore never appear in the
  repo, the snapshots, or GHA settings; the GHA environments hold only the
  deploy role ARN and region. Accepted trade-off: `cdk diff` doesn't surface
  SSM value changes — a repointed parameter takes effect silently on the next
  deploy.
- **Single AWS account, two stages** — `FootballBlackjack-test` / `-prod` stacks
  side by side, separated by naming and per-stage deploy roles.
- **Tagging: every resource carries `app: football-blackjack` and
  `stage: test|prod`** — applied once with `Tags.of(...)` at the app/stack level
  so CDK propagates them to every taggable resource. Stage names are exactly
  `test` and `prod`, matching the stack names and the GHA environment names
  ([09-cicd](09-cicd.md)). The `app` tag identifies the application account-wide
  (cost allocation, console filtering) and leaves the door open for tag-based
  IAM conditions later.
- **API behind CloudFront** — the distribution serves the SPA from S3 and routes
  `/api/*` to the HTTP API origin: same-origin everywhere, no CORS, matching the
  Vite-proxy dev setup.
- **CDK snapshot tests** — the synth'd CloudFormation for both stages is
  committed as vitest snapshots, so every infrastructure change (or its absence)
  appears explicitly in the PR diff, and unexpected template changes fail
  build-and-test.

## Approach

### Stacks

- **Per-stage app stack** (`FootballBlackjack-{stage}`), stage passed via CDK
  context/props. Built in two steps:

  **Skeleton (phase 1):**
  - API Lambda (assembly jar via `Code.fromAsset` — the stub handler from
    [05-lambdas](05-lambdas.md)) + HTTP API with the `POST /api/{operation}`
    proxy route
  - webroot S3 bucket (private, OAC) + CloudFront distribution: default behaviour
    → S3, `/api/*` behaviour → HTTP API origin (caching disabled); stage domain
    + imported cert, both resolved from SSM at deploy time. SPA fallback for
    client routes is a viewer-request CloudFront Function on the S3 behaviour
    rewriting extensionless paths to `index.html` — not distribution-wide
    error responses, which would rewrite API error bodies too
  - DNS: A/AAAA alias records for the stage domain as one L1
    `CfnRecordSetGroup` — the L2 `ARecord` does synth-time string logic on
    record names, which silently misbehaves on deploy-time tokens; alias
    target zone is the fixed `CloudFrontTarget.CLOUDFRONT_ZONE_ID`
  - `BucketDeployment` publishing `frontend/dist` (frontend build runs before
    `cdk deploy`), with its `distribution`/`distributionPaths` props set so the
    deployment issues the CloudFront cache invalidation itself — CDK-native, no
    post-deploy workflow step, extra role, or stack output needed. Without it a
    cached `index.html` hides new releases until TTL expiry
  - log retention (~1 month)

  **Added as the data layer lands (phases 4–5):**
  - two DynamoDB tables (on-demand): games (PK `gameId`), competition-stats
    (PK `competitionId`, SK `timestamp`); `RemovalPolicy.RETAIN` on prod (phase 4)
  - data-service Lambda + EventBridge rate rule (15–30 min), with the rule's
    enabled state and the competition list as per-stage config — switched off
    between tournaments (phase 5)
  - env vars into the Lambdas: table names, competition config, football-data
    API key resolved from SSM at deploy time (phase 5)
  - CloudWatch alarm on sustained data-service failures → SNS → email
    ([04-competition-job](04-competition-job.md)) (phase 5). Deliberately no
    alarm on the API Lambda: users notice a broken API, nobody notices a
    silently stale stats job
- **Once-per-account CI stack** (phase 0; deployed manually with admin
  credentials, never from CI/CD — the deploy roles shouldn't manage the stack
  that defines them): just the two per-stage deploy roles trusted for this
  repo/environment ([09-cicd](09-cicd.md)). The account's existing GitHub
  OIDC identity provider (shared infrastructure owned by another project; an
  account holds one per URL) is **referenced, not created** — its ARN is
  deterministic
  (`arn:aws:iam::{account}:oidc-provider/token.actions.githubusercontent.com`),
  built from the stack's account id. Two plain IAM roles and nothing else: as
  static as hand-written CloudFormation, no custom-resource Lambdas, and CDK
  library bumps never force a redeploy. If that provider ever disappears,
  the roles stop authenticating and the fix is to add it to this stack.

### GHA deploy role scoping (phase 0)

The deploy roles stay tiny because CDK's permission model concentrates the
power in the bootstrap roles:

- Each per-stage deploy role grants **only `sts:AssumeRole` on the CDK
  bootstrap roles** (deploy, file-publishing, lookup; no container assets, so
  the image-publishing role isn't needed) — no direct resource permissions at
  all. `cdk deploy` does everything through those roles.
- The **trust policy is the real gate**: federated via the GitHub OIDC
  provider, `aud` = `sts.amazonaws.com`, and `sub` pinned to
  `repo:adamnfish/football-blackjack:environment:{stage}` — only workflow runs
  in that GHA environment can assume that stage's role.
- The **CDK bootstrap roles are left as-is** (including the CloudFormation
  execution role's default `AdministratorAccess`): the account is shared with
  other applications, so the account-wide bootstrap roles aren't ours to scope.
  Access control rests on the deploy roles' trust conditions and GHA
  environment protection; revisit only if the account's sharing situation
  changes.

### Snapshot tests (phase 1, maintained throughout)

- vitest in the `infrastructure` package: synth the app for both stages with
  pinned config and assert `Template.fromStack(...).toJSON()` against committed
  snapshots. Both real stages are snapshot (not a synthetic third stage) so
  per-stage differences — prod's `RemovalPolicy.RETAIN`, the schedule's enabled
  flag — are covered in diffs too.
- Determinism: stage config (competition list, …) comes in via props/context;
  domain/zone/cert values are deploy-time SSM parameters, so synth needs no
  credentials or lookups (no `cdk.context.json` cache) and the templates carry
  only the SSM parameter *names* — real domains never reach the snapshots. A
  snapshot serializer masks asset hashes (Lambda jars,
  `BucketDeployment` bundles) so snapshots only churn on real infrastructure
  changes; asset paths are stack props, so tests synth against small
  placeholder files instead of requiring built jars.
- Snapshots are updated deliberately (`vitest -u`) in the same change that causes
  them — the reviewer sees the CloudFormation delta alongside the CDK code
  change.

### Prerequisites (manual, one-off — phase 0)

The [phase-0-runbook](phase-0-runbook.md) owns the step-by-step detail
(certificates, SSM parameters, bootstrap check, CI stack, GHA environments,
verification, and what's deferred to phase 5).

## Notes

- Deploy order within a release: sbt assembly + frontend build first, then
  `cdk deploy` picks up both artifacts.
- In the walking-skeleton order this piece leads rather than trails: the
  skeleton stack ships in phase 1 with the stub Lambda and hello-world frontend,
  and grows as the data layer arrives.
