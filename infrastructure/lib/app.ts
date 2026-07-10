import { existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import * as cdk from "aws-cdk-lib";
import { AppStack, Stage } from "./app-stack.js";
import { CiStack, STAGES } from "./ci-stack.js";

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), "..", "..");

const defaultAssetPaths = {
  // sbt 2 build layout; produced by `sbt api/assembly`
  apiJarPath: join(repoRoot, "target", "out", "jvm", "scala-3.8.4", "api", "api.jar"),
  // produced by `pnpm --dir frontend build`
  webrootPath: join(repoRoot, "frontend", "dist"),
};

export interface BuildAppProps {
  apiJarPath?: string;
  webrootPath?: string;
}

/**
 * Builds the whole CDK app. Shared by the cdk CLI entrypoint and the
 * snapshot tests, so the tests synth exactly what deploys, including
 * app-level tags. The snapshot tests pass placeholder asset paths; the
 * CLI uses the real build artifacts.
 */
export function buildApp(props: BuildAppProps = {}): {
  app: cdk.App;
  ciStack: CiStack;
  appStacks: Partial<Record<Stage, AppStack>>;
} {
  const app = new cdk.App();
  cdk.Tags.of(app).add("app", "football-blackjack");

  // Once-per-account CI stack, deployed manually (phase 0)
  const ciStack = new CiStack(app, "FootballBlackjack-ci");

  const apiJarPath = props.apiJarPath ?? defaultAssetPaths.apiJarPath;
  const webrootPath = props.webrootPath ?? defaultAssetPaths.webrootPath;

  const appStacks: Partial<Record<Stage, AppStack>> = {};
  if (existsSync(apiJarPath) && existsSync(webrootPath)) {
    for (const stage of STAGES) {
      const stack = new AppStack(app, `FootballBlackjack-${stage}`, {
        stage,
        apiJarPath,
        webrootPath,
      });
      cdk.Tags.of(stack).add("stage", stage);
      appStacks[stage] = stack;
    }
  } else {
    // eslint-disable-next-line no-console
    console.warn(
      "App stacks skipped: build the artifacts first " +
        "(sbt api/assembly; pnpm --dir frontend build)",
    );
  }

  return { app, ciStack, appStacks };
}
