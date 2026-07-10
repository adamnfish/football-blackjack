import * as cdk from "aws-cdk-lib";
import { CiStack } from "./ci-stack.js";

/**
 * Builds the whole CDK app. Shared by the cdk CLI entrypoint and the
 * snapshot tests, so the tests synth exactly what deploys, including
 * app-level tags.
 */
export function buildApp(): { app: cdk.App; ciStack: CiStack } {
  const app = new cdk.App();
  cdk.Tags.of(app).add("app", "football-blackjack");

  // Once-per-account CI stack, deployed manually (phase 0).
  // The per-stage app stacks (FootballBlackjack-test/-prod) arrive in phase 1.
  const ciStack = new CiStack(app, "FootballBlackjack-ci");

  return { app, ciStack };
}
