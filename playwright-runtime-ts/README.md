# AR Web Playwright Runtime V2

This package is the isolated TypeScript worker-plane boundary for future AR Web executions. The
current checkpoint contains no Playwright dependency and cannot launch or control a browser.

Implemented now:

- versioned execution/grant contracts;
- loopback-only HTTP health and readiness endpoints;
- bounded HS256 execution-grant verification;
- a capped, expiring, in-memory run reservation registry;
- exact-token replay and conflicting-run refusal;
- safe structured logs that never include grants or request bodies.

Not implemented yet:

- Java grant issuance;
- React or Java routing to this service;
- Playwright workers, browsers, contexts, pages, or actions;
- database, runtime-variable, CSV, XLSX, or filesystem writes.

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
