# Phase 0 runbook — manual setup steps

The one-off manual work that unblocks the walking skeleton
([00-overview](00-overview.md)). Five steps in dependency order; only step 1
has real lead time, the rest is an hour or two of account/repo admin once the
CI stack is written. Run with your own admin credentials — this is the
chicken-and-egg work that creates what the pipeline authenticates with.

**Open decisions needed to execute:** the domain itself, and the AWS region for
the app stacks.

## 1. Domain and hosted zone

- [ ] Choose and register the domain — the step with lead time (registration
      and any DNS propagation are the slow parts)
- [ ] Create the Route53 hosted zone (automatic if registered through Route53;
      otherwise create the zone and point the registrar's NS records at it)
- [ ] Decide the per-stage names — they feed stage config: e.g. `test.<domain>`
      for test, apex or `www` for prod

Certificates are **not** manual: the app stacks create ACM certs in phase 1
with DNS validation, which is why the zone must exist first. CloudFront
requires its cert in **us-east-1** — see the bootstrap note below.

## 2. CDK bootstrap (verify rather than do)

- [ ] Check for a `CDKToolkit` CloudFormation stack in the target region — the
      account is shared, so it's likely already bootstrapped. If present,
      leave it alone (decided: bootstrap roles stay unmodified —
      [08-infrastructure](08-infrastructure.md))
- [ ] If absent: `cdk bootstrap aws://<account>/<region>` with admin credentials
- [ ] Also check/bootstrap **us-east-1**: if the app stack creates the
      CloudFront cert cross-region (CDK's `certificates` support uses a
      us-east-1 support stack), that region needs bootstrapping too — better
      caught now than mid-deploy in phase 1

## 3. Write and deploy the CI stack

The one phase-0 item that's code: a small CDK stack in `infrastructure/`,
deployed once manually with admin credentials. Contents
([08-infrastructure](08-infrastructure.md) has the full scoping rationale):

- **GitHub OIDC identity provider** for `token.actions.githubusercontent.com`
  - [ ] Check first whether one already exists — an account can only hold one
        provider per URL, and in a shared account another project may have
        created it. If so, the CI stack references it instead of creating it
- **Two deploy roles** (test, prod), each with:
  - Trust policy: federated via the OIDC provider, `aud` =
    `sts.amazonaws.com`, `sub` =
    `repo:adamnfish/football-blackjack:environment:test` (or `:prod`). This is
    the entire security boundary — the environment name must match step 4
    exactly (why we standardized on `test`/`prod`)
  - Permissions: **only** `sts:AssumeRole` on the CDK bootstrap roles
    (`cdk-*-deploy-role`, `cdk-*-file-publishing-role`, `cdk-*-lookup-role`) —
    no direct resource permissions
- [ ] Deploy it and note the two role ARNs from the stack outputs

## 4. GitHub repository configuration

In repo Settings → Environments:

- [ ] Create environments `test` and `prod` — names must match the trust
      policies exactly
- [ ] In each, store that stage's deploy role ARN and the AWS region as
      environment variables (consumed by `aws-actions/configure-aws-credentials`)
- [ ] Optional, later: required-reviewer protection on `prod` — with the OIDC
      trust pinned to the environment this is a real security control, not
      just process; the plan leaves it as a one-click knob to add if wanted

## 5. Verify — the phase 0 "done when"

- [ ] A throwaway `workflow_dispatch` workflow (or the skeleton of the deploy
      workflow) with `permissions: id-token: write`, running
      `configure-aws-credentials` with the role ARN then
      `aws sts get-caller-identity`, once per environment

When both environments return the assumed-role identity, phase 0 is done and
phase 1 is unblocked.

## Explicitly deferred to phase 5

- The SSM parameter holding the football-data API key
- The SNS topic email subscription confirmation

Neither needs to exist until the data-service Lambda does.
