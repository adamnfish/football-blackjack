# Phase 0 runbook — manual setup steps

The one-off manual work that unblocks the walking skeleton
([00-overview](00-overview.md)). Five steps in dependency order — an hour or
two of account/repo admin once the CI stack is written. Run with your own
admin credentials — this is the chicken-and-egg work that creates what the
pipeline authenticates with.

**Open decision needed to execute:** the AWS region for the app stacks. The
stage domain names are decided out-of-band and deliberately never written
into the repo — they live only in SSM (step 1).

## 1. Certificates and SSM parameters

The parent domain's Route53 zone already exists in this account; the stage
domains are subdomains of it, and the app stacks create their alias records —
nothing to register.

- [ ] Request one ACM certificate per stage domain in **us-east-1** (CloudFront
      requires that region), DNS-validated — one click to create the
      validation records since the zone is in the same account
- [ ] Create the three SSM parameters per stage (plain `String`, in the
      app-stack region):
      - `/football-blackjack/{stage}/domain-name`
      - `/football-blackjack/{stage}/hosted-zone-id`
      - `/football-blackjack/{stage}/certificate-arn`

The stacks resolve these at deploy time ([08-infrastructure](08-infrastructure.md)).
Remember `cdk diff` never shows their values: repointing a parameter takes
effect silently on the next deploy.

## 2. CDK bootstrap (verify rather than do)

- [ ] Check for a `CDKToolkit` CloudFormation stack in the target region — the
      account is shared, so it's likely already bootstrapped. If present,
      leave it alone (decided: bootstrap roles stay unmodified —
      [08-infrastructure](08-infrastructure.md))
- [ ] If absent: `cdk bootstrap aws://<account>/<region>` with admin credentials

Only the app-stack region needs bootstrapping: the certs are created manually
(step 1), so there's no cross-region cert support stack and us-east-1 stays
untouched.

## 3. Write and deploy the CI stack

The one phase-0 item that's code: a small CDK stack in `infrastructure/`
containing just the two deploy roles, deployed once manually with admin
credentials and never from CI/CD. The account's existing GitHub OIDC identity
provider is referenced by its deterministic ARN, not provisioned
([08-infrastructure](08-infrastructure.md) has the detail and full scoping
rationale):

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
      environment variables (consumed by `aws-actions/configure-aws-credentials`) —
      the only per-stage config GitHub holds; everything else is discovered
      from SSM (step 1)
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

- [ ] Finally, record what was done as the first operator doc in `docs/`:
      region, deploy role ARNs, the SSM parameter names, where the certs and
      zone live, and any deviations from this runbook. This runbook is the
      plan; the record is the as-built, and it's what future maintenance
      works from. Domain names stay out of the repo — reference the SSM
      parameters, never their values.

## Explicitly deferred to phase 5

- The SSM parameter holding the football-data API key
- The SNS topic email subscription confirmation

Neither needs to exist until the data-service Lambda does.
