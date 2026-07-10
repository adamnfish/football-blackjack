import { App } from "aws-cdk-lib";
import { Template } from "aws-cdk-lib/assertions";
import { describe, expect, it } from "vitest";
import { CiStack } from "../lib/ci-stack.js";

describe("CI stack", () => {
  it("synthesizes the expected CloudFormation template", () => {
    const app = new App();
    const stack = new CiStack(app, "FootballBlackjack-ci");
    expect(Template.fromStack(stack).toJSON()).toMatchSnapshot();
  });
});
