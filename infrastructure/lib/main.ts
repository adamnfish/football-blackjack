import * as cdk from "aws-cdk-lib";
import { CiStack } from "./ci-stack.js";

const app = new cdk.App();
cdk.Tags.of(app).add("app", "football-blackjack");

// Once-per-account CI stack, deployed manually (phase 0).
// The per-stage app stacks (FootballBlackjack-test/-prod) arrive in phase 1.
new CiStack(app, "FootballBlackjack-ci");
