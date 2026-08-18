# ADR-0001: React control plane, Node worker plane, Java authority boundary

Date: 2026-08-11
Status: accepted for incremental implementation

## Context

The current Smoke Test Integration executes React-owned control flow but delegates physical
actions to a Java service that owns one process-global Playwright page. That model cannot safely
isolate multiple Bot Jobs, organizations, REAL datasets, browser storage, cancellation, or
ExcelWrite memory.

Browser React also cannot directly use the Node-only Playwright API or safely write arbitrary
filesystem destinations. Embedding a Node server inside the Java process would couple browser
worker crashes and dependency lifecycle to the database authority.

## Decision

- React remains the execution control plane and owns typed program orchestration and future
  ExcelWrite memory.
- A standalone Node/TypeScript service becomes the Playwright worker plane. It will later own a
  bounded pool and one isolated execution session per run.
- Java remains the user/license/owner/database authority. It will issue short-lived signed grants,
  freeze plans/data, commit authorized runtime values, and atomically write validated artifacts.
- React will communicate with Node using versioned, correlated execution messages after obtaining
  Java authority. Node never connects directly to the application database.
- Migration is whole-run and explicit. A run uses either `TYPESCRIPT_PLAYWRIGHT_V2` or
  `LEGACY_JAVA`; it never falls back between engines after side effects begin.

## P0/P1 boundary

This package currently verifies grants and reserves identities only. It is not connected to React
or Java, has no Playwright dependency, launches no browser, and writes no database or file data.

## Consequences

- The Node service has an independent package, process, health, version, capacity, deployment, and
  rollback lifecycle.
- Java grant issuance and secret provisioning must be implemented before the service can become
  ready for application execution.
- Cross-language contract compatibility requires fixture verification before the first Java-issued
  grant is accepted.
- More processes cost more memory, but failures and state are isolated per execution rather than
  contaminating the global Java page.
