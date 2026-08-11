# Multi-Bot-Job TypeScript Playwright and ExcelWrite Manager Roadmap

Date: 2026-08-11  
Status: implementation started; safe Main Dashboard admission slice deployed; execution runtime not started
Primary priorities: Smoke Test Integration, ExcelWrite Manager, concurrent Bot Job execution  
Migration strategy: incremental strangler replacement of the Java execution engine

## 1. Architecture decision

Do not embed a Node server inside Java and do not import Node-only Playwright into the browser
React bundle.

Create two isolated TypeScript applications:

1. The existing React frontend is the control plane. It owns execution orchestration, run state,
   REAL/SYNTHETIC selection, control flow, the in-memory ExcelWrite model, user editing, file
   creation policy, and the new floating pages.
2. A separate Node.js/TypeScript Playwright Runtime is the browser worker plane. It owns isolated
   browser processes, contexts, pages, locator resolution, physical browser actions, readiness,
   screenshots, and browser cleanup.

Java remains a narrow authority and persistence boundary. It authenticates the user and Bot Job,
freezes database facts, issues short-lived execution grants, commits runtime values, and atomically
writes finalized file artifacts. Java must not receive new browser orchestration, control-flow, CSV,
XLSX, column-order, or ExcelWrite lifecycle logic.

```text
React control plane
  |-- Single Smoke Test Integration
  |-- Multi-Bot-Job Execution Manager
  |-- ExcelWrite Manager (frontend memory)
  |
  | signed execution DTOs and correlated events
  v
Node/TypeScript Playwright Runtime
  |-- worker pool
  |-- one isolated execution session per runId
  |-- Browser/Context/Page ownership
  |
  | minimal authority and persistence requests
  v
Java gateway
  |-- owner/license/database authorization
  |-- immutable execution-plan snapshot
  |-- runtime-variable compare-and-set
  |-- validated atomic artifact write
  `-- no Playwright command engine in the target architecture
```

## 2. Current baseline to replace

The current Smoke Test Integration is a hybrid:

- React builds the program, runs conditions/loops/GOTO, and sends one correlated step at a time.
- `SmokeTestIntegrationService` allows one global active Integration run.
- `SmokeTestIntegrationSnapshotRepository` freezes database instructions and relationships.
- `ExcelDataWorkspaceService` freezes the selected REAL or SYNTHETIC data.
- `SmokeTestIntegrationStepExecutor` performs physical actions in Java.
- `ARWebDriver` and `ARPlaywrightDriver` expose one process-global Playwright page.
- `PlaywrightRuntimeHealingExecutor` resolves locators and performs CLICK/INPUT/OUTPUT in Java.
- `executeJob()` is not called by Smoke Integration, but the physical Integration engine is still
  Java and only one Bot Job can own Playwright.
- React currently sends `excelRowIndex=0`; complete multi-row execution is not implemented.
- ExcelWrite is not a supported Smoke Integration physical step.

The first migration must preserve the working React control-flow behavior while replacing the
single global Java Playwright seam.

## 3. Required user outcomes

### 3.1 Single Bot Job

- Smoke Test `INTEGRATION` uses the TypeScript Playwright Runtime.
- REAL and SYNTHETIC data use the same execution engine.
- The selected Bot Job endpoint is opened and render-ready before the first instruction.
- CLICK, INPUT, OUTPUT, GET, SET, REFRESH, control flow, and ExcelWrite are correlated and visible.
- Stop releases only the exact run and never disables unrelated row controls.

### 3.2 Multiple Bot Jobs

- Reuse the existing Main Dashboard Bot Job row and select-all checkboxes and the existing
  `selectedBotJobIds` / `loadedSelectedBotJobIds` state. Do not create a second selection model.
- The highlighted row used by Open, Clone, and Launch is not implicitly selected for execution.
  Only checked Bot Job rows participate.
- The same checked-row selection may feed Delete Selected or Run Selected, but those remain
  independent actions with separate confirmations, pending state, request IDs, and outcomes.
- A new two-line **RUN (N) / MULTIPLE JOBS** button immediately before **Refresh** opens a floating
  **Multi-Bot-Job Execution Manager**.
- At zero selected rows the button is disabled and non-glowing. Above zero it displays the exact
  count and uses the established cyan glow.
- Clicking the button copies the exact checked positive Bot Job IDs into an immutable launch draft.
  It does not delete rows or begin execution until the user reviews and confirms `Start Selected`.
- Each selected Bot Job chooses REAL or SYNTHETIC data independently.
- Same-organization and different-organization jobs can execute concurrently when permissions,
  worker capacity, data readiness, and license policy allow them.
- Each run has independent progress, current Block/instruction, logs, Stop, browser, variables, and
  ExcelWrite memory.
- `Stop Selected` and `Stop All` are explicit actions; one run failing must not stop other runs.

### 3.3 ExcelWrite Manager

- A new floating page titled **ExcelWrite Manager** presents all ExcelWrite commands reached by the
  selected execution or executions.
- Files appear as tabs in first-arrival order.
- Columns and values appear in first-arrival order and remain editable in frontend memory.
- No file is written when an ExcelWrite instruction merely arrives.
- The selected policy writes at the end of each Block or at the end of execution.
- CSV is the canonical first artifact. An XLSX target creates the companion CSV first and then the
  XLSX artifact from the same immutable frontend snapshot.
- React sends finalized artifact DTOs/chunks to Java only when a flush boundary is reached.

## 4. Ownership and isolation model

Every run uses an immutable identity:

```ts
export type ExecutionIdentity = Readonly<{
  runId: string;
  organizationId: number;
  homeBankingId: number;
  botJobId: number;
  workspaceEpoch: number;
  graphRevision: string;
  planRevision: string;
  dataMode: 'REAL' | 'SYNTHETIC';
}>;
```

Every run owns:

- a unique `runId` and monotonic event sequence;
- an immutable execution plan and frozen dataset revision;
- one isolated Playwright session;
- independent cookies, storage, permissions, downloads, temporary paths, and browser events;
- independent runtime-variable memory;
- independent ExcelWrite files and flush state;
- independent cancellation and terminal acknowledgement.

No two Bot Jobs share a Playwright `Page`. Same-organization jobs also remain isolated by default.

Recommended browser isolation:

| Execution type | Isolation |
| --- | --- |
| REAL banking execution | Dedicated Node worker process, Browser, BrowserContext, and Page |
| Different organizations | Separate BrowserContext at minimum; dedicated process preferred |
| Different VPN/proxy/security zone | Separate worker process or container |
| SYNTHETIC, non-sensitive execution | Separate BrowserContext in a bounded shared worker process |

The worker pool must enforce configurable global, per-organization, and per-Bot-Job limits. It must
never create an unbounded number of Chromium processes.

## 5. React control plane

Business logic must be pure TypeScript outside JSX. React components render state and dispatch
typed intents; reducers/state machines own lifecycle changes.

### 5.1 Proposed execution components

```text
src/components/multi-execution/
  MultiBotJobExecutionPage.tsx
  MultiBotJobExecutionPage.module.scss
  MultiBotJobPicker.tsx
  MultiBotJobRunCard.tsx
  MultiBotJobToolbar.tsx
  MultiBotJobExecutionHelpModal.tsx

src/components/execution-runtime/
  ExecutionCoordinatorProvider.tsx
  executionContracts.ts
  executionReducer.ts
  executionSelectors.ts
  executionProgram.ts
  executionControlFlow.ts
  executionDataProvider.ts
  executionWebSocket.ts
  executionReconnect.ts

src/components/excel-write-manager/
  ExcelWriteManagerPage.tsx
  ExcelWriteManagerPage.module.scss
  ExcelWriteFileTabs.tsx
  ExcelWriteFileGrid.tsx
  ExcelWritePolicyControl.tsx
  ExcelWriteStatusBar.tsx
  ExcelWriteHelpModal.tsx
  domain/excelWriteTypes.ts
  domain/excelWriteReducer.ts
  domain/excelWriteSelectors.ts
  domain/excelWriteCsvEncoder.ts
  domain/excelWriteArtifactBuilder.ts
  workers/excelWriteArtifact.worker.ts
```

### 5.2 Main page selection and button

Add a two-line button following the established top-toolbar design immediately before the current
`Refresh` button:

```text
RUN (N)
MULTIPLE JOBS
```

The button consumes the existing checkbox selection already rendered by `MainDashboard`. It must
not infer selection from the highlighted row and must not introduce another checkbox collection.

| Checked Bot Jobs | Appearance | Interaction |
| ---: | --- | --- |
| `0` | Established neutral, non-glowing style | Disabled; no manager admission |
| `> 0` | Established cyan glow and exact count | Opens/focuses the manager with an immutable copy of the selected IDs |

Accessibility and lifecycle rules:

- expose an exact accessible label such as `Run 3 selected Bot Jobs`;
- respect the application's reduced-motion behavior for the cyan animation;
- keep the Main Dashboard selection after opening the manager;
- let the existing dashboard refresh pruning remove IDs for Bot Jobs that no longer exist;
- revalidate every launch-draft owner and Bot Job before Start, failing closed if one disappeared;
- copy independent ID sets for Delete Selected and Run Selected so neither action can settle,
  trigger, cancel, or otherwise mutate the other action.

The button opens or focuses one fixed `multiBotJobExecutionManager` page. It must not start every
Bot Job immediately. The user reviews the exact checked rows and confirms `Start Selected`.

The manager contains:

- a read-only summary/list of the exact Bot Jobs checked on Main Dashboard;
- a REAL/SYNTHETIC selector per selected Bot Job;
- readiness diagnostics for plan, Excel Data, endpoint, and worker capacity;
- maximum parallelism input bounded by server capacity;
- `Start Selected`, `Stop Selected`, and `Stop All`;
- one live card per run;
- a shortcut to the corresponding ExcelWrite Manager state.

### 5.3 Frontend execution ownership

The execution coordinator page owns the in-memory run state. A floating ExcelWrite Manager is a
projection of that state, not its only owner. Closing the ExcelWrite page must not delete data.

If the owning execution page disconnects or closes:

1. Node pauses new physical steps.
2. A bounded reconnect grace period starts.
3. A matching owner/generation may reconnect and bootstrap the exact run revision.
4. Expiry stops and cleans the run. It must never continue unattended under a different user.

No completed action is retried automatically after an unknown outcome.

## 6. Node/TypeScript Playwright Runtime

Create a separate package/service. It must not be compiled into the CRA browser bundle.

```text
playwright-runtime-ts/
  package.json
  tsconfig.json
  src/server.ts
  src/config/runtimeConfig.ts
  src/contracts/executionContracts.ts
  src/security/executionGrantVerifier.ts
  src/coordinator/executionCoordinator.ts
  src/coordinator/executionRegistry.ts
  src/pool/playwrightWorkerPool.ts
  src/pool/workerProcess.ts
  src/session/executionSession.ts
  src/session/sessionState.ts
  src/browser/browserFactory.ts
  src/browser/pageReadiness.ts
  src/browser/pageIdentity.ts
  src/actions/actionDispatcher.ts
  src/actions/clickAction.ts
  src/actions/inputAction.ts
  src/actions/outputAction.ts
  src/actions/refreshAction.ts
  src/locator/locatorResolver.ts
  src/locator/registryCandidates.ts
  src/locator/targetValidator.ts
  src/diagnostics/runtimeDiagnostic.ts
```

### 6.1 Runtime responsibilities

- Verify a signed, short-lived Java-issued execution grant.
- Create one `ExecutionSession` per run ID.
- Allocate a safe worker slot.
- Launch/configure the browser or context for that exact run.
- Navigate to the plan endpoint and wait for bounded render readiness.
- Execute exactly one correlated physical action per step request.
- Resolve authored locator, current owner/page registry locator, canonical name, client alias, and
  validated coordinates in the established fail-closed order.
- Return ambiguity and validation diagnostics without guessing.
- Serialize all operations for one Page while allowing other run sessions concurrently.
- Stop, close, and clean only the exact run resources.
- Never connect directly to the application database or write Excel/CSV files.

### 6.2 Pool rules

- A queue admits runs only when capacity is available.
- REAL runs default to one worker process per run.
- SYNTHETIC runs may share a browser process but never a context or page.
- Limits are configuration, not client assertions.
- Worker crashes fail only their owned runs.
- A run heartbeat and lease deadline detect abandoned sessions.
- Memory, process count, page count, execution duration, and output sizes are bounded.
- Every cleanup verifies that the context, page, downloads, and temporary directory are gone.

## 7. Minimal Java gateway

Do not create a Node server in Java. Java supplies small authoritative services and contracts only.

### 7.1 Reuse/extract existing code

- Reuse the SQL snapshot logic currently in `SmokeTestIntegrationSnapshotRepository`.
- Extract a serializable immutable execution-plan DTO without exposing mutable database objects.
- Reuse exact detached-window owner, binding, workspace, graph-revision, and license authorization.
- Reuse normalized variable definitions/slots and typed command configuration.
- Reuse the existing controlled-path and atomic-write safety model.

### 7.2 Proposed Java classes

```text
ExecutionPlanSnapshotService
ExecutionRuntimeGrantService
ExecutionRuntimeGrantSigner
ExecutionRuntimeValueCommitService
ExecutionArtifactWriteService
ExecutionArtifactWriteLedger
ExecutionV2Contracts
```

Java endpoints/operations:

```text
execution.v2.plan.freeze
execution.v2.runtime.grant
execution.v2.runtimeValue.commit
execution.v2.artifact.begin
execution.v2.artifact.chunk
execution.v2.artifact.commit
execution.v2.artifact.abort
```

Java must validate:

- exact current user, organization, Bot Job, workspace generation, plan revision, and run ID;
- license policy;
- instruction/variable ownership for a runtime write;
- configured and controlled destination directory;
- allowed filename, extension, size, chunk order, checksum, and content type;
- duplicate/replayed request IDs;
- that only a temporary file is written before checksum verification and atomic final move.

Java must not interpret workbook rows, merge columns, choose flush timing, generate CSV, generate
XLSX, or control Playwright.

## 8. Execution protocol

### 8.1 Frozen start DTO

React requests a database-authoritative snapshot. The response contains:

- exact run/owner generations and revisions;
- ordered Blocks and instructions;
- typed command configuration and normalized variable slots;
- owner-scoped Page Mapping candidate DTOs or a revision-bound lookup grant;
- selected REAL/SYNTHETIC dataset snapshot/revision;
- browser endpoint and non-secret browser options;
- signed Node runtime grant and expiry.

The client cannot author locators, owner identity, output paths, or database relationships.

### 8.2 Node runtime operations

```text
playwright.v2.run.start
playwright.v2.run.bootstrap
playwright.v2.run.step
playwright.v2.run.refresh
playwright.v2.run.stop
playwright.v2.run.close
playwright.v2.run.heartbeat
```

Events:

```text
playwright.v2.run.queued
playwright.v2.run.ready
playwright.v2.step.started
playwright.v2.step.completed
playwright.v2.step.failed
playwright.v2.run.paused
playwright.v2.run.stopped
playwright.v2.run.completed
playwright.v2.run.cleaned
```

Every message carries contract version, request ID, run ID, owner identity, plan revision, and
monotonic sequence/revision. Duplicate exact requests replay their terminal result. Conflicting
reuse of an accepted request ID is dropped or explicitly refused without settling the original.

## 9. REAL and SYNTHETIC execution

- Both modes use the same React program and Node Playwright actions.
- The dataset is frozen at run start and cannot be changed by later Excel Data edits.
- REAL dirty data fails start until explicitly saved/acknowledged under the chosen policy.
- SYNTHETIC data is deterministic for a recorded seed and dataset revision.
- Every input step carries the exact selected row index; remove the current hard-coded row zero.
- `NEXT ROW` advances the React-owned dataset cursor with bounds and terminal behavior.
- No credential/input value is written to logs, URLs, diagnostics, or operational events.
- Different runs never share mutable dataset or runtime-variable maps.

## 10. ExcelWrite Manager rules

### 10.1 In-memory model

```ts
export type ExcelWriteFileState = Readonly<{
  fileId: string;
  runId: string;
  targetId: string;
  displayName: string;
  finalFormat: 'CSV' | 'XLSX';
  delimiter: string;
  columns: readonly string[];
  rows: readonly Readonly<Record<string, string>>[];
  touchedBlockIds: readonly number[];
  revision: number;
  dirty: boolean;
  flushState: 'MEMORY' | 'BUILDING' | 'UPLOADING' | 'SAVED' | 'FAILED';
}>;
```

Use stable `fileId`/`targetId`, not the displayed basename. Equal basenames in different approved
directories are distinct; conflicting outputs to the exact same canonical target are rejected or
serialized by explicit policy.

### 10.2 Instruction arrival

When React reaches ExcelWrite:

1. Validate the typed instruction target and normalized `READ` variable slot from the frozen plan.
2. Read the exact run-local variable value.
3. Create/select the file tab on first use.
4. Create the destination column on first use.
5. Upsert the current execution row in arrival order.
6. Record source Block/instruction IDs and mark the file dirty.
7. Do not call Java and do not touch disk yet.

### 10.3 Flush policy

Use one mutually exclusive control:

- **End of each Block**: flush dirty files touched by that successfully completed Block.
- **End of execution**: keep all files in memory and flush only during finalization. This is the
  recommended default for minimum disk I/O.

A mandatory final flush saves all remaining dirty files under either policy. Policy is frozen at
run start; UI changes apply to the next run.

Stop/failure defaults:

- normal completion: flush automatically;
- user Stop: retain partial frontend memory and offer `Save Partial` or `Discard`;
- execution failure: retain partial memory and require the same explicit decision;
- browser/React crash: memory-only data is not durable; report this limitation honestly.

### 10.4 CSV-first artifact construction

Run serialization in a browser Web Worker so large CSV/XLSX construction does not block React.

- Build deterministic CSV bytes first.
- For a CSV target, upload only the CSV.
- For an XLSX target, retain the companion CSV and build XLSX from the same immutable table
  snapshot using a pinned, audited browser-compatible library.
- Calculate SHA-256 and exact byte length in the frontend.
- Send a manifest DTO followed by bounded binary chunks.
- Java validates every chunk/checksum and atomically moves the completed temporary artifact.
- CSV success plus XLSX failure is `CSV_READY / XLSX_FAILED`, not overall success.
- Detect `.csv` companion path collisions before execution starts.

## 11. Multi-Bot-Job user flow

```text
Main page: check exact Bot Job rows
  -> RUN (N) / MULTIPLE JOBS turns cyan when N > 0
  -> button immediately before Refresh opens/focuses manager
  -> manager receives the immutable checked-ID launch draft
  -> choose REAL/SYNTHETIC per row
  -> validate plan/data/endpoint/capacity
  -> confirm Start Selected
  -> create one isolated run per Bot Job
  -> queue beyond configured parallel capacity
  -> show live cards and ExcelWrite tabs
  -> finish each run independently
  -> preserve terminal evidence until dismissed
```

Run cards show:

- organization, Bot Job, run ID, and data mode;
- `QUEUED`, `STARTING`, `LOADING PAGE`, `READY`, `RUNNING`, `FLUSHING`, and terminal state;
- current Block/instruction and progress;
- browser/page readiness;
- passed/warning/failed/bypassed counts;
- ExcelWrite files in memory/dirty/saved;
- Stop and open-details actions.

The Main page button and manager must reuse established AR Web floating-page, Pages badge, status,
help, responsive, keyboard, and focus patterns without placing feature CSS into another page's
module.

## 12. Command migration strategy

Move command families in small checkpoints. No silent fallback is permitted within a run.

1. CLICK, INPUT, OUTPUT, and REFRESH.
2. GET and SET with run-local variables plus explicit durable write-through.
3. Wait/Pause and page-readiness commands.
4. IF/ELSEIF/ELSE/ENDIF and CheckValue.
5. LOOP, GOTO, EXCEL GOTO, and NEXT ROW.
6. ExcelWrite and final artifact delivery.
7. PDF/CSV checks and remaining typed commands.
8. Browser close, screenshots, and specialized actions.

Each frozen run declares exactly one runtime:

```text
TYPESCRIPT_PLAYWRIGHT_V2
LEGACY_JAVA
```

An unsupported V2 command fails preflight before opening the browser. It must not switch to Java
after earlier TypeScript steps have already executed.

## 13. Failure, retry, and reconnect rules

- A physical action is at-most-once. Unknown outcome blocks that run until authoritative recovery.
- Node caches bounded terminal results by exact run/sequence/request identity.
- React retries only requests proven not admitted; admitted unknown physical actions are never
  repeated automatically.
- Browser navigation, page identity, and render readiness fail closed.
- Locator ambiguity produces zero physical attempts.
- Node failure affects only runs owned by that worker.
- Java artifact upload supports exact idempotent chunk replay and an atomic commit.
- A failed artifact commit never replaces an existing final file.
- React retarget, user switch, license loss, plan revision change, or Bot Job deletion retires the
  affected run without affecting unrelated runs.
- Logs contain IDs, stages, counts, durations, and safe codes—not URLs, locators, banking text,
  credentials, cell values, or file contents.

## 14. Implementation phases and commit checkpoints

### P0 - Contract and threat model

- Freeze V2 owner/run/event/artifact DTOs in shared generated TypeScript definitions.
- Define signed grant expiry, replay protection, capacity rules, size limits, and secret handling.
- Record an architecture decision for direct React-to-Node WebSocket plus Java authority calls.
- Add no runtime behavior.

### P1 - Node runtime skeleton

- Create the standalone TypeScript package, configuration, health/readiness endpoints, structured
  safe logging, and graceful shutdown.
- Add grant verification and one in-memory run registry without launching a browser.
- Provide independent build/lint/unit-test scripts.

### P2 - Isolated Playwright sessions

- Implement the bounded worker pool and one session per run.
- Add start/navigate/readiness/refresh/stop/cleanup.
- Prove two Bot Jobs cannot share context, page, storage, or downloads.

### P3 - First physical vertical slice

- Port authored locator resolution and CLICK/INPUT/OUTPUT to TypeScript.
- Add current-page mapping candidates and ambiguity diagnostics.
- Keep the existing Java Integration path selectable only as an explicit whole-run rollback mode.

### P4 - Smoke Test Integration V2

- Connect the current React control-flow engine to Node V2.
- Freeze authoritative plan/data through minimal Java operations.
- Remove hard-coded row zero and implement REAL/SYNTHETIC row selection.
- Live-prove Lloyds Bot Job 29 through the V2 path.

### P5 - React ExcelWrite Manager

- Add the frontend reducer/store, floating page, file tabs, editable grids, policy control, memory
  and dirty-state presentation.
- Implement ExcelWrite arrival with no disk write.
- Keep artifacts in memory through Manager window close while the owner execution page remains.

### P6 - Artifact generation and Java write adapter

- Add deterministic CSV encoder, browser worker generation, XLSX library gate, checksums, and chunks.
- Add minimal Java validated begin/chunk/commit/abort operations.
- Verify end-of-Block, end-of-execution, final safety flush, Save Partial, Discard, and retry.

### P7 - Multiple Bot Job manager

- Reuse the existing Main Dashboard row and select-all checkboxes; do not add competing execution
  selection state.
- Add the count-aware cyan `RUN (N) / MULTIPLE JOBS` button immediately before `Refresh`.
- Keep it disabled/non-glowing at zero, accessible at every count, and based only on checked IDs.
- Keep Delete Selected and Run Selected independently confirmed and correlated.
- Add the manager's selected-job review, per-run data mode, bounded parallelism, run cards, and
  independent Stop.
- Prove concurrent same-organization and different-organization isolation.

### P8 - Complete TypeScript command coverage

- Port remaining command families in the order in section 12.
- Add preflight capability reporting and refuse incomplete V2 plans before side effects.

### P9 - Deployment hardening

- Package the Node runtime independently.
- Add service health, version compatibility, capacity metrics, crash cleanup, and rolling restart.
- Configure Windows desktop/development launch and production/container launch without embedding
  Node implementation inside Java.

### P10 - Java retirement

- Stop routing new runs to `SmokeTestIntegrationStepExecutor` and Java Playwright healing.
- Remove global Integration ownership only after all runs use the Node pool.
- Retire Java `executeJob()` command families only after equivalent V2 live evidence exists.
- Keep database, authorization, secrets, and atomic persistence Java boundaries.

Every phase receives a narrow `CODEX-` commit and push. Frontend, Node runtime, Java gateway,
deployment assets, migrations, and documentation remain separate commits.

## 15. Verification matrix

### 15.1 Functional

- Zero checked rows keeps Run Multiple Jobs disabled and non-glowing.
- One or more checked rows shows the exact count and cyan glow and admits exactly those IDs.
- Highlighting a row without checking it does not admit it to execution.
- Select-all, individual uncheck, and refresh pruning update the run-button count.
- Delete Selected and Run Selected share only the explicit checkbox input; their confirmation,
  request, pending, cancellation, and outcome paths remain independent.
- Opening or starting a run never deletes a Bot Job, and deleting checked rows never starts a run.
- One REAL Lloyds run.
- One SYNTHETIC Lloyds run.
- Multiple rows with NEXT ROW.
- Concurrent Lloyds plus BancaStato.
- Two jobs in the same organization.
- Two jobs in different organizations.
- Queue beyond worker capacity.
- Independent Stop and one-run failure containment.
- Browser refresh and page-readiness failure.
- Authored, registry, canonical, alias, coordinate, missing, stale, and ambiguous locator outcomes.

### 15.2 ExcelWrite

- Two ExcelWrite instructions in different Blocks share one file and independent columns.
- Multiple files create multiple first-arrival tabs.
- End-of-Block writes only touched dirty files and retains accumulated rows.
- End-of-execution performs no early disk write.
- CSV target writes an exact CSV.
- XLSX target writes CSV first and XLSX from the same snapshot.
- CSV success/XLSX failure remains recoverable and accurately reported.
- Stop/failure Save Partial and Discard.
- Duplicate artifact chunks and commit requests are idempotent.
- Existing file survives checksum, authorization, or conversion failure.

### 15.3 Isolation and lifecycle

- No cookie/storage/download/runtime-variable/file-tab crossover.
- No cross-owner plan, mapping, dataset, runtime value, or destination-path access.
- React disconnect grace, exact reconnect, takeover refusal, and expiry cleanup.
- Node worker crash cleans only its resources and does not orphan browser processes.
- Bot Job switch/delete, license change, and stale revision fail closed.
- Memory/process/file/queue caps and large-artifact behavior.

### 15.4 Delivery

- Focused frontend and Node unit tests.
- Node Playwright integration tests using local deterministic pages.
- Focused Java authorization/artifact tests.
- Type checks, lint, builds, `git diff --check`, and complete manual diff review.
- Exact package/image/service versions and health evidence.
- User-driven live banking acceptance only after authorization and without logging secrets.

## 16. Rollout and rollback

- Introduce an explicit whole-run runtime selector guarded by server configuration.
- Start with one allowed Bot Job and maximum concurrency one.
- Expand to SYNTHETIC multi-run before REAL multi-run.
- Expand organization allowlists only after isolation evidence.
- Keep V1 Java Integration available as a separately selected rollback runtime during migration.
- Never switch a partially executed run from V2 to V1.
- Rollback stops V2 admission, drains/stops V2 sessions, and leaves existing Java/database paths
  unchanged.
- No schema migration is required for P0-P5 unless durable per-Bot-Job policies or audit history are
  approved. Any later migration is separately created, applied, and evidenced.

## 17. First implementation slice

The P0 plus P1 safe coding slice is implemented:

1. Define V2 shared contracts and security limits.
2. Scaffold the independent Node/TypeScript service.
3. Add health/readiness and signed-grant verification.
4. Add no browser action, no database write, and no deployment switch.

The first functional vertical slice is then P2-P4 for one Lloyds Bot Job 29 run. ExcelWrite Manager
P5-P6 follows immediately, before enabling the Main page multi-run button in P7. This ordering
prevents multiplying an unproven single-run execution path.

## 18. Completion gates

```text
[x] V2 architecture/threat model approved
[x] Shared contracts implemented and versioned
[x] Node runtime package created
[x] Signed Java grant signing/verification boundary implemented (authorized routing still open)
[x] Isolated worker pool implemented
[ ] Single-run Smoke Integration moved to Node Playwright
[ ] REAL and SYNTHETIC data verified
[ ] Multiple Excel rows verified
[ ] ExcelWrite Manager implemented in React memory
[ ] CSV-first artifact generation implemented in frontend
[ ] Minimal Java atomic artifact writer implemented
[ ] End-of-Block and end-of-execution policies verified
[x] Main RUN / MULTIPLE JOBS button implemented
[ ] Concurrent same-organization isolation verified
[ ] Concurrent different-organization isolation verified
[ ] Failure/reconnect/stop/cleanup verified
[ ] Remaining commands migrated to TypeScript
[ ] Java V1 execution retirement audit complete
[x] Focused tests passed
[ ] Broader builds/tests passed
[x] Committed and pushed by narrow checkpoint
[ ] Node/React/Java artifacts deployed with exact versions
[ ] Services healthy and restart counts stable
[ ] User live behavior verified
[ ] Evidence and handoff updated
```

## 19. Explicit limitations

- Browser React cannot execute Playwright or directly write arbitrary filesystem paths.
- Frontend-memory-only ExcelWrite data is lost if the owning execution page/browser process crashes;
  crash recovery requires an explicitly approved journal and is not silently implied.
- Multiple Chromium processes have significant memory cost; configured admission limits are
  mandatory.
- VPN/proxy routing is process/host infrastructure and may require dedicated workers or containers.
- Infinite pages, closed Shadow DOM, virtualized elements, canvas/video state, and ambiguous live
  targets remain bounded fail-closed cases rather than targets for unsafe guessing.

## 20. Main Dashboard admission checkpoint - 2026-08-11

This checkpoint implements only the safe P7 admission surface; it does not claim that the Node
runtime, worker pool, execution grants, REAL/SYNTHETIC selection, or concurrent execution exists.

- Reused the authoritative Main Dashboard row/select-all checkbox state. The highlighted row is
  still not an execution selection.
- Added `RUN (N) / MULTIPLE JOBS` immediately before `Refresh`; zero is disabled/non-glowing and a
  positive count uses the cyan glow with reduced-motion support.
- Opening freezes an owner-qualified snapshot of the exact checked rows in one isolated floating
  `Multi-Bot-Job Execution Manager`. Checkbox refresh/delete behavior remains separate.
- `Start Selected` is intentionally disabled with an explicit runtime-not-installed explanation,
  preventing accidental use of the process-global legacy Playwright page.
- Pages Open now inventories and can close this inline manager independently of Auto Test.
- Frontend source commit/push: `f6aa520`; backend source commit/push: `1740e8da`; frontend resource
  deployment commit/push: `9fb06b73`.
- `npm run build` succeeded with pre-existing warnings. `mvn -DskipTests compile` succeeded for 568
  Java sources with two pre-existing warnings. No tests were run in this checkpoint.
- The 58-file build mirrors exactly into `src/main/resources/build` and `target/classes/build`.
  Entry assets: `main.d551b552.js` SHA-256
  `1933EBE8D5B381DCCD588CCC2BFFA1385A148BAED43B0C3ACCF3F22CDBA55C28` and
  `main.9b770504.css` SHA-256
  `2F7A8F609361F3017B0357DC58C35F5718CACDF4B8624E9796CDEC01E91F0A95`.
- No ARControlPanel JVM was running after deployment, so live HTTP/UI freshness is not claimed.

The P0/P1 runtime-boundary work below supersedes the earlier next-step statement for this roadmap.

## 21. Execution V2 P0/P1 checkpoint - 2026-08-11

- Added the isolated `playwright-runtime-ts` package. It is not bundled into React or embedded in
  Java.
- Recorded ADR-0001 and the P0 threat model: React control plane, Node worker plane, and Java
  authority/persistence boundary.
- Added strict version-1 execution/grant contracts and an HS256 verifier with exact issuer,
  audience, type, key ID, runtime, UUID owner/run identities, revision hashes, capabilities,
  lifetime, activation, and expiry validation.
- Added a bounded in-memory reservation registry with exact replay, conflict refusal, capacity,
  expiry cleanup, exact-token read/release authority, and no live-entry eviction.
- Added loopback-only HTTP liveness/readiness/version and reserve/bootstrap/release endpoints.
  Missing grant configuration is live but returns not-ready; no grant or request body is logged.
- The package contains no Playwright dependency and performs no browser, database, runtime-value,
  CSV/XLSX, or filesystem action.
- `npm run lint` and TypeScript build passed. All 10 focused Node tests passed. `npm install` audited
  four packages with zero vulnerabilities.
- Source commit/push: `e03f4e3f`. No Java or frontend source changed, so no Maven/frontend build was
  required for this checkpoint.
- No service, image, or package was deployed or started. Java grant issuance and secret
  provisioning do not exist yet, so production execution remains disconnected and fail-closed.

The signer checkpoint below completes the previously listed cross-language authority prerequisite.

## 22. Java grant signer compatibility checkpoint - 2026-08-11

- Added Java Execution V2 constants, validated immutable authorized-facts DTO, fail-closed
  environment configuration, deterministic HS256 signer, and short-lived grant service.
- Java creates both UUIDs, fixes the runtime capabilities, emits second-precise times, enforces
  lowercase SHA-256 revisions and JavaScript-safe workspace epochs, and never logs the secret or
  compact grant.
- Missing secret leaves issuance disabled; malformed base64url, short secrets, unsafe key IDs, and
  grant lifetimes outside 10 to 120 seconds are refused.
- Added one deterministic test-only Java grant fixture. Java proves it emits the exact compact
  bytes; the independent Node verifier proves it accepts and parses those same claims.
- The service is intentionally not exposed through current Smoke Integration/WebSocket routing.
  Current authorization has Home Banking/Bot Job authority but cannot yet prove the separate
  `organizationId`, so no client-provided organization assertion can mint a grant.
- `mvn -Dtest=ExecutionRuntimeGrantServiceTest test` compiled 572 production and 334 test sources;
  all 3 focused Java tests passed. Node lint/build passed and all 11 focused Node tests passed.
- Source commit/push: `3e784a43`. No frontend source or deployment assets changed.
- No secret was provisioned, and no Node service, browser, application service, image, migration,
  or database was started or changed.

## 23. Execution V2 P2 isolated Playwright session checkpoint - 2026-08-11

- Added an internal bounded worker pool with global, per-organization, and per-Bot-Job admission
  limits plus a bounded queue. The queue scans for the next eligible owner so one saturated
  organization cannot block an independent organization behind it.
- Every admitted run owns a dedicated Chromium process, BrowserContext, and Page with opaque
  instance IDs. Contexts block service workers and downloads; no page, cookies, storage, or browser
  handle is shared between runs.
- Added strict HTTP(S) endpoint validation, bounded DOM-content navigation/readiness, explicit HTTP
  error refusal, refresh, loading interruption, stop, and cleanup of page/context/browser resources.
- Navigation failure and asynchronous browser/page termination fail only the affected run, retain a
  safe terminal diagnostic, release its exact capacity slot, and admit the next eligible queued run.
  Unexpected-close notification is buffered so a process that dies during handle construction is
  still observed after callback registration.
- `playwright-core` is pinned at `1.61.1`; it does not download or bundle Chromium. Production must
  explicitly provide a compatible executable or channel.
- `npm run typecheck` and `npm run lint` passed. `npm test` built the package and all 15 tests passed,
  including same-owner queueing, different-owner concurrent admission, opaque resource separation,
  loading interruption, refresh, navigation-failure cleanup, and one-browser crash containment.
- Source commit/push: `a55bd648`. No Java or frontend source changed, so no Maven or frontend build
  was run. No browser binary was launched, and no application service, database, migration, image,
  or deployment was changed.
- The pool remains intentionally internal: current HTTP routes still expose only reservation
  operations, and current React/Java execution cannot start, refresh, or stop these sessions.

Next: P3 authored locator resolution and the first CLICK/INPUT/OUTPUT physical-action vertical
slice inside the Node runtime. Before application admission, the Java routing adapter must resolve
`organizationId` authoritatively and expose only signed, capability-bound run operations.

## 24. Execution V2 P3 internal physical-action checkpoint - 2026-08-11

- Added strict internal action DTOs for sequenced CLICK, INPUT, and OUTPUT instructions plus frozen
  authored selectors and owner/page-scoped registry candidates. Invalid IDs, page keys, tags,
  selectors, scopes, candidate counts, and input sizes are refused before page interaction.
- Ported the established resolution priority: exact live page identity, ordered authored selectors,
  registry locator/canonical/client-alias tiers, then unique live canonical/client-alias names.
  Selector ambiguity is deferred only while a stronger later tier may resolve uniquely; terminal
  ambiguity and missing targets produce zero physical attempts.
- Added Playwright validation for visibility, exact tag, same-origin iframe scope, top-document
  boundary, and action capability. Shadow-scoped targets remain deliberately unsupported and
  fail-closed. Unsafe coordinate fallback is not part of V2.
- CLICK performs one click; INPUT supports writable controls and exact unique native-select values
  plus optional Enter/Tab; OUTPUT preserves a legitimate empty value rather than treating it as a
  missing target. Exact page identity is rechecked immediately before the physical operation.
- Added a bounded 4,096-result per-session sequence ledger. Exact duplicate requests replay the
  cached terminal result without another physical attempt; changed payloads and out-of-order
  sequences are refused. An infrastructure exception with uncertain action outcome terminates the
  run as `ACTION_OUTCOME_UNKNOWN` and remains exactly replayable.
- The Node page-key implementation was verified against the established Java identities for Lloyds
  and BancaStato. P3 diagnostics retain only safe codes, stages, counts, validation flags, and
  physical-attempt count; locator strings, page URLs, and input/output values are not logged.
- `npm run lint` passed. `npm test` built the package and all 26 tests passed, including authored,
  registry-healed, canonical, alias, ambiguity, empty-output, page-change, Shadow refusal,
  at-most-once replay/conflict/order, and unknown-outcome cases plus all P0-P2 coverage.
- Source commit/push: `21027e59`. No Java or frontend source changed, so no Maven or frontend build
  was run. No browser binary, banking page, application service, database, migration, image, or
  deployment was started or changed.
- This remains an internal engine. Smoke Test Integration still uses Java V1 because Java does not
  yet expose the frozen authoritative plan/registry action DTO and the Node server has no signed
  start/action/refresh/stop route.

Next: the P4 admission adapter must authoritatively resolve organization and Bot Job ownership,
freeze the plan/data/registry inputs, add capability-bound Node run/action routes, and connect one
explicit Smoke Integration V2 run without permitting partial fallback to Java V1.

## 25. Execution V2 P4 run-authority prerequisite - 2026-08-11

- Extended the signed-grant capability vocabulary for start, action, refresh, stop, and heartbeat
  while retaining the original reserve/bootstrap/release compatibility fixture.
- Reservation now creates one cryptographically random 256-bit opaque run-access token. Only an
  exact replay of the same admitted signed grant can receive the same in-memory token; conflicting
  run or grant reuse remains refused.
- The token is validated as canonical base64url, retained only in runtime memory, compared through
  SHA-256 plus constant-time equality, and constrained by the capabilities copied from the verified
  Java grant. It is not added to logs or the public run view.
- A run must activate with `runtime.start` before action/refresh/stop/heartbeat authority is usable.
  Activation replaces the short admission expiry with a renewable idle lease bounded to 10-300
  seconds. Every authorized active operation renews that lease; abandoned runs are swept without
  allowing an expired, never-activated reservation to survive.
- This solves the mismatch between the signer maximum 120-second grant and a legitimate longer
  execution without making the bearer grant long-lived. HTTP start/action/refresh/stop/heartbeat
  routes are still absent and therefore cannot invoke the worker pool yet.
- `npm test` built the isolated package and all 28 focused Node tests passed. The first sandboxed
  attempt was unable to spawn Node test workers (`EPERM`); the identical approved out-of-sandbox
  rerun passed. `git diff --check` passed.
- Source commit/push: `0d122f27`. No Java or frontend source changed, so Maven and the frontend
  build were not run. No browser, application service, database, migration, package, or deployment
  was started or changed.

Next: expose strictly parsed token-authorized start/action/refresh/stop/heartbeat routes, connect
them to the existing isolated worker/action engine, and then add the minimal Java authority adapter
that supplies only frozen server-derived plan, registry, endpoint, and dataset facts.

## 26. Execution V2 P4 isolated runtime-route checkpoint - 2026-08-11

- Connected loopback-only Start, Session/Heartbeat, Action, Refresh, Stop, and terminal Release HTTP
  routes to the existing bounded worker pool. These routes use the opaque run token after signed
  admission; the short-lived grant is not reused as long-running authority.
- Start accepts only a bounded HTTPS/HTTP endpoint and safe headless/channel options. Arbitrary
  executable paths and unknown fields are refused. A normalized launch fingerprint makes exact
  Start replay idempotent and rejects changed-payload reuse without creating another browser.
- Action accepts a bounded JSON body, rejects unknown top-level and candidate fields, validates
  strings/tags/keyboard flags and all established action limits, then enters the existing serialized
  at-most-once session action ledger.
- Added configurable global, queue, organization, Bot Job, and idle-lease limits. An expired active
  lease now stops and releases only its exact worker session; an unactivated expired reservation
  creates no worker. Legacy signed-grant deletion refuses an active worker and requires the run-token
  terminal path, preventing an orphaned Chromium session.
- `/version` now reports that the isolated runtime action routes are available. This does not mean
  the ARWeb application uses them: there is still no Java adapter, secret provisioning, React route,
  or live browser deployment.
- `npm run typecheck` passed. `npm test` built the package and all 31 focused tests passed, covering
  token routes, exact/conflicting Start, action DTO refusal, wrong-token isolation, Stop/Release, and
  lease-expiry worker cleanup in addition to all P0-P3 behavior. `git diff --check` passed.
- Source commit/push: `e34c95c7`. No Java or frontend source changed, so no Maven/frontend build was
  run. No browser binary, banking page, application service, database, migration, artifact, or
  deployment was started or changed.

Next: implement the minimal Java-side runtime client/authority adapter. It must resolve organization
and Bot Job ownership from the current workspace, freeze plan/data/registry/endpoint facts, retain
the run token server-side, and expose one explicit whole-run V2 Smoke Integration mode without any
mid-run fallback to Java V1.

## 27. Execution V2 P4 Java runtime-custody checkpoint - 2026-08-11

- Expanded Java-issued grants with the exact Start, Action, Refresh, Stop, and Heartbeat capabilities
  already recognized by Node. The deterministic Java/Node fixture and compact signature were updated
  together; Node independently verifies the new signed bytes.
- Added a loopback-only Java runtime client configuration. The runtime address is fixed to
  `127.0.0.1` with a bounded port and request timeout; remote hosts, credentials, paths, queries,
  fragments, redirects, and invalid ports are refused.
- Added the minimal Java HTTP transport for Reserve, Start, Action, Heartbeat, Refresh, Stop, and
  terminal Release. It requires JSON envelopes, bounds responses, preserves safe runtime codes,
  validates the exact granted run ID and canonical 256-bit token, and never logs grants, request
  bodies, tokens, endpoints, locators, or values.
- The opaque token is retained in a Java `RuntimeRun` object with no token accessor or token-bearing
  `toString`; successful Release retires it and later use fails closed. The client accepts Start and
  Action only package-internally so a future server-authorized adapter—not React—must build those
  facts.
- `mvn -DskipTests compile` compiled 574 production sources successfully with two pre-existing
  warnings. The first focused test run had one fake-transport expectation error (`Bearer` belongs to
  the production transport layer); after correcting the test, all 6 focused Java signer/client tests
  passed. The full isolated Node suite built and all 31 tests passed, including Java grant
  compatibility. `git diff --check` passed.
- Source commit/push: `c89379a4`. No frontend source or resource mirror changed. No runtime secret
  was provisioned and no Node/browser/application service, database, migration, image, or deployment
  was started or changed.

Next: add the server-authorized Smoke Integration V2 adapter that derives organization/home-banking
identity from the active workspace, freezes the existing SQL plan plus REAL/SYNTHETIC dataset and
owner/page-scoped registry candidates, and maps one explicit whole-run V2 mode to the Java-custodied
Node run. V1 remains a separate rollback mode; partial-run fallback is forbidden.

## 28. Execution V2 hash-only live-page identity checkpoint - 2026-08-11

- Added a token-authorized `GET /v2/runs/{runId}/page-identity` operation under the existing
  `runtime.action` capability. It is serialized with that run's page operations and is available
  only while the exact isolated session is READY.
- Node reads the current Playwright URL only inside the owned browser handle, converts it through
  the established Java-compatible `url-v1:SHA-256` identity, validates the result again at the HTTP
  boundary, and returns only `pageKey`. The raw banking URL is not returned or logged.
- Added Java runtime-client support that retains the opaque run token and strictly accepts only the
  hash contract. Added an owner-and-page-key registry query plus a healing preparation seam that
  revalidates the Bot Job's authoritative Home Banking owner before selecting any candidates.
- The existing URL-based Java healing path now delegates to the same exact page-key query, retaining
  backward compatibility while removing duplicate selection logic.
- `npm test` rebuilt the isolated runtime and all 32 Node tests passed. `mvn -DskipTests compile`
  compiled 574 production sources successfully with two pre-existing warnings. Focused Java tests
  `ExecutionRuntimeHttpClientTest,ScannedElementRepositoryTest,RuntimeElementHealingServiceTest`
  passed 16/16. `git diff --check` passed.
- Source commit/push: `65989d4c`. No frontend source/resource mirror changed. No Node runtime,
  browser, ARWeb service, database, migration, image, or deployment was started or changed.

Next: implement the server-authorized Smoke Integration V2 run adapter. It will freeze the current
SQL plan and REAL/SYNTHETIC data, issue and retain one run grant/token in Java, query each action's
current hash-only page identity, build authoritative Node action DTOs from the frozen instruction
and owner-scoped registry candidates, and keep V1 as an explicit whole-run rollback mode only.

## 29. Execution V2 authoritative action-facts checkpoint - 2026-08-11

- Added a Java action factory that accepts only one frozen SQL `InstructionSnapshot`, the exact
  server-prepared owner/page registry candidates, a monotonic JavaScript-safe sequence, and an
  already resolved INPUT value. React cannot supply locators, candidate rows, page identity, or
  owner scope through this seam.
- The factory maps only the frozen physical actions C/I/O to CLICK/INPUT/OUTPUT, preserves the
  established authored XPath/CSS then registry locator/canonical/client-alias tier order, dedupes
  selectors, and carries iframe/shadow/tag/name validation facts within the Node contract bounds.
- INPUT alone may carry a bounded value and the frozen Enter/Tab completion flags. CLICK/OUTPUT
  reject an attached input value. Owner mismatch, unavailable preparation, invalid sequence/tag,
  oversized selectors/names/input, and more than 100 registry candidates fail before submission.
- `mvn -Dtest=ExecutionRuntimeActionFactoryTest test` compiled 575 production and 336 test sources;
  both focused tests passed. The compiler reported only the two established warnings.
  `git diff --check` passed.
- Source commit/push: `2a3808f1`. No frontend, Node runtime, resource mirror, database, migration,
  browser, service, image, or deployment changed.

Next: add the whole-run lifecycle adapter that freezes plan/data, issues and reserves the Java-held
run, starts the isolated Node browser, obtains the current page hash for each physical instruction,
submits these authoritative action facts, and stops/releases the exact run on finish, stop,
disconnect, or failure. V1/V2 selection must be explicit at start and cannot change mid-run.

## 30. Execution V2 isolated run lifecycle checkpoint - 2026-08-11

- Added an environment-disabled Java run coordinator. Missing V2 grant configuration returns no
  coordinator and leaves Smoke Integration V1 untouched; no fallback or partial-run switch occurs.
- Start verifies current app authority (`organizationId == homeBankingId`), frozen plan owner,
  environment owner/Bot Job, and plan revision before issuing a grant or contacting Node. It maps
  only configured Chrome, Edge, or Chromium channels and starts a visible dedicated runtime page.
- Java retains the opaque runtime authority, polls bounded QUEUED/STARTING/LOADING_PAGE states for
  up to 45 seconds, and exposes the run only after READY. Terminal, invalid, or timeout states run
  best-effort exact stop/release cleanup; an unactivated reservation remains bounded by grant expiry.
- Each physical action is serialized against refresh/close, re-reads the current hash-only Node page
  identity, prepares the owner/page-scoped registry from the frozen instruction, submits the strict
  action DTO, and never exposes runtime authority to React.
- Close performs exact Stop then Release and marks the Java run closed only after both acknowledge.
  An unknown terminal outcome retains authority for an exact retry instead of guessing success.
- `mvn -Dtest=ExecutionRuntimeRunCoordinatorTest,ExecutionRuntimeActionFactoryTest test` compiled
  576 production and 337 test sources; all 4 focused tests passed with only the two established
  compiler warnings. `git diff --check` passed.
- Source commit/push: `7300b26f`. No current Smoke route was switched. No frontend/Node resource,
  database, migration, browser, process, service, image, or deployment changed.

Next: add the explicit V1/V2 start selector and connect `SmokeTestIntegrationService` to this
coordinator. The service must freeze plan plus REAL/SYNTHETIC data before issuing V2 authority,
resolve INPUT values server-side, translate Node diagnostics/output into the existing step response,
and close the exact V2 run on finish, stop, disconnect, binding change, failure, and shutdown.

## 31. Execution V2 explicit Smoke routing checkpoint - 2026-08-11

- Added a frozen whole-run `runtimeMode` to the Smoke Integration start contract. A missing field
  remains `JAVA_V1` for backward compatibility; the only explicit alternative is
  `TYPESCRIPT_PLAYWRIGHT_V2`. The selected mode is echoed in the accepted start response and cannot
  change during the run.
- Added the V2 Smoke step translator. Frozen CLICK, INPUT, and OUTPUT instructions use the isolated
  Node action path; REFRESH uses the exact isolated run; logical React-owned rows remain logical.
  INPUT values are resolved from the frozen REAL/SYNTHETIC dataset and variable slots before the
  action DTO is built. Node diagnostics, ambiguity, output, and step disposition are mapped into
  the existing correlated Smoke response without exposing runtime authority.
- Commands that have not yet been migrated to V2, including GET, SET, and ExcelWrite, fail closed
  with `V2_COMMAND_NOT_MIGRATED`. They never fall back to Java V1 inside an accepted V2 run.
- Connected `SmokeTestIntegrationService` to the environment-disabled V2 coordinator. The service
  freezes and reauthorizes the plan/data first, starts the dedicated Node browser only for explicit
  V2, retains its opaque authority server-side, and never opens or executes through the shared Java
  Playwright browser for that run.
- Finish, Stop, disconnect, Bot Job binding change, failed start, and application shutdown close the
  exact V2 run. Unknown cleanup failure retains the run for a bounded exact retry instead of
  declaring it released. V1 retains its existing shared-page behavior and terminal wording; V2
  reports that its isolated Playwright session was closed.
- Focused verification command
  `mvn -Dtest=SmokeTestIntegrationServiceTest,SmokeTestIntegrationV2StepExecutorTest,SmokeTestIntegrationContractsTest test`
  compiled 577 production and 338 test sources and passed all 12 tests with zero failures/errors.
  `git diff --check` passed.
- Source commits/pushed: selector `fa0dcc3d`, V2 step translation `1887dbe2`, and service routing
  `90789f4d`. No frontend or resource mirror changed. No runtime secret was provisioned, and no Node
  runtime, browser, ARWeb service, database, migration, image, or deployment was started or changed.

Next: migrate GET and SET without Java physical-action fallback, then add the frontend runtime-mode
control and deploy/configure the loopback Node runtime for an explicit live V2 Smoke run. ExcelWrite
and multi-Bot-Job orchestration remain later checkpoints and must use the same isolated run model.

## 32. Execution V2 GET/SET and physical-sequence checkpoint - 2026-08-11

- Migrated frozen GET and SET commands without invoking Java Playwright. GET submits an OUTPUT
  action to Node against its validated same-Block Web Element parent, then updates the existing
  run-local variable overlay and optional durable mirror. SET reads the exact `READ_SET` run-local
  slot and submits an INPUT action against that frozen parent.
- Java remains authoritative for command/parent/Block relationships, variable slot ownership, and
  runtime-variable state. Node receives only the physical parent locator facts while the request
  retains the GET/SET command instruction ID for diagnostics and at-most-once correlation.
- Empty GET output remains a valid value. Missing slots, VOID SET values, malformed Node output,
  failed producers, durable-mirror failure, invalid parents, and runtime refusal remain fail-closed.
  Successful GET continues to emit the established `runtimeUpdate` response consumed by React.
- Added a Node-physical sequence owned by each isolated Java run. React Integration sequence numbers
  include logical rows and therefore cannot be used directly by Node's consecutive action ledger.
  Physical actions now receive 1, 2, 3... even when IF/LOOP/other React-only rows occur between
  them, while React's original sequence remains the outer Smoke request correlation.
- A transport/HTTP exception after physical submission marks that isolated run's action outcome
  unknown and blocks every later action. Exact Stop/Release cleanup remains available; the system
  never guesses whether the physical action occurred or reuses its sequence for a different step.
- Focused verification command
  `mvn -Dtest=ExecutionRuntimeActionFactoryTest,ExecutionRuntimeRunCoordinatorTest,SmokeTestIntegrationV2StepExecutorTest,SmokeTestIntegrationServiceTest test`
  compiled 577 production and 338 test sources and passed all 13 tests with zero failures/errors.
  `git diff --check` passed.
- Source commit/push: `420e9f31`. No frontend/Node/resource mirror, database, migration, browser,
  service, image, or deployment changed.

Next: add the Smoke Test frontend runtime-mode control so a user can explicitly choose V1 or V2,
then configure and deploy the loopback Node runtime for the first live isolated Smoke run. ExcelWrite
remains intentionally fail-closed in V2 until its frontend-memory manager and artifact boundary are
implemented.
