import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { defineConfig } from "vite";
import elmPlugin from "vite-plugin-elm";

// pnpm wraps the elm package's `bin/elm` with a `node` shim, which breaks
// because that file is the native compiler binary, not JS. Point
// node-elm-compiler straight at the real binary instead.
const require = createRequire(import.meta.url);
const pathToElm = join(dirname(require.resolve("elm/package.json")), "bin", "elm");

export default defineConfig({
  plugins: [elmPlugin({ nodeElmCompilerOptions: { pathToElm } })],
  server: {
    // Same-origin /api, matching production's CloudFront behaviour; the
    // sbt dev server (backend/dev-server) listens on 9090
    proxy: {
      "/api": process.env.API_PROXY_TARGET ?? "http://localhost:9090",
    },
  },
});
