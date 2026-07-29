# P0 Variable Graph Baseline

Date: 2026-07-29

Status: automated baseline captured; manual runtime acceptance remains pending

Canonical roadmap: `ROADMAP_VARIABLE_CENTRIC_INSTRUCTION_GRAPH_2026_07_29.md`

## 1. Safety boundary

P0 changes documentation, a SELECT-only audit utility, and synthetic test fixtures only. It does
not change application behavior, database schema, production rows, drag rules, delete rules,
Memory List behavior, or execution behavior.

The known Banca Stato database was never opened by the audit:

1. no AR Web Java process was running;
2. a byte-for-byte SQLite copy was created under ignored `target/p0-audit`;
3. source and copy SHA-256 hashes were required to match;
4. the audit opened only the copy;
5. every SQL statement was a metadata query or `SELECT`;
6. the transaction ended with `ROLLBACK`.

SQLite JDBC reported `Connection.isReadOnly() = false`; therefore the safety guarantee is the
verified disposable copy plus the SELECT-only statement set and rollback, not a driver-enforced
read-only flag.

The copy is a local safety artifact and is not committed:

| Item | Value |
|---|---|
| File | `target/p0-audit/database-p0-2026-07-29.db` |
| Bytes | `3,792,896` |
| SHA-256 | `fdef38ccfd3093127ae685c4be95b776c645eb55ab7ec294195fe83b217b66e3` |
| Source/copy parity | Exact |

Do not run a future destructive migration against this production-shaped source first. Make a new
copy immediately before the data phase because the live database may have changed after this
baseline.

## 2. Source and artifact baseline

### Repositories

| Repository | Branch | Baseline commit | Upstream state |
|---|---|---|---|
| Java backend | `refactor/perform-actions-decomposition` | `9c47174d0b93361cbe6e2173b0c191ed3b6f4b4d` | Matched upstream |
| React frontend | `VERSION-4.6` | `84aab63b075e1b7ee3e79e0a71755e1bc82d4fa2` | No P0 source change |

### Loose/classpath React bundle

The frontend `build` directory and backend `src/main/resources/build` contain the same 45 relative
files and have the same deterministic tree digest:

`ee01979859fea82e6a533c30cdfda6624c550b5e5426839e72726ee1cf2cc6bd`

| Asset | SHA-256 |
|---|---|
| `asset-manifest.json` | `323bc3effecdc5399e84c7cbb520ec4339349fe71461fcc28d67a46346c1a29f` |
| `index.html` | `893cee9271017cc9f6c57dfbfcd5e91f691016a218a0baa11b2d9fb3fe069962` |
| `main.a44bc6c7.js` | `fba144d4f73344dfa8a0d42427d40cd2d5403a01d6365b929143e987afd56cad` |
| `main.20959580.css` | `aa9945187b47751ec4acb382ad3c5a1bcd1b964b21919b37b713ffdd643f7994` |

`target/classes/build` contains and references the same current assets, but also retains three
unreferenced `main.ea9c0b24.js*` files. They are stale classpath output and must not be treated as
part of the source deployment.

### Packaged JAR divergence

`target/AR_Web_Scanner-4.2.jar`:

- SHA-256:
  `9ffa11697d1c74f6c689ed4cd7ddd8a03544c00b5126c920fb07f547a77ab09d`;
- embeds `main.22e5fb90.js` and `main.3451644a.css`;
- does not embed the current loose/classpath bundle.

This means `java -jar` and an IDE/classes launch can serve different React versions. No Java
process was running during P0, so the actually launched artifact could not be proven. P0 records
the divergence and does not rebuild or deploy it.

## 3. Database engine and migration baseline

The known Banca Stato configuration selects `TEXT`, which the application maps to SQLite
`path_db/database.db`.

| Fact | Value |
|---|---|
| Target fingerprint | `3fac1c2df04567fd96da767b8659ab46915cee0f650d82d12cc2a6558c068f43` |
| Database product | SQLite `3.50.3` |
| JDBC driver | SQLite JDBC `3.50.3.0` |
| Applied Java migrations | 14 |
| Latest applied migration | `2026-07-24__scanned_element_page_scope` |

The production startup authority is `com.allinweb.ch.db.MigrationRunner` and its
`schema_migrations(name, applied_at)` ledger. The similarly named resource-SQL runner under
`com.allinweb.ch.migration` is not the production authority and must not be run against this
database.

Supported production targets observed in the active connection code are PostgreSQL, SQLite
(`TEXT`), and Access/default. SQL Server constants exist, but the main connection path and
dependencies do not currently make it a supported P0 target.

## 4. Actual relationship schema

All listed relationship columns are nullable in this SQLite database unless noted.

| Table | Relationship column | Actual constraint |
|---|---|---|
| `variable` | `instruction_id` | nullable FK to `instruction.id`, `ON DELETE CASCADE` |
| `variable` | `bot_job_id` | nullable FK to `bot_job.id`, `ON DELETE CASCADE` |
| `instruction` | `block_id` | nullable FK to `block.id`, `ON DELETE CASCADE` |
| `instruction` | `parent_block_id` | nullable FK to `block.id`, `ON DELETE CASCADE` |
| `instruction` | `parent_id` | nullable, no database FK |
| `instruction` | `variable_id` | nullable, no database FK |
| `instruction` | `bot_job_id` | nullable FK to `bot_job.id`, `ON DELETE CASCADE` |
| `component_variable` | `instruction_id` | nullable FK to `component_instruction.id`, `ON DELETE CASCADE` |
| `component_variable` | `home_banking_id` | nullable FK to `home_banking.id`, `ON DELETE CASCADE` |
| `component_instruction` | `block_id` | nullable FK to `component_block.id`, `ON DELETE CASCADE` |
| `component_instruction` | `parent_block_id` | nullable FK to **`block.id`**, `ON DELETE CASCADE` |
| `component_instruction` | `parent_id` | nullable, no database FK |
| `component_instruction` | `variable_id` | nullable, no database FK |
| `component_instruction` | `home_banking_id` | nullable FK to `home_banking.id`, `ON DELETE CASCADE` |

Critical baseline finding: the actual `component_instruction.parent_block_id` target is
`block.id`, not `component_block.id`. P0 does not repair it. A later schema phase must first prove
the intended Component semantics on a disposable copy and register a dialect-safe migration.

## 5. Sanitized graph audit

Only aggregate counts were exported. No IDs, names, values, locators, URLs, operations,
credentials, or raw rows were emitted.

| Metric | Bot Jobs | Components |
|---|---:|---:|
| Instructions | 783 | 213 |
| Variables | 34 | 7 |
| Variable-linked instructions | 58 | 11 |
| Ownerless variables | 0 | 0 |
| Variables with missing owner row | 0 | 0 |
| Instructions with missing variable row | 0 | 0 |
| Instructions with missing parent row | 0 | 0 |
| Cross-owner parent links | 0 | 0 |
| Parent present but `parent_block_id` missing | 28 | 0 |
| Duplicate non-null variable owners beyond the first | 3 | 2 |

These are audit facts, not repair authorization. In particular:

- duplicate owners must not be deleted automatically;
- the 28 missing `parent_block_id` values must not be guessed;
- ownerless-variable behavior still needs an executable round-trip before the durable-memory phase;
- every future audit compares against a newly copied database, not these stale counts.

## 6. Reusable sanitized golden fixture

Canonical fixture:

`../../src/test/resources/fixtures/instruction-graph/golden-instruction-graphs-v1.json`

Executable integrity test:

`../../src/test/java/com/allinweb/ch/testsupport/GoldenInstructionGraphFixtureTest.java`

SELECT-only audit utility:

`../../scripts/p0/ReadOnlyInstructionGraphAudit.java`

The version-1 bundle uses synthetic owners and IDs and contains exactly:

1. `simple_parent_child`;
2. `get_multiple_consumers`;
3. `loop_with_positional_body`;
4. `if_else_endif_family`;
5. `goto_excel_goto_chain`;
6. `ownerless_memory_variable`;
7. `component_check_payment_complex`.

The complex Component case preserves the structural 15-row overlap, two-variable duplicate-owner
anomaly, and 25 sanitized reference links without customer fields.

The root `frozenMemoryContract` records the behaviors that future phases must not change:

- React visible-graph selection;
- one typed Memory item for a whole Block;
- fresh IDs on Apply;
- source rows unchanged;
- explicit links remapped;
- connected Memory reorder remains atomic;
- Memory clears only after acknowledged success.

Focused fixture result:

`GoldenInstructionGraphFixtureTest`: 2 tests passed, 0 failures/errors.

The audit utility and sanitized fixture rollback point is commit `36e9b31e`.

## 7. Existing Memory List golden coverage

P0 reuses existing focused tests rather than rewriting the working Memory List:

### React

- `instructionDependency.test.ts`;
- `useInstructionMemory.test.ts`;
- `GridItemComp.memoryParity.test.tsx`;
- `memoryList.groups.test.ts`;
- `useMemoryListDrag.test.ts`;
- `MemoryList.commandLifecycle.test.tsx`.

### Java

- `ComponentMemoryApplyServiceTest`;
- `MemoryListReorderTest`;
- `InstructionGraphControlFlowFixtureTest`;
- `InstructionGraphRevisionServiceTest`;
- `VariableRelationshipServiceTest`.

The exact focused results for these suites are recorded in section 10.

## 8. Active-bug ownership

The shared tracker is authoritative:

`ACTIVE_BUGS_TO_FIX_2026_07_28.md`

P0 owns only its documentation, audit utility, synthetic fixture, and fixture integrity test.

- BUG-001: automated/deployment verification passed; manual disposable-copy acceptance pending.
- BUG-002: pending and unclaimed; scheduled for independent Component work.
- BUG-003: implementation/automated regression complete; CODEX owns verification only; manual
  three-window isolation acceptance pending.
- BUG-004: partially covered; full Memory command -> transaction -> correlated publication ->
  already-open Bot Job repaint test remains unclaimed.
- BUG-005: this sanitized baseline is captured; repair implementation remains gated and unclaimed.
- BUG-006: remains owned by the Claude terminal.

## 9. Manual acceptance still required

P0 does not claim manual behavior that was not observed. The following remain explicit review
gates:

- BUG-001 deletion on a disposable production-shaped Bot Job;
- BUG-003 concurrent Bot Job, Component, and Memory List drag isolation;
- identification of the actual launch artifact/config when AR Web is next running;
- a new production backup immediately before any later schema or repair phase.

## 10. Focused verification record

| Check | Result |
|---|---|
| Audit utility against copied SQLite database | Passed; transaction rolled back |
| Source/copy database hash parity | Passed |
| Golden fixture integrity | 2 passed |
| React graph and frozen Memory suites | 10 suites passed; 109 tests passed |
| Java graph/copy/reorder suites | 8 classes passed; 89 tests passed; 0 failures/errors/skips |
| Broad frontend/backend suites | Intentionally not run |
| React build/deployment | Intentionally not run; no React source changed |

React emitted only existing test-harness warnings: npm's deprecated `msvs_version` setting and
four suites using deprecated `ReactDOMTestUtils.act`. The focused runs produced no failed tests or
runtime errors. The Java fixture integrity run is separate from the 89-test graph/copy/reorder run,
so P0 executed 91 focused Java tests in total.

## 11. Rollback

P0 rollback is isolated:

1. revert the P0 documentation/fixture commit;
2. remove the ignored `target/p0-audit` copy if it is no longer needed;
3. do not alter the live database;
4. do not rebuild or deploy the known-stale packaged JAR as part of a documentation rollback.
