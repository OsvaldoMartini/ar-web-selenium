# Variable AUTO / Resolve batch performance — 2026-08-05

## Outcome

- [x] TASK — Trace AUTO and Resolve from React through WebSocket and SQLite.
- [x] TASK — Replace production AUTO variable work with one compact WebSocket request.
- [x] TASK — Route Resolve variable work through the same atomic batch after parent resolution.
- [x] TASK — Batch SQLite definition inserts and slot inserts with prepared statements.
- [x] TASK — Batch graph instruction and variable-owner updates.
- [x] TASK — Publish one authoritative update after the committed variable transaction.
- [x] TASK — Preserve Same Vars / Distinct naming and fill only missing slots.
- [x] TASK — React production build passed and resources were copied.
- [ ] TASK — User runs Maven/package and Java tests.
- [ ] TASK — Live timing and database verification on a large Bot Job.

## Bottleneck breakdown

Before, React orchestrated variable creation and persistence in phases. A large
scope could produce N create requests, relationship mutations, RIGHT-operand
updates, and repeated refreshes. Each request repeated authorization, parsing,
transaction setup, graph loading, version checks, commit, and publication.

The backend already contained an atomic auto-resolve transaction, but React did
not use it. It also inserted definitions and slots one row at a time and used
outdated oldest-variable naming rules.

## Production path now

```text
AUTO click
  -> one variablesWorkspace.variables.autoResolve v2 request
  -> one serialized SQLite transaction
  -> one graph read and deterministic plan
  -> one MAX(id) allocation
  -> one prepared definition batch
  -> one prepared slot batch
  -> one graph-version compare/increment
  -> one verification read
  -> one commit
  -> one response + one authoritative publication
```

Resolve performs one parent mutation only when required, then invokes the same
variable batch. Existing connections are never overwritten.

Release uses the corresponding bounded path:

```text
Release Connections confirmation
  -> one compact parent-clear mutation when parent links exist
  -> one variablesWorkspace.variables.autoResolve v2 RELEASE request
  -> one serialized SQLite transaction
  -> one prepared-statement batch deleting every scoped variable slot
  -> one graph-version compare/increment and verification read
  -> one commit, response, and authoritative publication
```

The former LEFT, RIGHT, and regular-command instruction-by-instruction release
loop is no longer executed. Variable definitions remain intact; only scoped
`instruction_variable_slot` connections are removed.

## Why no thread pool

SQLite serializes writers. Parallel write tasks add scheduling, connections,
lock contention, busy retries, and nondeterministic ordering without increasing
write throughput. One short transaction with prepared batching is faster and
safer. Read-only snapshot construction can be considered for a worker pool only
after profiling proves it dominates latency.

## Scalability recommendations

- Measure p50/p95/p99 request duration, planned slot count, batch size, database
  time, verification time, and publication time.
- Keep batches owner-scoped and serialized per Bot Job.
- Cap instruction IDs per request and reject oversized payloads explicitly.
- If the database changes from SQLite to PostgreSQL, retain the transaction
  contract and benchmark multi-row INSERT/UPSERT or COPY separately.
- Remove the dormant legacy React phase driver after live acceptance of v2.
