import { Elm } from "./Main.elm";

// Personal player keys, stored per game: { [gameId]: playerKey }. The full
// map goes in via flags; writes come back out through the storePlayerKey port.
const STORAGE_KEY = "football-blackjack.player-keys";

const readPlayerKeys = () => {
  try {
    return JSON.parse(window.localStorage.getItem(STORAGE_KEY)) ?? {};
  } catch {
    return {};
  }
};

const app = Elm.Main.init({
  node: document.getElementById("app"),
  flags: { playerKeys: readPlayerKeys() },
});

app.ports.storePlayerKey.subscribe(({ gameId, playerKey }) => {
  const playerKeys = readPlayerKeys();
  playerKeys[gameId] = playerKey;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(playerKeys));
  } catch {
    // localStorage unavailable; the key still lives in the app's model
  }
});
