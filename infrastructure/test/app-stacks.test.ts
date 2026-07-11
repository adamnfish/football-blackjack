import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { Template } from "aws-cdk-lib/assertions";
import { describe, expect, it } from "vitest";
import { STAGES } from "../lib/ci-stack.js";
import { buildApp } from "../lib/app.js";

const fixtures = join(dirname(fileURLToPath(import.meta.url)), "fixtures");

// Asset hashes churn with every artifact build; mask them so snapshots
// only change on real infrastructure changes
const maskAssetHashes = (template: unknown): unknown =>
  JSON.parse(JSON.stringify(template).replace(/[0-9a-f]{64}/g, "ASSET-HASH"));

describe("app stacks", () => {
  const { appStacks } = buildApp({
    apiJarPath: join(fixtures, "api.jar"),
    webrootPath: join(fixtures, "webroot"),
  });

  for (const stage of STAGES) {
    it(`synthesizes the expected ${stage} template`, () => {
      const stack = appStacks[stage];
      expect(stack).toBeDefined();
      expect(
        maskAssetHashes(Template.fromStack(stack!).toJSON()),
      ).toMatchSnapshot();
    });
  }
});
