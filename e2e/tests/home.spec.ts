import { expect, test } from "@playwright/test";

test("the app loads", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveTitle("Football Blackjack");
  await expect(page.getByTestId("create-player-name")).toBeVisible();
  await expect(page.getByTestId("create-submit")).toBeVisible();
  await page.screenshot({ path: "screenshots/home.png", fullPage: true });
});
