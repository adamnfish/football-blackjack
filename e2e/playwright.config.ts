import { defineConfig, devices } from "@playwright/test";

// Default target is the local app: Vite (started by Playwright below) with
// its /api proxy to the sbt dev server, which must already be running
// (sbt devServer/run). Set BASE_URL to target a deployed environment
// instead; capability flags for what runs where arrive with the features
// that need them.
const baseURL = process.env.BASE_URL ?? "http://localhost:5173";
const localTarget = !process.env.BASE_URL;

export default defineConfig({
  testDir: "./tests",
  outputDir: "./test-results",
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: localTarget
    ? {
        command: "pnpm --dir ../frontend dev --port 5173 --strictPort",
        url: "http://localhost:5173",
        reuseExistingServer: !process.env.CI,
      }
    : undefined,
});
