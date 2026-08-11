# Execution V2 P0 Threat Model

## Trust boundaries

- React is an orchestration client, not an authority for owner IDs, plan revisions, runtime limits,
  browser endpoints, or file destinations.
- Java remains the database, license, user, Bot Job, plan, and persistence authority.
- This Node runtime accepts only short-lived Java-signed grants and never reads the application
  database.
- Each future run owns a distinct worker/session/page boundary. This checkpoint reserves identity
  only and launches no browser.

## Grant contract

- Compact three-part base64url envelope using `HS256`, fixed type `ARWEB-EXECUTION-GRANT`, a
  configured key ID, and constant-time signature comparison.
- Exact issuer `arweb-java-gateway`, audience `arweb-playwright-runtime`, contract version 1, and
  runtime `TYPESCRIPT_PLAYWRIGHT_V2`.
- UUID request/grant/run identities, positive safe owner IDs and workspace epoch, immutable
  SHA-256 graph/plan revisions, explicit REAL/SYNTHETIC mode, and an allowlisted capability set.
- Maximum lifetime is bounded by runtime configuration. Expired, premature, excessively long,
  malformed, unknown-field, wrong-key, and wrong-owner grants fail closed.
- The secret is supplied only through process configuration, must decode to at least 256 bits, and
  is never logged or returned.

## Replay and capacity

- Exact replay of the same grant/run reservation is idempotent.
- A different grant attempting to claim an existing run ID is refused.
- Grant IDs cannot reserve different runs.
- Expired reservations are removed before admission and by a bounded periodic sweep.
- The registry has a configured hard capacity and never evicts a live run to admit another one.

## Network and data exposure

- P0/P1 binds to loopback only and exposes no CORS policy, WebSocket, browser, database, or file
  endpoint.
- Request bodies are capped and currently unused. Tokens and request bodies never enter logs.
- Responses expose safe IDs, state, counts, timestamps, and revision hashes only; no credentials,
  locator values, URLs, banking text, input values, or secrets.

## Deferred risks and gates

- HS256 requires secure shared-secret provisioning and rotation. A later asymmetric algorithm may
  reduce secret-distribution risk; changing algorithms requires a new contract version.
- The Java signer exists, but the exact user/license/workspace authorization adapter and secret
  provisioning are not implemented, so this service must not be connected to production execution.
- Browser worker isolation, process limits, page readiness, at-most-once physical actions,
  reconnect leases, crash cleanup, and VPN/proxy separation are P2+ gates.
- Artifact upload, controlled paths, checksums, atomic moves, and ExcelWrite lifecycle are P5/P6
  gates.
