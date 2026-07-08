# 09 — CI/CD (GitHub Actions)

**Status: designed**

## Goal

GitHub Actions pipelines that test every change and deploy on demand to the
**test** and **production** GHA environments, authenticating to AWS via **OIDC**
(no long-lived credentials).

Reference: [adamnfish/pokerdot](https://github.com/adamnfish/pokerdot) for
overall shape (adapted: it uses websockets and CloudFormation where this project
uses HTTP and CDK).

## Current state

- Nothing: no `.github/` directory; OIDC provider + deploy roles are specified in
  [08-infrastructure](08-infrastructure.md)'s once-per-account CI stack

## Depends on

- [08-infrastructure](08-infrastructure.md) — OIDC provider and per-stage deploy
  roles (deployed manually once, before the first GHA deploy)
- [10-e2e-tests](10-e2e-tests.md) — the suite two of the workflows run

## Decided — workflow shape

Deploys are **explicitly triggered** (`workflow_dispatch`), not automatic on merge:

1. **build-and-test** (on PRs and main pushes): sbt tests, elm-test, frontend
   build, CDK synth, plus the **local e2e suite** — Playwright against the dev
   server (DynamoDB Local via Docker, simulated clock) with screenshots uploaded
   as workflow artifacts, so design changes are visually reviewable on the PR.
2. **deploy** (`workflow_dispatch`, environment input: test | production): runs
   the full build-and-test against the checked-out code, then deploys that build
   to the selected environment (sbt assembly + frontend build + `cdk deploy`)
   using the environment's OIDC role. No artifact promotion between
   environments — each deploy builds fresh from the selected ref.
3. **e2e-test** (`workflow_dispatch`, environment input): runs the e2e suite
   against the selected *deployed* environment (full suite on test; restricted
   suite on production — see [10-e2e-tests](10-e2e-tests.md)).

Branch testing on AWS = manually run **deploy** on the branch with the test
environment, then **e2e-test** against test.

## Approach

- **OIDC**: one deploy role per environment, trust condition scoped to this repo
  + the specific GHA environment; the workflow's `permissions: id-token: write`
  + `aws-actions/configure-aws-credentials` with the role ARN. The roles need
  what `cdk deploy` needs (assume the CDK bootstrap roles) plus artifact upload.
- **GHA environments**: `test` and `production` hold the role ARN and any
  per-stage variables; optional required-reviewer protection on `production` is
  a one-click knob to add later if wanted.
- **Caching**: coursier/sbt and pnpm caches; Playwright browser cache.
- **Bootstrap order** (manual, once): CDK bootstrap → CI stack (OIDC + roles) →
  create the two GHA environments in repo settings → first dispatch deploy.

## Notes

- The deploy workflow deploying test and prod from the same definition keeps the
  two environments honest; the only differences live in stage config.
