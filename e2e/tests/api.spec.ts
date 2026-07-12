import { expect, test } from "@playwright/test";

test("POST /api/ping answers 200 with a Ping response", async ({ request }) => {
  const response = await request.post("/api/ping");
  expect(response.status()).toBe(200);
  expect(await response.json()).toEqual({ Ping: {} });
});
