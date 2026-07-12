# Dev server

The dev server runs the API locally for frontend development and the e2e suite. It lives
in [backend/dev-server](../backend/dev-server). It is currently a walking-skeleton stub that answers `200 "ok"` to any
`POST /api/{operation}`, matching the stub api Lambda.

## Prerequisites

- JDK and [sbt](https://www.scala-sbt.org/)
- [pnpm](https://pnpm.io/) (`pnpm install` from the repository root fetches all workspace dependencies)

## Running

Start the dev server:

```
sbt devServer/run
```

It listens on http://localhost:9090 and prints the address when it is ready.

Start the frontend:

```
pnpm --dir frontend dev
```

Vite serves the frontend on http://localhost:5173 and proxies `/api` requests to the dev server. This matches
production, where CloudFront routes `/api` to the API Lambda. Open http://localhost:5173 in a browser.

## e2e tests

```
pnpm --dir e2e test
```

Playwright starts Vite itself, but expects the dev server to already be running. Set `BASE_URL` to run the suite
against a deployed environment instead.

## Populating data

Not yet available. The dev server will grow a `/dev` panel for triggering competition job runs and a simulated clock in
later phases. This section will describe how to load competition data when that lands.
