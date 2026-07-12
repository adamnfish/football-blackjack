import { BrowserContext, Page, expect, test } from "@playwright/test";

// Create/join/selection flows (plan/07-frontend.md), specified per
// plan/10-e2e-tests.md with per-step screenshots. The full flow needs the
// phase 2 dev server (API dispatch + seeded competition stats); against the
// phase 1 stub - which answers "ok" to everything - it is skipped.

const STORAGE_KEY = "football-blackjack.player-keys";

const readPlayerKeys = (page: Page) =>
  page.evaluate(
    (key) => JSON.parse(window.localStorage.getItem(key) ?? "{}"),
    STORAGE_KEY,
  );

test("a personal link's key is stored and stripped from the address bar", async ({
  page,
}) => {
  await page.goto("/game/test-game?key=secret-key");
  await expect(page).toHaveURL("/game/test-game");
  expect(await readPlayerKeys(page)).toEqual({ "test-game": "secret-key" });
  // No such game (real server) / undecodable stub response: either way the
  // page reports the load failure rather than crashing
  await expect(page.getByTestId("game-load-error")).toBeVisible();
  await page.screenshot({
    path: "screenshots/personal-link-stripped.png",
    fullPage: true,
  });
});

test("create, creator selection, second player joins, selection conflict", async ({
  browser,
  request,
}) => {
  const probe = await request.post("/api/create-game", { data: {} });
  test.skip(
    (await probe.text()) === "ok",
    "needs the phase 2 dev server (stub answered)",
  );

  const shot = (page: Page, name: string) =>
    page.screenshot({ path: `screenshots/flows-${name}.png`, fullPage: true });

  let alice: BrowserContext | undefined;
  let bob: BrowserContext | undefined;
  try {
    // Alice creates a game
    alice = await browser.newContext();
    const alicePage = await alice.newPage();
    await alicePage.goto("/");
    await shot(alicePage, "01-create-form");
    await alicePage.getByTestId("create-player-name").fill("Alice");
    await alicePage.getByTestId("create-game-name").fill("Office sweepstake");
    await shot(alicePage, "02-create-filled");
    await alicePage.getByTestId("create-submit").click();

    // ... and lands straight on the selection screen, with an empty pick
    await expect(alicePage).toHaveURL(/\/game\/[^/?]+$/);
    await expect(alicePage.getByTestId("selection-count")).toContainText(
      "Picked 0 of 4",
    );
    const gameUrl = alicePage.url();
    await shot(alicePage, "03-creator-selection");

    // Alice picks her 4 teams
    const aliceCards = alicePage.getByTestId("team-card");
    const alicePicks: string[] = [];
    for (let i = 0; i < 4; i++) {
      const card = aliceCards.nth(i);
      alicePicks.push((await card.getAttribute("data-team-id")) ?? "");
      await card.click();
    }
    await expect(alicePage.getByTestId("selection-count")).toContainText(
      "Picked 4 of 4",
    );
    await shot(alicePage, "04-creator-selection-picked");
    await alicePage.getByTestId("selection-submit").click();

    // ... and sees the game view with her selection
    await expect(alicePage.getByTestId("game-view")).toBeVisible();
    await expect(alicePage.getByTestId("player-row")).toContainText("Alice");
    await shot(alicePage, "05-creator-game-view");

    // Bob follows the shared link in his own browser
    bob = await browser.newContext();
    const bobPage = await bob.newPage();
    await bobPage.goto(gameUrl);
    await shot(bobPage, "06-join-form");
    await bobPage.getByTestId("join-player-name").fill("Bob");
    await bobPage.getByTestId("join-continue").click();

    // Bob picks exactly Alice's teams: the combination is taken
    await expect(bobPage.getByTestId("selection-count")).toBeVisible();
    for (const teamId of alicePicks) {
      await bobPage.locator(`[data-team-id="${teamId}"]`).click();
    }
    await bobPage.getByTestId("selection-submit").click();
    await expect(bobPage.getByTestId("selection-error")).toContainText(
      "already taken",
    );
    await shot(bobPage, "07-selection-conflict");

    // Changing one team resolves the conflict
    const cardCount = await bobPage.getByTestId("team-card").count();
    let replacement: string | undefined;
    for (let i = 0; i < cardCount; i++) {
      const teamId = await bobPage
        .getByTestId("team-card")
        .nth(i)
        .getAttribute("data-team-id");
      if (teamId && !alicePicks.includes(teamId)) {
        replacement = teamId;
        break;
      }
    }
    expect(replacement).toBeDefined();
    await bobPage.locator(`[data-team-id="${alicePicks[3]}"]`).click();
    await bobPage.locator(`[data-team-id="${replacement}"]`).click();
    await bobPage.getByTestId("selection-submit").click();

    // Bob is in: both players and their selections are visible
    await expect(bobPage.getByTestId("game-view")).toBeVisible();
    await expect(bobPage.getByTestId("player-row")).toHaveCount(2);
    expect(await readPlayerKeys(bobPage)).toHaveProperty(
      gameUrl.split("/").pop() as string,
    );
    await shot(bobPage, "08-joined-game-view");

    // Alice sees Bob after a refresh
    await alicePage.reload();
    await expect(alicePage.getByTestId("player-row")).toHaveCount(2);
    await shot(alicePage, "09-creator-sees-joiner");
  } finally {
    await alice?.close();
    await bob?.close();
  }
});
