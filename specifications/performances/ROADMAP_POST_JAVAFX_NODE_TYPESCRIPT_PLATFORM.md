# Post-JavaFX Node/TypeScript Platform Roadmap

Date: 2026-07-11

## Objective

After the active React instruction migration is complete, reduce Java to a temporary compatibility boundary and move application orchestration, authoritative business rules, and new APIs to server-side TypeScript. Retire JavaFX and JCEF after the browser-hosted React application has operational parity.

This roadmap does not move database credentials, SQL, validation, or authoritative automation rules into browser React code. Those responsibilities belong in a Node/TypeScript server.

## Target Architecture

```text
React/TypeScript browser
        |
        | HTTPS + authenticated WebSocket
        v
Node/TypeScript application server
  - API and WebSocket gateway
  - command and graph rules
  - organization/job/instruction services
  - transaction boundaries and repositories
  - automation orchestration
        |
        +---- SQLite repository
        |
        +---- temporary Access compatibility adapter
        |
        +---- temporary Java compatibility service
```

The final client should open the React application in a normal browser. It must not require JavaFX, JCEF, or a graphical environment on the server.

## Non-Negotiable Rules

- Finish `MIGRATION_TRACKER_2026-07-11.md` before starting this roadmap.
- Keep rules authoritative on the server; React renders capabilities and submits typed intents.
- Do not duplicate SQL or business rules across Java and Node for longer than a controlled transition.
- Every mutation uses transactions, ownership checks, revision checks, request IDs, and authoritative responses.
- Preserve Access support until each affected client has a verified migration or compatibility route.
- A fixed internal IP is routing, not security. Require authentication, authorization, TLS where possible, origin checks, and audit logs.
- Replace one bounded capability at a time with contract and database parity tests.

## Phase 0 - Baseline and Freeze

- [ ] Complete the active React/Java migration tracker and its end-to-end tests.
- [ ] Record supported Windows and Linux deployment modes, database type, Java version, and client-specific dependencies.
- [ ] Freeze new JavaFX/JCEF features; allow only fixes needed for migration.
- [ ] Capture golden workflows for organization `2`, bot job `19`, component grid, Memory List, commands, variables, split, move, clone, and deletion.
- [ ] Define measurable exit criteria for JavaFX, JCEF, Access, and the Java backend.

## Phase 1 - Database and Query Audit

- [ ] Inventory every SQL statement, generated query, schema mutation, transaction, and database-specific branch.
- [ ] Map each query to its caller, table ownership, read/write behavior, transaction boundary, and UI workflow.
- [ ] Identify duplicate queries and direct database access outside repository/facade boundaries.
- [ ] Compare Access and SQLite schemas, constraints, defaults, null handling, generated IDs, date types, and boolean encoding.
- [ ] Add schema-version tables and repeatable migrations for SQLite.
- [ ] Create characterization fixtures that run equivalent repository behavior against sanitized Access and SQLite data.
- [ ] Mark unused tables, columns, queries, and commands as candidates; remove only after runtime and client-data evidence.

### Deliverables

- `DATABASE_QUERY_INVENTORY.md`
- `ACCESS_SQLITE_PARITY_MATRIX.md`
- versioned SQLite schema and migration scripts
- a list of removable database code with evidence and rollback instructions

## Phase 2 - Typed Contracts

- [ ] Define versioned HTTP and WebSocket contracts with TypeScript schemas.
- [ ] Generate or share DTO types for requests, responses, errors, capabilities, revisions, and mutation results.
- [ ] Validate every inbound payload at runtime; TypeScript compile-time types alone are insufficient.
- [ ] Replace operation strings at transport boundaries with typed command fields while keeping one canonical server encoder during transition.
- [ ] Add contract tests that replay current React messages against Java and Node adapters.
- [ ] Define compatibility and deprecation rules for old desktop clients.

## Phase 3 - Node/TypeScript Server Foundation

- [ ] Create a separate Node/TypeScript server package with pinned runtime and dependency versions.
- [ ] Use a small HTTP framework, runtime schema validation, structured logging, and an authenticated WebSocket implementation.
- [ ] Add health, readiness, version, database-status, and migration-status endpoints.
- [ ] Implement graceful shutdown, connection limits, request timeouts, payload limits, and correlation IDs.
- [ ] Bind to a configurable interface; document the internal fixed IP and DNS name without hard-coding either in application logic.
- [ ] Provide systemd and container deployment options; Kubernetes is optional and should be introduced only when operations require it.
- [ ] Add backup, restore, log rotation, metrics, and alerting procedures.

## Phase 4 - Repository Migration

- [ ] Introduce server-side TypeScript repository interfaces for organizations, environments, jobs, blocks, instructions, variables, and references.
- [ ] Implement SQLite repositories with parameterized queries and explicit transactions.
- [ ] Route database access through services; prohibit SQL in React components and WebSocket handlers.
- [ ] Port read-only workflows first and compare Node output with Java output using the same database snapshot.
- [ ] Port mutations only after read parity, then verify affected-row counts and rollback behavior.
- [ ] Add concurrency tests for revisions, duplicate request IDs, stale clients, and simultaneous edits.

## Phase 5 - Access Strategy

- [ ] Identify clients that still require Access and why they cannot immediately use SQLite.
- [ ] Choose one transitional adapter based on tested feasibility: isolated Java service, Windows sidecar, controlled export/import, or ODBC bridge.
- [ ] Do not depend on an unverified pure Node Access driver for production.
- [ ] Build an Access-to-SQLite migration tool with dry-run, backup, checksums, row counts, relationship validation, and rollback.
- [ ] Run client-by-client migration rehearsals using copied databases.
- [ ] Make SQLite the default for new installations.
- [ ] Retire Access code only after all clients sign off and archived databases remain recoverable.

## Phase 6 - Authoritative Logic Migration

- [ ] Port command metadata and command encoding to server-side TypeScript with golden fixtures against Java behavior.
- [ ] Port instruction graph parsing, IF/ELSEIF/ELSE/ENDIF rules, loop ownership, capabilities, previews, and impact analysis.
- [ ] Port variable compatibility, reference resolution, split, move, delete, Memory List, and block normalization.
- [ ] Port organization, environment, configuration, job creation, clone, and dashboard services.
- [ ] Keep React limited to presentation, local interaction state, typed drafts, and displaying server decisions.
- [ ] Run Java and Node in shadow comparison mode before switching each capability.
- [ ] Remove the corresponding Java implementation after parity, observability, rollback, and client compatibility are proven.

## Phase 7 - Automation Runtime Decision

- [ ] Inventory Selenium, OCR, native desktop, filesystem, Excel/PDF/CSV, browser-profile, and device integrations.
- [ ] Classify each runtime feature as portable to Node, better served by Playwright, or requiring a temporary Java/native worker.
- [ ] Keep automation workers headless and API-driven; do not place execution logic in React.
- [ ] Prototype representative Banca Stato workflows before selecting a full Selenium-to-Playwright migration.
- [ ] Define worker isolation, job cancellation, retries, artifact storage, secrets, browser lifecycle, and concurrency limits.
- [ ] Migrate incrementally; retain a narrow Java worker only for capabilities without safe Node parity.

## Phase 8 - Remove JCEF and JavaFX

- [ ] Make the browser URL the primary application entry point.
- [ ] Replace desktop-only open, maximize, navigation, and lifecycle calls with browser/server equivalents.
- [ ] Replace local Java dialogs with React confirmations and structured server errors.
- [ ] Provide authentication and session recovery without a desktop container.
- [ ] Verify all pages at desktop resolutions previously controlled by JCEF.
- [ ] Remove JCEF bridge messages after HTTP/WebSocket equivalents are deployed.
- [ ] Remove JavaFX panes, launchers, resources, and dependencies only after an audited zero-reference check.
- [ ] Confirm the server runs headlessly on Linux without a display server.

## Phase 9 - Cutover and Java Reduction

- [ ] Add per-capability feature flags and a documented rollback path.
- [ ] Run shadow traffic and compare payloads, decisions, SQL effects, and automation artifacts.
- [ ] Complete SQLite backup/restore and disaster-recovery exercises.
- [ ] Cut over pilot clients, then Access clients, then the remaining installations.
- [ ] Remove migrated Java APIs and database methods after a defined stability period.
- [ ] Reduce Java to explicitly documented compatibility workers, or remove it entirely when the final dependency is retired.
- [ ] Archive migration tools, schema mappings, and release evidence.

## Required Test Gates

- [ ] Contract tests for every HTTP and WebSocket operation.
- [ ] Repository parity tests for SQLite and the temporary Access route.
- [ ] Transaction rollback and concurrent-edit tests for every mutation family.
- [ ] Golden command-codec and graph-rule fixtures shared between old and new implementations.
- [ ] Playwright end-to-end coverage for organization `2`, bot job `19`, and component sessions.
- [ ] Security tests for authentication, authorization, origin validation, injection, payload limits, and replay/idempotency.
- [ ] Backup/restore and client database migration verification.
- [ ] Headless Linux deployment and restart tests.

## Completion Criteria

- React runs in a normal browser without JavaFX or JCEF.
- Node/TypeScript owns APIs, WebSockets, authoritative rules, transactions, and repositories.
- Browser code contains no credentials, SQL, or authoritative business-rule duplication.
- SQLite is the default supported database; remaining Access support is explicit, isolated, and scheduled for retirement.
- Java contains only documented compatibility workers, or no Java runtime is required.
- Every removed Java/database path has parity evidence, production observability, and a tested rollback plan.
