# Bootstrap

This file records the one-off account setup that CI/CD depends on. The plan for these steps is in [plan/phase-0-runbook.md](../plan/phase-0-runbook.md). Update this file when any of these resources change.

## CI stack

The `FootballBlackjack-ci` CloudFormation stack contains the two GitHub Actions deploy roles. It is defined in `infrastructure/lib/ci-stack.ts`.

Deploy it manually with admin credentials:

```
cd infrastructure
pnpm install
pnpm exec cdk deploy FootballBlackjack-ci
```

Do not deploy this stack from CI/CD. The deploy roles should not manage the stack that defines them.

Each role trusts the GitHub OIDC identity provider in this account. The trust policy only accepts workflow runs from one GitHub environment of this repository. The only permission held by each role is to assume the CDK bootstrap roles.

The GitHub OIDC identity provider is shared account infrastructure. It was created by another project. If it is deleted the deploy roles stop authenticating. The fix is to add the provider to the CI stack.

## Deploy roles

| GitHub environment | Role name |
|---|---|
| test | `football-blackjack-deploy-test` |
| prod | `football-blackjack-deploy-prod` |

The role ARNs are stack outputs. Each GitHub environment stores its role ARN and the AWS region as environment variables. GitHub holds no other per-stage configuration.

## Stage configuration in SSM

Each stage has three SSM parameters in the app stack region. The app stacks resolve them at deploy time.

- `/football-blackjack/{stage}/domain-name`
- `/football-blackjack/{stage}/hosted-zone-id`
- `/football-blackjack/{stage}/certificate-arn`

The hosted zone ID is the ID of the parent zone in this account, from the Route53 console. It is never the fixed CloudFront alias zone ID, which is hardcoded in the app stacks.

Create the parameters with the AWS CLI. Set the variables, then run the three commands. Run the whole block once per stage.

```sh
PROFILE=admin-profile-name
REGION=app-stack-region
STAGE=test
DOMAIN_NAME=stage-domain-name
HOSTED_ZONE_ID=route53-parent-zone-id
CERTIFICATE_ARN=acm-cert-arn-in-us-east-1

aws ssm put-parameter --profile "$PROFILE" --region "$REGION" \
  --name "/football-blackjack/$STAGE/domain-name" \
  --value "$DOMAIN_NAME" --type String \
  --tags Key=app,Value=football-blackjack "Key=stage,Value=$STAGE"

aws ssm put-parameter --profile "$PROFILE" --region "$REGION" \
  --name "/football-blackjack/$STAGE/hosted-zone-id" \
  --value "$HOSTED_ZONE_ID" --type String \
  --tags Key=app,Value=football-blackjack "Key=stage,Value=$STAGE"

aws ssm put-parameter --profile "$PROFILE" --region "$REGION" \
  --name "/football-blackjack/$STAGE/certificate-arn" \
  --value "$CERTIFICATE_ARN" --type String \
  --tags Key=app,Value=football-blackjack "Key=stage,Value=$STAGE"
```

`put-parameter` rejects `--tags` when the parameter already exists. To change an existing value, pass `--overwrite` and omit `--tags`. Existing tags survive an overwrite.

Domain names must not be written into this repository. Reference the parameter names, never their values. `cdk diff` does not show changes to parameter values. A repointed parameter takes effect on the next deploy.

## Certificates

Each stage has one ACM certificate in us-east-1. CloudFront requires that region. Certificates are created manually with DNS validation. Their ARNs are stored in the SSM parameters above.

## As-built record

Fill this in as the phase 0 steps complete.

| Item | Value |
|---|---|
| AWS account | to be recorded |
| App stack region | to be recorded |
| CDK bootstrap qualifier | `hnb659fds` |
| Deviations from the runbook | none |
