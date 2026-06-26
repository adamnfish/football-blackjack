import * as cdk from "aws-cdk-lib";
import { Construct } from "constructs";

class FootballBlackjackStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // Infrastructure resources will be defined here
  }
}

const app = new cdk.App();
new FootballBlackjackStack(app, "FootballBlackjackStack");

