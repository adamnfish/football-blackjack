import { Template } from "aws-cdk-lib/assertions";
import { describe, expect, it } from "vitest";
import { buildApp } from "../lib/app.js";

describe("CI stack", () => {
  it("synthesizes the expected CloudFormation template", () => {
    const { ciStack } = buildApp({ warnOnMissingAssets: false });
    expect(Template.fromStack(ciStack).toJSON()).toMatchSnapshot();
  });
});
