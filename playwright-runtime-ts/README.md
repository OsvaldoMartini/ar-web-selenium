# AR Web Playwright Runtime V2

This package is the isolated TypeScript worker-plane boundary for future AR Web executions. The
current checkpoint includes an internal Playwright worker/session pool, but no HTTP or application
route can admit work into it yet.

Implemented now:

- versioned execution/grant contracts;
- loopback-only HTTP health and readiness endpoints;
- bounded HS256 execution-grant verification;
- a deterministic compatibility fixture shared with the Java grant signer;
- a capped, expiring, in-memory run reservation registry;
- one opaque, capability-bound run access token returned only on exact reservation admission/replay;
- bounded renewable idle leases for admitted runs, independent of the short-lived admission grant;
- exact-token replay, constant-time authority checks, and conflicting-run refusal;
- a bounded worker pool with global, organization, and Bot Job admission limits;
- one dedicated Chromium process, BrowserContext, and Page per admitted run;
- bounded navigation readiness, refresh, stop, cleanup, and asynchronous crash containment;
- an internal authored/registry/canonical/alias locator ladder for CLICK, INPUT, and OUTPUT;
- exact page revalidation, tag/frame/action checks, ambiguity refusal, and at-most-once actions;
- safe structured logs that never include grants or request bodies.

Not implemented yet:

- an authorized Java/WebSocket adapter that may invoke the grant signer;
- production secret provisioning and rotation;
- React or Java routing to this service;
- HTTP start/refresh/stop routes connected to the internal worker pool;
- run-token heartbeat/action routes connected to the renewable lease authority;
- authorized plan/registry preparation and HTTP routing for physical actions;
- coordinates, Shadow DOM targets, and command families beyond CLICK, INPUT, and OUTPUT;
- production browser executable provisioning and a live browser acceptance run;
- database, runtime-variable, CSV, XLSX, or filesystem writes.

`playwright-core` is pinned for the worker implementation. It does not download or bundle a browser;
deployment must provide an explicitly configured compatible Chromium executable or channel.

## Configuration

The process listens on `127.0.0.1` only. It is live but not ready until a grant secret is present.

| Environment variable | Default | Rule |
| --- | --- | --- |
| `ARWEB_EXECUTION_V2_PORT` | `60110` | Integer `1..65535` |
| `ARWEB_EXECUTION_V2_GRANT_SECRET_BASE64URL` | none | Canonical base64url, at least 32 decoded bytes |
| `ARWEB_EXECUTION_V2_GRANT_KID` | `v1` | `1..64` safe identifier characters |
| `ARWEB_EXECUTION_V2_MAX_RESERVED_RUNS` | `32` | Integer `1..256` |
| `ARWEB_EXECUTION_V2_MAX_GRANT_SECONDS` | `120` | Integer `10..300` |
| `ARWEB_EXECUTION_V2_CLOCK_SKEW_SECONDS` | `5` | Integer `0..30` |

Endpoints:

- `GET /health/live`
- `GET /health/ready`
- `GET /version`
- `POST /v2/runs/reserve` with a signed Bearer grant
- `GET /v2/runs/{runId}` with the same run grant
- `DELETE /v2/runs/{runId}` with the same run grant

Run `npm ci`, then `npm run build`, `npm run typecheck`, or `npm test`.
