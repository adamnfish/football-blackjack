import { expect, test } from "@playwright/test";

test("POST /api/ping answers 200", async ({ request }) => {
  const response = await request.post("/api/ping");
  expect(response.status()).toBe(200);
  // "ok" from the phase 1 stub servers; the circe-encoded Ping response once
  // the phase 2 API is behind the route
  expect(["ok", '{"Ping":{}}']).toContain(await response.text());
});
