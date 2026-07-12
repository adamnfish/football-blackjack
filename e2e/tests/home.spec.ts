import { expect, test } from "@playwright/test";

test("the app loads", { tag: "@smoke" }, async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveTitle("Football Blackjack");
  await expect(page.getByText("Hello, world!")).toBeVisible();
  await page.screenshot({ path: "screenshots/home.png", fullPage: true });
});
