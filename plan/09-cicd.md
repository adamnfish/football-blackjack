# 09 — CI/CD (GitHub Actions)

**Status: all three workflows built · next: first dispatch deploy to test, verify at the stage domain, then run e2e-test against it**

## Goal

GitHub Actions pipelines that test every change and deploy on demand to the
**test** and **prod** GHA environments (named to match the `stage` tag and stack
names — [08-infrastructure](08-infrastructure.md)), authenticating to AWS via
**OIDC** (no long-lived credentials).

Reference: [adamnfish/pokerdot](https://github.com/adamnfish/pokerdot) for
overall shape (adapted: it uses websockets and CloudFormation where this project
uses HTTP and CDK).

## Current state

- **build-and-test**: backend, frontend, infrastructure, and e2e jobs, per the
  workflow shape below
- **deploy**: calls build-and-test via `workflow_call`, then builds the
  artifacts (`sbt api/assembly`, frontend build) and runs `cdk deploy` for the
  selected environment's stack under its OIDC role. Not yet dispatched — the
  first deploy to test is the next step.
- **e2e-test**: dispatches against the selected environment's deployed app.
  Reads the stage domain from SSM under the environment's OIDC role (the deploy
  roles hold `ssm:GetParameter` on the stage's `domain-name` parameter — the CI
  stack must be redeployed manually when that changes) and runs the
  `@smoke`-tagged subset of the Playwright suite against it. Not yet run — it
  needs the first deploy.

## Depends on

- [08-infrastructure](08-infrastructure.md) — OIDC provider and per-stage deploy
  roles (deployed manually once, before the first GHA deploy)
- [10-e2e-tests](10-e2e-tests.md) — the suite two of the workflows run

## Decided — workflow shape

Deploys are **explicitly triggered** (`workflow_dispatch`), not automatic on merge:

1. **build-and-test** (on PRs and main pushes): formatting checks
   (`scalafmtCheckAll`, `elm-format --validate`), sbt tests, elm-test, frontend
   build, CDK snapshot tests (which synth the app for both stages —
   [08-infrastructure](08-infrastructure.md)), plus the **local e2e suite** —
   Playwright against the dev server with screenshots uploaded as workflow
   artifacts, so design changes are visually reviewable on the PR. Starts in
   phase 1 with the skeleton's minimal contents (no Docker until DynamoDB Local
   arrives in phase 4) and grows with the app.
2. **deploy** (`workflow_dispatch`, environment input: test | prod): runs
   the full build-and-test against the checked-out code, then deploys that build
   to the selected environment (sbt assembly + frontend build + `cdk deploy`)
   using the environment's OIDC role. No artifact promotion between
   environments — each deploy builds fresh from the selected ref.
3. **e2e-test** (`workflow_dispatch`, environment input): runs the e2e suite
   against the selected *deployed* environment (full suite on test; restricted
   suite on prod — see [10-e2e-tests](10-e2e-tests.md)). Until phase 5
   wires the deployed API to real data, deployed environments only support the
   smoke subset (page loads, `/api/ping` is 200).

Branch testing on AWS = manually run **deploy** on the branch with the test
environment, then **e2e-test** against test.

The deploy workflow runs build-and-test by calling it as a reusable workflow
(`workflow_call`), so the two cannot drift. Deploys to the same environment are
serialised with a `concurrency` group; queued deploys wait rather than cancel a
running CloudFormation deploy.

## Approach

- **OIDC**: one deploy role per environment, trust condition scoped to this repo
  + the specific GHA environment; the workflow's `permissions: id-token: write`
  + `aws-actions/configure-aws-credentials` with the role ARN, setting
  `mask-aws-account-id: true` explicitly so the account id is masked in log
  output from later steps too (e.g. ARNs printed by `cdk deploy`). As a
  belt-and-braces second layer, every workflow that assumes a role also masks
  the account id itself immediately after authenticating:

  ```yaml
  - name: Mask AWS account id
    run: |
      ACCOUNT_ID=$(aws sts get-caller-identity --query 'Account' --output text)
      echo "::add-mask::${ACCOUNT_ID}"
  ```

  The roles need what `cdk deploy` needs (assume the CDK bootstrap roles)
  plus artifact upload.
- **GHA environments**: `test` and `prod` hold only the secret
  `AWS_DEPLOY_ROLE_ARN` (a secret so the account id is masked in the public
  repo's logs) and the variable `AWS_REGION` (documented in
  [docs/bootstrap.md](../docs/bootstrap.md)) — all other stage config is
  discovered from SSM at deploy time
  ([08-infrastructure](08-infrastructure.md)); optional required-reviewer
  protection on `prod` is a one-click knob to add later if wanted.
- **Caching**: coursier/sbt and pnpm caches; Playwright browser cache.
- **Bootstrap order** (manual, once): CDK bootstrap (account is shared — may
  already be done) → CI stack (OIDC + roles) → create the two GHA environments
  in repo settings → first dispatch deploy. Role scoping details in
  [08-infrastructure](08-infrastructure.md).

## Notes

- The deploy workflow deploying test and prod from the same definition keeps the
  two environments honest; the only differences live in stage config.
