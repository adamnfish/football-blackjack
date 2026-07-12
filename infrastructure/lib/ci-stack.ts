import * as cdk from "aws-cdk-lib";
import * as iam from "aws-cdk-lib/aws-iam";
import { Construct } from "constructs";

const GITHUB_REPO = "adamnfish/football-blackjack";
// Default qualifier baked into the account's `cdk bootstrap` role names
const BOOTSTRAP_QUALIFIER = "hnb659fds";
export const STAGES = ["test", "prod"] as const;

/**
 * Once-per-account CI stack: the roles for the GitHub Actions to deploy to each environment.
 *
 * Deployed manually with admin credentials. Assumes a shared GitHub OIDC identity provider already exists.
 * It is referenced by its deterministic ARN.
 */
export class CiStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const githubOidcProviderArn = `arn:aws:iam::${this.account}:oidc-provider/token.actions.githubusercontent.com`;

    for (const stage of STAGES) {
      const role = new iam.Role(this, `DeployRole-${stage}`, {
        roleName: `football-blackjack-deploy-${stage}`,
        description: `GitHub Actions deploy role for the ${stage} environment`,
        // The trust policy is the security boundary: only workflow runs in
        // this repo's `${stage}` GitHub environment can assume the role
        assumedBy: new iam.WebIdentityPrincipal(githubOidcProviderArn, {
          StringEquals: {
            "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
            "token.actions.githubusercontent.com:sub": `repo:${GITHUB_REPO}:environment:${stage}`,
          },
        }),
      });

      // The only permission granted is to assume the CDK bootstrap roles that `cdk deploy` uses
      role.addToPolicy(
        new iam.PolicyStatement({
          actions: ["sts:AssumeRole"],
          resources: ["deploy", "file-publishing", "lookup"].map(
            (name) =>
              `arn:aws:iam::${this.account}:role/cdk-${BOOTSTRAP_QUALIFIER}-${name}-role-${this.account}-*`,
          ),
        }),
      );

      // The e2e-test workflow reads the stage's domain name from SSM to
      // discover the deployed target URL
      role.addToPolicy(
        new iam.PolicyStatement({
          actions: ["ssm:GetParameter"],
          resources: [
            `arn:aws:ssm:*:${this.account}:parameter/football-blackjack/${stage}/domain-name`,
          ],
        }),
      );

      cdk.Tags.of(role).add("stage", stage);

      new cdk.CfnOutput(this, `DeployRoleArn-${stage}`, {
        value: role.roleArn,
        description: `Deploy role ARN for the ${stage} GitHub environment`,
      });
    }
  }
}
