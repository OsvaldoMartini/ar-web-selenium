# Claude vs Codex Migration Checks — 2026-07-12

Status: Claude and CODEX investigation passes complete; the CODEX remaining Bot Job Details controls migration is implemented, tested, React-deployed, Java-packaged, and committed. Desktop runtime validation and the explicitly unchecked follow-up tasks remain pending.

This is the canonical investigation and roadmap for aligning local Scanner TEST RUN execution with the external AR Web Engine. Future Claude and Codex reviews must update this file instead of creating parallel roadmaps.

## Review ledger

| Reviewer | Status | Evidence recorded |
|---|---|---|
| Claude | Complete for independent `executeJob()`/Engine parity pass | Independently re-derived P0-3 (confirmed the exact NPE mechanism by line) and found three items not yet in this document: Engine never calls `fixExcelGoto` (P0-3b below), Engine's Excel reader call drops the clientNamed alias map (currently inert), and a dead duplicate `ELSE` branch in Engine's `checkActionToJump`. See "Claude's independent findings" section below. No Java/Maven build was run. |
| CODEX | Complete for investigation, toolbar migration, and automated validation | Compared both execution methods and their bootstrap, data-loading, status, browser, React, and lifecycle boundaries. Migrated the remaining Bot Job Details controls to React, removed their reachable JavaFX toolbar implementation, compiled and packaged Java, and completed the verification evidence in the CODEX section below. |

## Repositories and immutable comparison points

| Project | Branch | Commit inspected | Role |
|---|---|---|---|
| `D:\Projects\AllinWeb\ar-web-selenium` | `refactor/perform-actions-decomposition` | `3cd86f87c734d0507725dcfc3be3edef1b3a1689` implementation commit | Scanner, JavaFX host, embedded Playwright execution, and React toolbar backend |
| `D:\Projects\AllinWeb\ar-web-engine` | `VERSION-4-2-NEW` | `f890e833d9aad9a8abbfb455e789e3d74c7817a5` | External Selenium engine |
| `D:\Projects\AllinWeb\abr-react-ts-grid` | `VERSION-4.6` | `30d6f331e6519c8bec9211470d1a76e6d8d74363` | React migration UI |

The Engine snapshot is older than the Scanner snapshot. Do not replace Scanner `executeJob` with the Engine method wholesale. Scanner contains newer Playwright, realtime-status, failure aggregation, `BACK`, and forward-GOTO work, while the Engine still contains behavior and reporting defects documented below.

## CODEX — Findings, Fixes, and Action Checklist (2026-07-12)

This section contains the CODEX-owned investigation, implemented fixes, remaining tasks, acceptance criteria, and deployment decision. Checkbox state reflects the current working tree: `[x]` means completed in this pass; `[ ]` means not implemented or not runtime-validated yet.

### CODEX — Completed fixes and checks

- [x] Task: Compare `EngineRunner.executeJob()` with `ARScannedElementPane.executeJob()` and trace their bootstrap, block selection, Excel-row, GOTO, reporting, browser, and shutdown behavior.
- [x] Task: Add `Execute All` as the first TEST RUN dropdown option and select it by default.
- [x] Task: Add the `ALL/ONE` toggle with default green `ALL` and orange `ONE` states.
- [x] Task: Snapshot the selected mode and pass it explicitly into `testRunBlockPlaywright`; `ALL` sets `runSingleBlock=false` and `ONE` sets it to `true`.
- [x] Task: Prevent the contradictory `Execute All + ONE` state by returning the toggle to `ALL`.
- [x] Task: Reject TEST RUN when the Bot Job has no real/loaded executable block, before `executeJob()` can dereference an empty list.
- [x] Task: Return startup/submission acceptance from `testRunBlockPlaywright`/`recallJob` so rejected load, environment, browser, or executor startup does not report a successful launch or incorrectly arm STOP.
- [x] Task: Keep the synthetic no-ID `Execute All` option out of `GenFlowService` and retain only one execution-mode owner in the Bot Job Details TEST RUN toolbar.
- [x] Task: Confirm no React change is required for the direct JavaFX-to-Java TEST RUN path and verify the React build and deployed resource trees are byte-identical (45 files, zero hash differences).
- [x] Task: Run scoped `git diff --check` on the two Java changes and this roadmap; no Java/Maven build was run per user instruction.

### Today's implementation decision

The requested control belongs to the JavaFX Bot Job Details `TEST RUN` toolbar, not to React and not to the separate Scanner Pre-Launch controls.

Implemented working-tree contract:

- `ARViewBotJobPane` prepends a synthetic first option named `Execute All`.
- The first option is selected by default.
- The `ALL/ONE` toggle defaults to `ALL`, green (`#1a6b3a`).
- `ONE` is orange (`#E67E22`).
- The toggle value is snapshotted when TEST RUN is clicked and passed as an explicit boolean to `ARScannedElementPane.testRunBlockPlaywright(...)`.
- Selecting `Execute All` while `ONE` is active forces the mode back to `ALL`, preventing the contradictory `Execute All + ONE` state.
- Toggling to `ONE` while `Execute All` is already selected also snaps back to `ALL`.
- A job with no real block is rejected in both the toolbar and embedded startup before `executeJob` can dereference an empty loaded list.
- The embedded startup method returns an acceptance boolean; a load/environment/browser startup failure no longer enables STOP or logs a successful launch.
- The synthetic option has no database ID, so the shared GEN FLOW dropdown cannot pass it to `GenFlowService`.
- No second toggle is retained in `ARScannedElementPane` Pre-Launch. When more than one block is loaded, that surface already adds an `Execute All Blocks` option through `PerformLists.loadComboOptions("ScannerPane")`; a second mode owner would create divergent state.

Code locations in the current working tree:

- `ARViewBotJobPane.java:116-123` — labels and colors.
- `ARViewBotJobPane.java:370-478` — mode snapshot, validation, launch acknowledgement, and TEST RUN call.
- `ARViewBotJobPane.java:500-549` — sentinel/default selection and visual state.
- `ARViewBotJobPane.java:790-818` — dropdown rendering and default toggle creation.
- `ARViewBotJobPane.java:1001-1011` — toolbar placement.
- `ARScannedElementPane.java:3249-3408` — explicit execution-mode parameter and startup/submission acknowledgement.
- `ARScannedElementPane.java:6555-6562` — existing one-block stop checkpoint.

#### Exact current UI truth table

| Dropdown | Toggle | `runSingleBlock` | Current result |
|---|---:|---:|---|
| `Execute All` | `ALL` | `false` | Start at loaded index 0 and continue through the job. |
| Numbered block N | `ALL` | `false` | Start at N and continue through all remaining blocks. This is “from selected onward,” not block 1 onward. |
| Numbered block N | `ONE` | `true` | Start at N and stop at the existing single-block checkpoint. Caveats below still apply. |
| `Execute All` | `ONE` | N/A | Prevented by forcing the toggle back to `ALL`. |

Recommended future internal names are `FULL`, `FROM_SELECTED`, and `ONE`, even if the UI remains the requested two-state `ALL/ONE` toggle. This makes the distinction between the first two rows explicit.

### Execution entry points

#### External Engine

Execution path:

1. `Engine.main(String[])`
2. `EngineRunner.run(String[])`
3. `EngineRunner.startParametersInterpreter(String[])`
4. `EngineRunner.initializeWebDriver()`
5. `EngineRunner.recallJob()`
6. `EngineRunner.executeJob()`

Effective CLI contract:

```text
execute/j <homeBankId> <botJobId> <blockOrderNumber> <excelPath> [-c <configPath>]
```

The third value is logged as “Block Id” but is actually parsed as `blockOrderNumber - 1` and used as a list index (`EngineRunner.java:176-185`). Values `0`, negative, or nonnumeric start at index 0. A positive N starts at index N-1 and continues through the remaining blocks. The Engine has no `runSingleBlock` request field.

The Engine's `README-DEBUG.md` example omits this block argument and is stale. Scanner external Launch/BAT currently passes `1`, relying on “start at first and continue.”

#### Embedded Scanner TEST RUN

`ARViewBotJobPane` directly calls `ARScannedElementPane.testRunBlockPlaywright(...)`. The Scanner loads the complete job and Excel context, opens one Playwright browser, and submits the same large `executeJob` loop asynchronously. Scanner adds `runSingleBlock` as local mutable state and consumes it after a normally completed block.

There is no desktop TEST RUN WebSocket contract and no React callback in this path.

### Engine versus Scanner parity matrix

| Area | External Engine | Embedded Scanner | Decision |
|---|---|---|---|
| Start selector | CLI order converted to list index | Dropdown order converted to list index | Replace both with stable block identity resolution. |
| ALL behavior | Selected index through last block | Same when `runSingleBlock=false` | Preserve, but name it `FROM_SELECTED` internally. |
| ONE behavior | Not supported | Stop flag at normal block-loop tail | Define strict scope before porting to Engine. |
| Excel rows | First row starts selected; later rows use selected/default or EXCEL GOTO | Same until ONE sets `stopAll` | Decide whether ONE means row 1 only or selected block for every row. |
| Empty blocks | Excluded by `JOIN instruction` | Same query shape | P0: order/index can address the wrong loaded block. |
| Inactive selected block | Skip to next block | Skip occurs before ONE checkpoint | P0: ONE can execute a later active block. |
| GOTO | Legacy bounded-loop behavior | Adds forward-target routing, currently defective | P0 integration fix and characterization required. |
| Loops/conditions | Legacy nested control flow | Substantially copied control flow | Golden fixtures required; static similarity is not parity proof. |
| Browser | Selenium WebDriver | Playwright-only TEST RUN path, with Selenium branches guarded | Keep browser-specific behavior behind an adapter. |
| Wait initialization | Assumes a Selenium driver | Lazily skips Selenium waits without a driver | Intentional Scanner difference. |
| Element action gate | Requires `WebElement` | Allows Playwright action without `WebElement` | Intentional Scanner difference. |
| Action constants referenced | 33 shared constants | Same set plus `BACK` | Preserve Scanner addition; verify behavior, not only names. |
| Status destination | Legacy `engine-perform-bot-job` JSON | `botJobTasks` plus `InstructionRealtimePublisher` | Normalize an event contract, retain adapters. |
| Final success | Branches on last mutable `success` | Uses aggregate `anyFailure` | Port aggregate result semantics to Engine. |
| Process result | `recallJob()` exits 0 after `executeJob()` | In-process UI state | Engine must expose a machine-readable result/nonzero failure exit. |
| Completion UI | Legacy modal and driver quit rules | Scanner modal/connection behavior | Keep presentation outside the core executor. |

### Confirmed defects and ambiguous contracts

#### P0-1 — block order is being used as a list index

Both projects order by `block_order_number`, but `PerformDBEngine.loadCompleteJobs` uses an inner `JOIN instruction`. A block with zero instructions disappears from the loaded list. Noncontiguous orders, deleted blocks, or empty blocks therefore make `order - 1` point to the wrong block or outside the list.

Required correction:

- Change the load to retain block metadata even when a block has zero instructions.
- Carry `startBlockId` as the authoritative identity.
- Resolve that ID to a loaded block after load.
- Retain order only for display and deterministic sorting.
- Return a defined `EMPTY` result for a valid empty block in ONE; in ALL/FROM_SELECTED, treat it as a no-op and continue.
- Reject a genuinely missing/deleted `startBlockId` before opening the browser.
- Never report success when no instruction/block was executed.

#### P0-2 — ONE is not currently a strict one-block boundary

The stop check runs only at the natural bottom of the block loop.

- An inactive selected block increments and continues before the check, so a later block may execute.
- GOTO can `continue blockLoop` before the check and escape to another block.
- `stopAll=true` ends the entire data loop, so only the first Excel row is processed.
- A thrown exception can bypass the one-shot consumption point; later full Launch currently relies on a separate reset.

Product decision required:

1. Strict physical block: never enter another block; a cross-block GOTO is reported as a scope violation.
2. Logical flow: allow GOTO dependencies, despite the label ONE.
3. Row policy: execute the selected block for only row 1 or once per Excel row.

Recommended default: strict physical block plus an explicit `FIRST_ROW` policy for today's TEST RUN. If users need dependency traversal or every data row, expose those as separate named modes instead of silently broadening ONE.

#### P0-3 — forward GOTO is broken

The Engine treats GOTO as a bounded loop. With a count of 1 it can decrement to zero without entering the forward target.

Scanner commit `e9c68e7b` attempted to treat forward GOTO as a branch. Current code detects a forward target without adding a `mapLoops` entry (`ARScannedElementPane.java:5006-5026`), but the special forward handler is nested inside the `LOOP` action branch (`5122-5151`) and is unreachable for GOTO. Control later dereferences the missing `mapLoops` entry (`5256-5271`), creating an NPE risk.

`GotoExecutionRoutingTest` only verifies target-index parsing. It does not execute the integrated control flow.

#### P0-4 — invalid start can report false success

An order beyond the loaded list can skip the block loop while leaving `resultActions` as “No instruction executed yet.” Completion can still be reported as successful, and final labels use `blocksLoaded.get(0)` rather than the selected block.

#### P0-5 — Engine result reporting is unreliable

Engine tracks `anyFailure` but final completion branches on the last mutable `success` value (`EngineRunner.java:2844`). A later success can mask an earlier failure. `recallJob()` then ignores the boolean return and exits with code 0 (`EngineRunner.java:348-354`).

#### P1-1 — ONE still loads and validates the complete job

Both paths load all blocks, variables, actions, and Excel schema before applying the selected start. A supposedly isolated block can therefore fail because of unrelated job data, or fail because it depends on navigation established by earlier blocks.

#### P1-2 — asynchronous UI lifecycle is still only a launch acknowledgement

The JavaFX worker now distinguishes rejected startup from accepted submission, but it still returns after scheduling `executeJob`, not after execution finishes. TEST RUN is re-enabled and STOP is armed before a terminal execution result exists. A user can change the visual mode while the captured run continues under the earlier mode.

#### P1-3 — model and status drift

- Scanner `HomeUrlDTO` includes environment `name`; Engine does not.
- Scanner `loadHomeUrls` selects/defaults `hu.name`; Engine omits it.
- Scanner extracted graph helpers into `InstructionGraph`; Engine still relies on `PerformActions` helpers.
- Status destinations and payload shapes differ.

### React boundary and future integration risks

No React change is required for today's JavaFX TEST RUN toggle.

Current React `GridItemScannMobile` observations:

- `handleLaunchBotJobClick` sends `LAUNCH_BOT_JOB_TEST` without selected block, block order, or `runSingleBlock`.
- The visible `selectedBlock` is also the destination for scanner insertion operations. Reusing `null` or an Execute All sentinel for execution would break Insert All and per-row actions.
- `BlockData` contains ID/name/job ID but not block order.
- Block filtering uses only `selectedJob?.id`, while other code accepts `id ?? botJobId`; payloads that only contain `botJobId` can show an empty block list.

If TEST RUN is deliberately migrated to React later:

- Add a separate execution-scope/start-block control; do not reuse the scanner destination selector.
- Normalize `selectedJobId = selectedJob?.id ?? selectedJob?.botJobId` once.
- Send stable `startBlockId`, explicit `executionScope`, row policy, and GOTO policy.
- Add a backend acknowledgement and terminal event before allowing another run.

### Unique migration roadmap

#### Phase 0 — freeze the contract and capture golden evidence

- [ ] Approve the UI truth table in this document.
- [ ] Decide ONE row policy: `FIRST_ROW` or `ALL_ROWS`.
- [ ] Decide ONE GOTO policy: strict boundary or logical-flow escape.
- [ ] Build a fixture job with at least four blocks: active, inactive, empty, and active target.
- [ ] Include contiguous and noncontiguous `block_order_number` values.
- [ ] Include two Excel rows, with and without EXCEL GOTO.
- [ ] Capture ordered execution events from Engine and Scanner for the same fixtures.
- [ ] Record browser URL, block ID/order, instruction ID/action, row index, status, and terminal result.

Exit criterion: expected behavior is written independently of either current implementation.

#### Phase 1 — correctness hotfixes before extraction

- [ ] Fix forward GOTO in Scanner at the GOTO handling level, not inside LOOP handling.
- [ ] Add integrated forward/backward GOTO tests with counts 1 and 2.
- [ ] Load valid empty blocks and distinguish their defined `EMPTY` outcome from a missing/deleted start block.
- [ ] Reject missing/out-of-range start blocks before browser startup.
- [ ] Make ONE stop behavior run at every block exit path: active completion, inactive skip, GOTO, STOP, and exception.
- [ ] Reset all per-run flags in one `finally` path.
- [ ] Change Engine terminal success to aggregate failures.
- [ ] Return a nonzero Engine process result or write a machine-readable terminal result.

Exit criterion: no false-success, wrong-target, forward-GOTO NPE, or mode-leak fixture remains.

#### Phase 2 — introduce an immutable execution request/result

Replace global mode fields and positional arguments with a per-run context similar to:

```text
ExecutionRequest
  homeBankingId
  botJobId
  startBlockId
  executionScope = FULL | FROM_SELECTED | ONE
  excelRowPolicy = FIRST_ROW | ALL_ROWS
  crossBlockGotoPolicy = ALLOW | REJECT
  endpointUrl
  excelPath

ExecutionResult
  status = PASSED | EMPTY | FAILED | INTERRUPTED | REJECTED
  blocksVisited
  instructionsExecuted
  rowsProcessed
  firstFailure
  startedAt / finishedAt
```

- [ ] Resolve `startBlockId` after loading and retain order only as metadata.
- [ ] Make scope/row/GOTO policy final for the lifetime of the run.
- [ ] Remove `runSingleBlock` as cross-run mutable policy after callers migrate.
- [ ] Emit one terminal result exactly once.

#### Phase 3 — create one canonical execution core

Do not continue copy-editing the two 2,000+ line methods.

- [ ] Extract block/row/control-flow orchestration behind narrow ports.
- [ ] Define ports for browser actions, element resolution, reporting, status events, persistence reads, prompts/modals, and lifecycle shutdown.
- [ ] Keep Selenium and Playwright objects outside the domain control-flow state.
- [ ] Move GOTO/LOOP/condition transitions into testable pure functions.
- [ ] Run the same fixture suite against the canonical core.

Exit criterion: Engine and Scanner no longer own independent copies of traversal policy.

#### Phase 4 — adapt both runtimes

- [ ] Engine adapter: Selenium locator/action, legacy CLI compatibility, nonzero result, status adapter.
- [ ] Scanner adapter: Playwright locator/action, realtime publisher, JavaFX result binding.
- [ ] Align `HomeUrlDTO` and environment-name loading.
- [ ] Preserve Scanner-only `BACK` and Playwright navigation stabilization through capabilities, not forked traversal.
- [ ] Deprecate the misleading Engine “Block Id” log and stale README example.

#### Phase 5 — lifecycle and UI completion

- [x] Keep TEST RUN disabled until the exact owned scanner execution reaches a terminal event.
- [x] Keep STOP enabled only while the run is active, including cancellable pre-ID startup.
- [ ] Show captured mode/start block in the live status.
- [ ] Prevent job/block refresh from mutating the active request.
- [ ] If React migration is approved, implement the separate execution selector and typed request described above.

#### Phase 6 — cross-runtime acceptance

- [ ] Run the acceptance matrix below in both adapters.
- [ ] Compare ordered events and terminal results, allowing only documented browser/status differences.
- [ ] Run a complete job after every ONE/STOP/failure scenario to prove no state leaked.
- [ ] Update this file with evidence, commit IDs, and remaining accepted differences.

### Acceptance matrix

| Case | Required assertion |
|---|---|
| One active block, Execute All + ALL | Block executes and terminal result is correct. |
| N blocks, Execute All + ALL | Every active block executes in order. |
| Middle block + ALL | Only middle through last execute. |
| First/middle/last + ONE | Exactly the approved ONE scope executes. |
| Execute All selected while toggling ONE | UI returns to ALL; no contradictory request is sent. |
| Inactive selected block + ONE | Does not silently execute the next active block. |
| Empty selected block | Stable ID is resolved and a defined empty/no-op result is returned. |
| Noncontiguous order values | Correct stable block is selected. |
| Order beyond loaded blocks | Request is rejected before browser startup. |
| Two Excel rows | Matches approved row policy. |
| EXCEL GOTO on later row | Starts at the documented target without escaping scope unexpectedly. |
| Forward GOTO count 1/2 | Target traversal and repeat counts are correct; no NPE. |
| Backward GOTO count 1/2 | Loop count and termination are correct. |
| ONE + cross-block GOTO | The approved strict/logical scope policy is enforced before any `continue blockLoop`. |
| Missing GOTO target | Deterministic failed result, no false success. |
| Earlier failure followed by success | Final result remains failed in both runtimes. |
| Failing external Engine fixture | Produces a nonzero process exit or the approved machine-readable failed terminal result. |
| STOP during ONE/ALL | Interrupted terminal result emitted once; browser closes safely. |
| Exception during ONE | Flags reset in `finally`; next ALL run is complete. |
| Successful ONE then ALL | ALL runs its complete approved range; no one-shot state leaks. |
| Successful ALL then ONE | ONE stops at its approved boundary; no prior mode state leaks. |
| Refresh/reorder/delete after selection | Active request remains immutable; next request resolves stable ID. |

### Validation and deployment record for this pass

- Static source comparison: complete.
- Target Java files and this roadmap pass scoped `git diff --check -- <paths>` validation. Full-worktree `git diff --check` is not clean because the pre-existing user change in `dependency-reduced-pom.xml:11` contains trailing whitespace.
- Java/Maven build/package: intentionally not run per user instruction.
- React source change: none.
- React repository commit/push: not triggered.
- React `build` versus deployed `ar-web-selenium/src/main/resources/build`: 45 files versus 45 files, SHA-256 comparison found zero differences before this pass.
- React build/clean-copy: not triggered because React did not change and deployed artifacts were already identical.
- This roadmap is currently untracked and therefore not yet versioned. No backend files are staged; the pre-existing untracked guidance file and PNG remain user-owned and untouched.

For a future React modification, preserve the user's required sequence:

1. Commit and push `abr-react-ts-grid` with prefix `CLAUDE...<details>` or `CODEX...<details>` according to the author.
2. Run `npm run build` in `D:\Projects\AllinWeb\abr-react-ts-grid`.
3. Delete every existing file under `D:\Projects\AllinWeb\ar-web-selenium\src\main\resources\build`.
4. Copy the complete React `build` directory contents into that destination.
5. Compare file manifests/hashes to prevent dead artifacts.
6. Review `git status --short -- src/main/resources/build` in `ar-web-selenium`; those deployed artifacts are tracked and can create additions/deletions in the backend repository.
7. Treat the backend artifact copy as local-only unless the user separately authorizes a backend commit/push.
8. Do not compile or package the Java backend for this task; runtime validation is performed by the user.

### CODEX — Remaining action checklist

The checklist below is the CODEX task-status view. Detailed evidence and acceptance criteria remain in the CODEX findings and phased roadmap above.

#### Product decisions required

- [ ] Task: Decide whether ONE is a strict physical-block boundary or may follow a cross-block GOTO.
- [ ] Task: Decide whether ONE processes `FIRST_ROW` only or repeats the selected scope for `ALL_ROWS`.
- [ ] Task: Confirm the internal three-scope contract: `FULL`, `FROM_SELECTED`, and `ONE`, while retaining the requested two-state UI.
- [ ] Task: Define the expected result for a valid empty block (`EMPTY`/no-op) separately from a missing or deleted block (`REJECTED`).

#### Critical execution fixes

- [ ] Task: Replace `blockOrderNumber - 1` list indexing with stable `startBlockId` resolution and retain valid empty block metadata during job loading.
- [ ] Task: Enforce ONE at every block-exit path, including inactive-block skip, cross-block GOTO, STOP, exception, and multi-row iteration.
- [ ] Task: Repair Scanner forward-GOTO dispatch so it cannot dereference a missing `mapLoops` entry, then add a real integrated execution test.
- [ ] Task: Port the verified forward-GOTO behavior into the external Engine without copying the broken Scanner implementation.
- [ ] Task: Reject missing/out-of-range starts before browser startup and prevent “No instruction executed yet” from becoming a false success.
- [ ] Task: Make Engine final status aggregate all failures and expose a nonzero process exit or machine-readable failed result.
- [ ] Task: Reset all run-scoped state in one guaranteed terminal/finally path and prove `ONE -> ALL` and `ALL -> ONE` do not leak mode.

#### Production-focused Playwright test track

- [ ] Task: Audit `D:\Projects\ARWebBancaStato\ARWeb\database.db` and `D:\Projects\ARWebBancaStato\Config-4.2\ARWeb.config` read-only, recording only schema/contract information and redacting sensitive values.
- [ ] Task: Create isolated copied database/config fixtures; never point a mutating test at the live `D:\Projects\ARWebBancaStato` files.
- [ ] Task: Prefer Playwright for browser-visible behavior and use Mockito/mocks only for Java boundaries that cannot be exercised safely through the browser.
- [ ] Task: Add Playwright coverage for the complete ALL/ONE truth table, no-block rejection, startup rejection, STOP lifecycle, mode leakage, block refresh/reorder, and terminal status.
- [ ] Task: Add fixture coverage for active, inactive, empty, missing, and noncontiguous blocks; two Excel rows; EXCEL GOTO; forward/backward/missing GOTO; earlier failure followed by success.
- [ ] Task: Provide a localhost-accessible test surface if the current UI cannot expose deterministic test controls without touching production data.
- [ ] Task: Run the Playwright suite against isolated fixtures, record commands/results here, and keep the production directory unchanged.

#### Canonical executor and lifecycle migration

- [ ] Task: Introduce immutable `ExecutionRequest`/`ExecutionResult` objects with stable block ID, scope, row policy, GOTO policy, and exactly one terminal result.
- [ ] Task: Extract one canonical control-flow executor with browser, persistence, reporting, prompt, and lifecycle ports.
- [ ] Task: Adapt Selenium Engine and Playwright Scanner runtimes to the same canonical executor and align Home URL/status contracts.
- [x] Task: Keep TEST RUN and STOP states bound to accepted submission and terminal events rather than a background-task launch acknowledgement. `BotJobTestRunCoordinator` now owns startup cancellation, exact scanner execution IDs, STOP delivery/retry, completion monitoring, and acknowledged terminal publication.
- [ ] Task: Execute the full acceptance matrix against both runtime adapters and record accepted differences.

#### Conditional React delivery

The page-by-page JavaFX-to-React sequence is tracked in
[`ROADMAP_REMAINING_LEGACY_PANELS_REACT_2026_07_12.md`](ROADMAP_REMAINING_LEGACY_PANELS_REACT_2026_07_12.md).

- [ ] Task: If a React test page is necessary, keep it isolated from scanner destination state and use typed, stable test-fixture inputs.
- [x] Task: If React changes, commit/push `abr-react-ts-grid` with a `CODEX...<details>` commit, run `npm run build`, clean the deployed build directory, copy the complete build, and verify manifests/hashes. First slice: `c3e077a`. Typed metadata/state continuation: `a06619e` plus license-recovery follow-up `88eac85`, pushed to `origin/VERSION-4.6`; the post-push build contained 45 files and matched the deployed backend resource tree by relative path, length, and SHA-256 with zero differences.
- [x] Task: Obtain separate authorization before committing or pushing tracked deployed artifacts in `ar-web-selenium`. Authorization was received in the follow-up request to package, commit, and push Java.

#### CODEX — Bot Job Details migration continuation (2026-07-12)

- [x] Task: Add transport-bound, request-correlated `botJobDetails.bootstrap`, `botJobDetails.state`, metadata-update, environment-refresh, and workspace-action contracts.
- [x] Task: Add an allowlisted direct database projection for Bot Job identity, metadata, environments, blocks, navigation time, execution state, and license-aware capabilities without mutating shared `PerformLists` caches.
- [x] Task: Add optimistic revision checks, bounded request replay/idempotency, request-fingerprint conflict detection, and an atomic metadata persistence/revision transition.
- [x] Task: Reject wrong-session, wrong-job, malformed, stale, nested cross-job, mismatched-action, and mismatched-operation responses before React state is changed.
- [x] Task: Add bootstrap timeout/retry, one-socket cleanup/reconnect behavior, edit-base revision preservation, committed-save/desktop-sync handling, and immediate license revoke/restore capability gating.
- [x] Task: Implement `BotJobDetailsChrome` and `BotJobMetadataEditor` with separate `.module.scss` files and mount them in Bot Job and Pre Scan while preserving the compact Components header.
- [x] Task: Remove the reachable JavaFX Bot Job identity, Edit/Save, environment selector, refresh-environments, and legacy Organizations buttons after their React replacements were mounted.
- [x] Task: Add React Organization Advanced fields for Priority, Search Config, and WebDriver Options, reuse the existing backend defaults, and redirect Bot Job Details to the React Organization Manager.
- [x] Task: Pass 9 focused React suites / 23 tests, pass the optimized production build, push final frontend head `88eac85272819711c156b77c88c88b03850f60f9`, clean-deploy 45 files, and verify zero manifest/hash differences.
- [x] Task: Complete the initial static Java source audits and scoped whitespace/dangling-control checks without compilation, then—after explicit user authorization—compile 296 main and 61 test sources, pass 43 targeted Java tests, and build the shaded executable JAR.
- [x] Task: Validate `target/AR_Web_Scanner-4.2.jar`: 384,155,105 bytes; SHA-256 `AC097EE3F2A4043CFEBC0E7E4287084D6A52E3300357901F56F80F3A42333F02`; main class `com.allinweb.ch.ARControlPanel`; migrated Bot Job Details, ALL/ONE, and React `main.b93e8594.js` entries present.
- [ ] Task: Gate or rename the existing interactive headed Playwright `*Test` classes so an unattended default `mvn clean package` cannot hang in `GotoCommandEditorPlaywrightTest`; the successful package used `-DskipTests` only after the 43 deterministic migration tests passed and all test sources compiled.
- [ ] Task: Runtime-validate the new Java WebSocket/service/JavaFX-host integration in the user-run backend, including metadata save, revision conflict, environment refresh, Organizations launch, license revoke/restore, Close without a license, and reopen/rebootstrap.
- [x] Task: Add and run headless localhost Playwright coverage with an isolated mock socket and no production BancaStato database/config access. Real-backend/production-fixture integration remains user-owned runtime validation.
- [x] Task: Complete Phase 2B by migrating block selection, reload, ALL/ONE, TEST RUN, STOP, and live execution state out of JavaFX into the separated `BotJobExecutionControls` component and `.module.scss`.
- [x] Task: Complete Phase 2C by migrating Navigation Time, Excel/Report, Export/Import, path/BAT, and Launch through typed native-desktop ports and separated React components.
- [x] Task: Obtain separate authorization before committing or pushing the backend source, roadmaps, and deployed resource artifacts. Authorization was received in the follow-up request to package, commit, and push Java.

## Claude's independent findings (2026-07-12)

This section is Claude's independent pass, run without prior knowledge of the CODEX section above
(it was written first, then discovered CODEX had already populated this file). It corroborates,
refines, and adds to the CODEX findings rather than replacing them. Per this file's own contract,
nothing above has been deleted or rewritten.

### Scope

Compared `executeJob()` line-by-line between `ar-web-selenium\...\ARScannedElementPane.java` and
`ar-web-engine\...\runner\EngineRunner.java` (both confirmed to be a verbatim copy of a common
ancestor — identical variable names, identical comments, identical typos), plus the shared helper
methods each loop calls in both repos' `PerformActions.java`/`PerformDBEngine.java` and the
Scanner-only `InstructionGraph`/`ExecutionReporter`/`EngineDialogs` extraction classes. Focus: block
loop bounds, IF/ELSE/ENDIF jump semantics, GOTO/LOOP handling, Excel/CSV row iteration and GOTO
fix-up, single-block/stop conditions, variable loading, and error propagation.

### Findings

#### C-1 (corroborates and sharpens CODEX P0-3) — the forward-GOTO fix throws an NPE before it can ever help; Engine has nothing at all (Critical)

Independently traced the exact crash CODEX's P0-3 describes, with current line numbers (file has
shifted slightly since the CODEX pass; re-verified against the working tree just now):

- `ARScannedElementPane.java:5019-5046` — the `GOTO` action-dispatch branch. When the target is
  ahead of `currentBlockOrder` (`InstructionGraph.gotoTargetIndex(msgInstruction) >
  currentBlockOrder`, lines 5027-5033), it sets `jumpGoto = true; forwardGoto = true;` but —
  unlike the backward-loop branch immediately below it (lines 5034-5039, which calls
  `mapLoops.put(msgInstruction.getKey(), ...)`) — it **never populates `mapLoops` for this key**.
- `ARScannedElementPane.java:5270-5283` — immediately afterward, in the same instruction/iteration,
  the shared `if (jumpGoto) { ... }` block runs unconditionally (it does not check `forwardGoto`)
  and executes `int repeat = mapLoops.get(msgInstruction.getKey()) - 1;` at line 5283. Since the
  forward branch never put this key into `mapLoops`, `mapLoops.get(...)` returns `null`, and
  auto-unboxing `null` in `null - 1` throws a `NullPointerException`.
- This happens on the **very first** execution of any forward GOTO in a given Excel-row pass
  (`mapLoops` is cleared per row at the top of the outer loop, so there is no way for the key to
  already be present). The `forwardGoto` redirect that was apparently intended to be the payoff of
  this fix (`ARScannedElementPane.java:5148-5169`, inside the `LOOP` action's
  `mapLoops.containsKey(parentFieldLoop)` branch) is therefore unreachable in practice — execution
  never gets there because it crashes one step earlier.

This means: forward GOTO is currently broken **in the Scanner itself**, independent of any Engine
comparison — CODEX's P0-3 is confirmed accurate down to the exact line and exception. My initial
read (before tracing this far) assumed the Scanner's fix worked and that the only gap was the
Engine having zero equivalent code (`InstructionGraph`, `gotoTargetIndex`, and `forwardGoto` do not
exist anywhere in the `ar-web-engine` repo — verified by grep across the whole repo, zero matches).
Both things are true and both matter: (1) the Scanner-side fix needs a real repair, not just a
port, and (2) once repaired, that repair still needs to be ported to `EngineRunner.java` or a
BotJob using forward GOTO will replay correctly in Scanner TEST RUN/Launch but incorrectly (or not
at all, pre-repair; via the old bounded-loop semantics, post-repair) when run standalone by the
Engine in production.

**Recommendation:** treat this as one combined fix, not two — repair the Scanner's `jumpGoto`
dispatch so the forward-branch case either populates `mapLoops` with a sentinel/loop-count-1 entry
before falling into the shared `if (jumpGoto)` block, or short-circuits that block entirely for
`forwardGoto`, and add an integrated test that actually executes a forward GOTO (not just
`GotoExecutionRoutingTest`, which per CODEX's P0-3 only tests index parsing, not the integrated
control flow). Then port whatever the corrected shape turns out to be into the Engine.

#### C-2 (new) — Engine never calls `fixExcelGoto`; a corrupted Excel-GOTO row is never persisted-repaired outside the Scanner (Medium)

**Where:** Scanner's `launchBotJobButton` (lines 2651-2662) and `testRunBlockPlaywright` (lines
3322-3332) both check whether the loaded Excel-GOTO instruction's `parentBlockId` is `null` or
`<= 0` and, if so, call `performDBEngine.fixExcelGoto("instruction", currentBotJob.getId(),
excelDataGoto.get(0).getId(), excelDataGoto.get(0).getBlockId())` — a **persistent**
`UPDATE instruction SET parent_block_id = ? WHERE id = ? AND bot_job_id = ?` — then reload
`excelDataGoto`. Engine's `startParametersInterpreter` (`EngineRunner.java:188-196`) loads
`excelDataGoto` via `loadExcelGotoBlock` but never checks `getParentBlockId()` and never calls
`fixExcelGoto`. Grepped the whole Engine repo: the only hit for `fixExcelGoto` is the unused method
definition itself in `PerformDBEngine.java:657` — it is dead code on the Engine side.

**Why it matters:** the identical in-loop fallback (`blockExcelGoto` defaulting to the GOTO's own
block or `1` when the parent lookup fails — `ARScannedElementPane.java:4456-4474` /
`EngineRunner.java:707-725`, verified byte-identical) makes a single run behave the same either
way, but only the Scanner permanently fixes the underlying DB row. A BotJob with a legacy/corrupted
Excel-GOTO `parent_block_id` that is run via the standalone Engine before any Scanner session ever
Launches/TEST RUNs it will keep silently falling back to the default restart position on **every**
production run, indefinitely, instead of the intended target block.

**Recommendation:** port the same null/`<=0` check + `fixExcelGoto` + reload sequence into
`EngineRunner.startParametersInterpreter`, ahead of `executeJob()`.

#### C-3 (new) — Engine's Excel reader call drops the clientNamed alias map (Low, currently inert)

**Where:** Scanner calls `excelReader.extractData(excelPath, performLists.getAllActions(),
ExcelUtils.buildAliasMap(performLists.getListBlock()))` in both entry points; Engine calls the
2-argument overload (`EngineRunner.java:247`), which defaults the alias map to
`Collections.emptyMap()` (`ExcelReader.java:38-40`, otherwise byte-identical file in both repos).
The alias map only affects the "missing fields" check (`ExcelReader.java:156-188`), which sets
`ExtractedData.missingFields` — and nothing in either repo ever calls `getMissingFields()` except
its own getter, so this is inert today. Flagging as a latent gap: if `missingFields` is ever wired
to something user-facing, the Engine will start flagging every renamed (`clientNamed`) Excel column
as missing while the Scanner won't.

**Recommendation:** either thread the alias map through Engine's call for future-proofing, or
remove `missingFields`/`getMissingFields()` as dead code from both repos.

#### C-4 (new) — dead duplicate `ELSE` branch in Engine's `PerformActions.checkActionToJump` (Informational)

**Where:** `ar-web-engine\...\facade\PerformActions.java:3733-3755` has three `else if` branches —
`ELSEIF`, `ELSE`, then a second, byte-identical `else if (action.equalsIgnoreCase(ARConstantsEngine.ELSE))`
(lines 3749-3752) that can never be reached. The Scanner's equivalent
(`InstructionGraph.checkActionToJump`, `InstructionGraph.java:311-328`) has only the two correct
branches. Purely cosmetic copy-paste residue with no behavioral effect; worth deleting next time
that file is touched.

### Non-issues checked (Claude pass)

Verified byte-identical between the two repos, beyond what CODEX already covered: block-loop bounds
and bootstrap (`currentBlockOrder`/`blockExcelGoto`/`blockRecall`/`firstRound`, Scanner
4448-4728 vs Engine 699-984); the IF/ELSEIF/ELSE/ENDIF jump-search block including
`searchMapConditional`, `getConditionIndexMapByParentId`, `checkActionToJump`'s two live branches,
and the IF_FAILED/ELSEIF_FAILED/ELSE_FAILED search (Scanner ~6479-6547 vs Engine ~2724-2793);
`updateProgressSuccess` (Scanner delegates to the extracted `ExecutionReporter`, Engine keeps it
inline, same logic); `NEXT_ROW` handling and the `xExcelCurrentRow` bounds clamp; `saveExcelWrite`
(Scanner 8578-8601 vs Engine 3208-3231); `loadAllVariables` in `PerformDBEngine.java` (full method
body diffed, identical SQL and result-set handling); `blockGotoFailed`/`actionResultMessage`
(Scanner delegates to the extracted `EngineDialogs`, same text/log calls as Engine's inline copy —
confirms the ongoing `PerformActions` decomposition into `facade/actions/*` has been a pure
mechanical extraction so far, at least for the pieces checked). Also confirmed three recent Scanner
commits are Scanner-only and out of scope for Engine parity: "Always write at least one data row on
Excel generation" (`ExcelUtils.java`, a GEN FLOW/template-regeneration utility that doesn't exist in
the Engine repo at all — Engine only reads an already-generated file, never regenerates one);
"Evict deleted block from nested memory list on DELETE_BLOCK" (`DELETE_BLOCK` does not appear
anywhere in the Engine repo — it's an interactive BotJob-editing concern the Engine, which only
replays saved jobs, has no analog for); and the two scanning/DOM-recording commits ("Scan icon-only
clickables...", "Exclude hidden framework inputs...", both confined to
`PlaywrightElementScanner.java`, which the Engine has no counterpart for since it never scans new
elements). Also confirms CODEX's note that the Selenium-vs-Playwright split in the low-level
element-interaction helpers is the known, tracked, intentional migration seam, not silent drift.

### Open questions (Claude pass, in addition to CODEX's Phase 0 decisions above)

- Given C-1, should the forward-GOTO feature be pulled/flagged as non-functional until repaired
  (it currently throws, it doesn't just misbehave), rather than scheduled as a normal port task?
  Worth confirming no real BotJob has been authored with a forward GOTO yet.
- Is the missing `fixExcelGoto` call in the Engine (C-2) intentional — i.e., is the Engine only
  ever expected to run against BotJobs a Scanner session has already "blessed" at least once (so
  the DB-level repair always happens Scanner-side first in practice)? If Engine-first/only
  execution is a supported scenario, C-2 should be ported.
- Should `ExtractedData.getMissingFields()` (C-3) be wired up to anything user-facing, or is it
  safe to delete as dead code from both repos?

## Claude — Action Checklist (2026-07-12)

Task-list view of the findings above (C-1 through C-4 in "Claude's independent findings"). This
section is for tracking; the prose evidence lives in that section and is not repeated here. Check
items off as they land, and note the commit ID inline when you do.

### Blocked on product decisions — answer before any Phase 1 work starts

- [ ] Task: Decide ONE's cross-block GOTO scope — strict physical boundary (a GOTO leaving the
      block is rejected as a scope violation) vs logical-flow escape (GOTO is followed even in ONE
      mode, despite the name). Blocks the Critical fix below from having a defined target behavior.
- [ ] Task: Decide ONE's Excel row policy — `FIRST_ROW` only vs `ALL_ROWS`.

### Critical — forward GOTO NPE (C-1, corroborates CODEX P0-3)

- [ ] Task: Repair `ARScannedElementPane.java`'s forward-GOTO dispatch (~lines 5019-5046) so the
      forward branch either populates `mapLoops` with a sentinel/loop-count-1 entry before falling
      into the shared `if (jumpGoto)` block (~5270-5283), or short-circuits that block entirely for
      `forwardGoto`. Today this is a guaranteed `NullPointerException` on the first forward GOTO of
      any Excel-row pass, not a misbehavior.
- [ ] Task: Add an integrated test that actually executes a forward GOTO end-to-end — the existing
      `GotoExecutionRoutingTest` only checks index parsing, not the integrated control flow.
- [ ] Task: Port the corrected forward-GOTO shape into `EngineRunner.java` once fixed — it currently
      has zero equivalent code (`InstructionGraph`, `gotoTargetIndex`, `forwardGoto` do not exist in
      `ar-web-engine` at all).
- [ ] Task: Confirm with the user whether any real BotJob has been authored using forward GOTO yet —
      determines whether this is a live production crash or an unshipped feature.

### Medium — Engine never repairs a corrupted Excel-GOTO row (C-2)

- [ ] Task: Port the `parentBlockId` null/`<=0` check + `fixExcelGoto` call + reload sequence from
      Scanner's `launchBotJobButton`/`testRunBlockPlaywright` into
      `EngineRunner.startParametersInterpreter`, ahead of `executeJob()`.
- [ ] Task: Confirm with the user whether Engine-only execution (a BotJob run standalone before any
      Scanner session ever Launches/TEST RUNs it) is a supported scenario — determines urgency.

### Low / informational cleanup (C-3, C-4)

- [ ] Task: Decide whether to thread the clientNamed alias map through Engine's
      `ExcelReader.extractData` call for future-proofing, or delete
      `ExtractedData.missingFields`/`getMissingFields()` as dead code from both repos (currently
      inert either way — nothing reads the getter).
- [ ] Task: Delete the unreachable duplicate `ELSE` branch in `ar-web-engine`'s
      `PerformActions.checkActionToJump` (~lines 3749-3752) next time that file is touched. Purely
      cosmetic, no behavioral effect.

### Verification once the above lands

- [ ] Task: Re-run the acceptance-matrix rows "Forward GOTO count 1/2" and "Backward GOTO count 1/2"
      against both Scanner and Engine.
- [ ] Task: Update the Review ledger and Decision log below with the resolved product decisions and
      the commit IDs that closed each task.

## Claude — production-data test coverage (2026-07-12)

Added against `D:\Projects\ARWebBancaStato` (config `Config-4.2\ARWeb.config`, database `ARWeb\database.db`)
per the user's request to test `executeJob()` "as much as possible" via Playwright, with the Engine repo
treated strictly as read-only reference (no test code added there).

- [x] Task: Query the real production database for actual BotJob/block/action data before writing any
      fixture, instead of guessing IDs or action codes. Found: bot job 5 ("Saldo Banca Stato", used by
      the existing `BancaStatoBotJobPlaywrightIT`) has **all blocks inactive**; zero rows anywhere in
      the whole database use `IF`/`ELSEIF`/`ELSE`/`ENDIF`/`GOTO`; only `LOOP` appears among branching
      actions. This directly answers one of the open questions above: no real BotJob has been authored
      with a forward GOTO yet, so C-1 is a live latent bug, not (yet) a live production failure.
- [x] Task: Investigate whether `ARScannedElementPane.executeJob()`/`testRunBlockPlaywright()` can be
      invoked directly from a JUnit test (the only way to get a truly authentic, non-mocked run).
      Finding: **not practical without a full JavaFX bootstrap.** `ARScannedElementPane` has
      `private final WebView webView = new WebView();` as an instance-field initializer — JavaFX
      requires `WebView` construction on the FX Application Thread — and eagerly loads the
      `ARScannedElementScene` singleton as a static field, so merely touching the class cascades into
      constructing another full scene. No test in this suite bootstraps JavaFX today (`Platform.startup`
      / TestFX do not appear anywhere under `src/test`). This is independent evidence for Phase 3's
      "canonical execution core... independent of Selenium/Playwright/JavaFX objects" — the current
      design is not unit-testable even in principle until that extraction happens.
- [x] Task: Add `src/test/java/com/allinweb/ch/runner/BancaStatoAperturaContoAllBlocksPlaywrightIT.java`
      — real config, real database, real Playwright browser, against bot job 20 ("Apertura Conto"),
      the only bot job with active blocks pointed at a public, auth-free, safe-to-repeat page
      (`https://www.bancastato.ch/apertura-conto`, the actual page the instructions were scanned
      against — unlike the existing sibling test, which redirects a login-flow bot job to an unrelated
      contact-form URL). Resolves the recorded locators for both active blocks (229 "Apre Aconto"
      CLICK steps and 231 "Start Registration" INSERT steps) in one browser session without invoking
      click/fill. Its loop structure models ALL by visiting both blocks; the second test models ONE by
      omitting the block-231 loop and asserting that block's email input remains empty. This validates
      fixture/selector reachability and the intended test boundary, not end-to-end executor behavior.
      Disabled by default (`-DbancastatoAperturaContoIT=true`), matching the existing sibling tests'
      convention.
- [x] Task: Add
      `src/test/java/com/allinweb/ch/facade/actions/InstructionGraphControlFlowFixtureTest.java` — fast,
      always-on, no browser/DB — synthetic fixtures covering the commands absent from production data
      (IF/ELSE/ENDIF jump resolution, `checkActionToJump`, LOOP/REFRESH_LOOP operation parsing, output
      bookkeeping, forward/backward/malformed GOTO index resolution), extending
      `GotoExecutionRoutingTest`'s pattern rather than duplicating it.
- [ ] Task: Runtime-validate both new test files on the user's machine (`mvn -Dtest=... test`) — not run
      by Claude per standing instruction not to invoke Maven; written to compile against the current
      API surface (`ARPlaywrightDriver`, `InstructionGraph`, `PerformDataBase`) but unverified end-to-end.
- [ ] Task: If bot job 20's "Apertura Conto" blocks are later edited (fields renamed, cookie banner
      removed, new blocks inserted), these tests will need their hardcoded IDs (229, 231, 20) and the
      `e_mail_address` instruction name refreshed to match.

## CODEX - remaining Bot Job Details controls migration (2026-07-12)

This is the CODEX implementation/checklist for the remaining controls shown in
`specifications/migrations/remainig buttons.png` and the metadata form redesign shown in
`specifications/migrations/make bette design.png`. Checked items are implemented in the recorded
delivery commits; the verification section contains the completed test, build, package, and push evidence.

### Findings and contract corrections

- [x] Task: Inventory the reachable JavaFX controls: Excel, Generate, Report, Navigation Time,
      Launch, Execute All/block selection, reload, ALL/ONE, TEST RUN, STOP, Export, Import,
      restore date, directory chooser/path, and BAT generation.
- [x] Task: Confirm that the legacy transfer field incorrectly used `PATH_LICENSE`; use
      `PATH_EXPORT` only as the native chooser's initial directory and never broadcast an absolute
      configured path in shared Bot Job state.
- [x] Task: Split monotonic UI state ordering (`revision`) from metadata optimistic locking
      (`metadataRevision`) so workspace/execution/file events cannot create false metadata conflicts.
- [x] Task: Keep execution state in the active job registry instead of constructing or reading a
      JavaFX pane from the WebSocket/service thread.
- [x] Task: Bind selected transfer directories to transport session + Bot Job. Export/import reject
      a client path that was not selected by the native chooser for that same scope.
- [x] Task: Extract modal-free Bot Job transfer work into `BotJobTransferService`, wait for the real
      database result, generate job-scoped backup names, and accept the legacy date-only filename on import.
- [x] Task: Serialize headless toolbar I/O away from the JavaFX thread; keep only native directory/report
      choosers on the FX thread and open the selected desktop file on the I/O executor.
- [x] Task: Restrict both embedded Jetty WebSocket listeners and their port probes to IPv4 loopback,
      reject duplicate live logical sessions without replacing the original transport, and make close/error
      cleanup conditional on the exact logical-session/transport pair.
- [ ] Task: Require a Java-generated unguessable WebSocket authentication token on every connection.
      This remains blocked because the production `actionExecutor.zip` contains only the encrypted
      `actionExecutor.min.enc` payload and its current URL accepts only the legacy numeric session ID.
      Rebuild that plugin to accept and append the token before enabling strict server-side token rejection;
      the server, React WebViews, native Java clients, page scanner, close-browser script, and action executor
      must be switched atomically so production action execution cannot be disconnected.

### Backend implementation

- [x] Task: Add typed `BotJobToolbarAction`, structured results, a bounded parameter-aware idempotency
      ledger, and `botJobDetails.toolbar.action` / `.actionResponse` routing.
- [x] Task: Implement stable-block-ID TEST RUN selection. The server reloads blocks and derives the
      canonical order and selected endpoint; React never supplies either value.
- [x] Task: Preserve the selection invariant: Execute All accepts ALL only; a numbered block accepts
      ALL (continue from selected) or ONE (selected block only).
- [x] Task: Permit STOP after license revocation, publish STARTING/RUNNING/STOPPING/INTERRUPTED/FAILED
      state, bind STOP to the exact scanner execution ID, and publish the terminal state only after
      that owned executor task actually finishes.
- [x] Task: Implement Navigation Time validation (0-10), Excel open/generate, report chooser/open,
      external Engine launch, Export, Import/date, folder chooser, and headless BAT creation.
- [x] Task: Stop constructing/mounting the legacy JavaFX toolbar at runtime; the Bot Job WebView owns
      the full workspace area.
- [x] Task: Physically delete every unreachable legacy JavaFX toolbar field, construction branch,
      event handler, and button-only helper after the cleanup compile passes.
- [x] Task: Use workspace epochs, an immutable persisted job context, a zero-queue foreground lease,
      run-owned scanner execution IDs, prompt STOP, guarded Close, and exact transport cleanup so stale
      jobs/stops/choosers cannot mutate a later workspace or inherit its transfer-folder grant.
- [x] Task: Replace the remaining natural-completion `IDLE` approximation with executor-owned
      PASSED/FAILED result data. `TestRunExecutionOutcomeTracker` records the exact submitted execution,
      and the coordinator publishes PASSED, FAILED, or INTERRUPTED only after that execution completes.
- [ ] Task: Add a blocks-specific revision/event domain if edits must be rejected rather than resolved
      by current stable block ID at execution time.
- [x] Task: Make multi-table Bot Job import transactional by suppressing legacy stage commits inside
      one owning JDBC transaction; roll back every earlier table when any later stage fails.
- [x] Task: Publish exports from a same-directory temporary file under a unique timestamped name,
      never replace an existing snapshot, and select the newest job/date snapshot with exact/legacy fallback.
- [x] Task: Treat external Engine Launch as a detached process, prevent conflicting Launch/TEST RUN/
      Import/generation work until it exits, and retain output/error log redirection.

### React components and form design

- [x] Task: Add separated execution, spreadsheet/report, and transfer components, each with its own
      `.module.scss`, and compose them in `BotJobDetailsChrome` without a second WebSocket.
- [x] Task: Render Execute All as the first option; default ALL is green, ONE is orange, and selecting
      Execute All forces ALL.
- [x] Task: Redesign metadata editing as a semantic responsive `<form>` with labeled inputs,
      read-only organization/project/URL context, accessible errors, Cancel, and Save.
- [x] Task: Add real numeric Navigation Time and date/path form controls plus capability, connection,
      pending, and execution-state disabled behavior.
- [x] Task: Keep destructive confirmation in React for Excel replacement, Export, and Import; remove
      those confirmations from the JavaFX toolbar path.

### Verification and delivery

- [x] Task: Compile the current backend source set successfully (304 main and 71 test Java sources,
      Java 17).
- [x] Task: Pass 65 focused parser, registry, idempotency, concurrency, transaction, transfer, and
      socket lifecycle tests with zero failures; pass the 19-test affected lifecycle/socket subset again
      after the final ownership and connection-cleanup corrections.
- [x] Task: Pass the complete focused backend Bot Job Details test set after physical JavaFX cleanup.
- [x] Task: Pass 9 focused React component/controller/contract suites (31 tests) and the optimized
      React build; remaining build output is the repository's pre-existing warning backlog.
- [x] Task: Add and pass a headless Playwright localhost/mock-socket test for every new visible control,
      ALL/ONE invariants, form behavior, pending/disabled state, and responsive layout.
- [x] Task: Commit/push the React project with `CODEX...` commits through
      `30d6f331e6519c8bec9211470d1a76e6d8d74363`, build it, clean-replace 45 backend resource
      files, and verify path/length/SHA-256 parity. Deployed bundle: `main.6a92189b.js`, SHA-256
      `DCCA4F5363F6719F59F857D2601BE2DDF626DFC65CE698E638DB6362B85FCC8D`.
- [x] Task: Package the Java backend with `mvn clean package -DskipTests`, validate the shaded JAR,
      and commit the implementation as `3cd86f87c734d0507725dcfc3be3edef1b3a1689`
      (`CODEX complete Bot Job Details toolbar migration`). Artifact:
      `target/AR_Web_Scanner-4.2.jar`, 384,197,039 bytes, 58,696 ZIP entries, main class
      `com.allinweb.ch.ARControlPanel`, SHA-256
      `5D8F4E3BB844AE93FB685F16B8D50D2BB96311B61071F524A849771AE2FAE82E`.
      The implementation and this successor evidence commit are pushed together to
      `origin/refactor/perform-actions-decomposition`.

### CODEX - backend lifecycle and Task 1 continuation (2026-07-13)

This continuation was backend-only. The user-owned React/Bot Job Details design and deployed build
were not modified.

#### Completed findings and fixes

- [x] Task: Fix Bot Job A -> B switching so every job-scoped variable, page, block, component, and
      action cache is cleared before B is loaded; fail activation closed instead of falling back to A.
- [x] Task: Retire A's exact logical WebSocket sessions, transfer grants, pending JavaFX WebView
      bootstrap timers/listeners, and loaded documents before B can bootstrap.
- [x] Task: Make `BotJobDetailsWebViewBootstrap` activation/deactivation generation-safe and generate
      its JavaScript through JSON escaping rather than interpolating job/session text.
- [x] Task: Extract TEST RUN lifecycle ownership from `ARViewBotJobPane` into the JavaFX-free
      `BotJobTestRunCoordinator` and bind it atomically to the exact scanner execution ID.
- [x] Task: Publish executor-owned PASSED/FAILED/INTERRUPTED outcomes; prevent stale attempts,
      executor rejection, transient completion-probe failures, or monitor interruption from unlocking
      the workspace before the scanner is actually complete.
- [x] Task: Make STOP cancellable before an execution ID exists, deliver STOP through a fallback when
      the primary executor rejects it, and restore the exact prior STARTING/RUNNING state when a running
      executor rejects STOP so the user can retry.
- [x] Task: Require acknowledged WebSocket runtime-state delivery with a timeout and retry terminal
      publication when synchronous or asynchronous transport delivery fails.
- [x] Task: Pass the clean safe backend suite: 235 tests, 0 failures, 0 errors, 1 optional diagnostic
      skip. Live `*IT` flows and the separately reported frontend contract test were excluded.
- [x] Task: Run the 15-test coordinator race/failure suite repeatedly after correcting the startup
      token/unblock ordering; all repeated runs passed.

#### Current frontend validation blockers - approval required before any React change

- [ ] Task: Restore or intentionally replace the deployed Bot Job metadata `Edit` entry point. The
      isolated Playwright contract found no metadata editor in the user's current design.
- [ ] Task: Correct the layer covering the visible Create BAT control. Real pointer hit-testing lands
      on another layer; the test dispatched a synthetic click only to continue auditing later controls.
- [ ] Task: Rerun `BotJobDetailsToolbarPlaywrightTest` after the user approves and completes any needed
      frontend changes. Current result: 1 test failure reporting exactly the two items above.

#### Task 1 - reduce `ARViewBotJobPane` to a WebView/window host

- [x] Task: Extract exact TEST RUN/STOP coordination, terminal outcomes, monitor recovery, and runtime
      publication from the pane.
- [x] Task: Remove the dead, unmounted API-tool WebView and harden reusable WebView activation,
      listener, timer, session, and close/reopen ownership.
- [x] Task: Extract workspace activation, job-cache loading, grid refresh, and scanner selection from
      `ARViewBotJobPane` into the JavaFX-free `BotJobWorkspaceService`. Activation and refresh now
      replace shared caches fail-closed, return immutable serialized grid snapshots for the host to
      publish, and expose the preserved web/mobile scanner disposition without scene coupling.
- [ ] Task: Extract the full Pre Scan workflow and its browser/driver lifecycle.
  - [x] Extract isolated Playwright driver creation/reuse, refresh, action dispatch, shutdown, failed-open
        cleanup, and the single-scan lease into the JavaFX-free `PreScanBrowserSession`. OCR/status/workflow
        orchestration remains in the pane and keeps the parent task open.
- [ ] Task: Extract scanner launch/modal coordination and remove pane-owned scanner flags.
- [ ] Task: Extract remaining native file, external Engine, organization, capability, payload, and
      workspace-close operations behind typed ports.
- [ ] Task: Remove zero-caller helpers, move remaining WebSocket/mobile persistence ownership out of
      `ARViewBotJobScene`, runtime-validate close/reopen and A -> B switching, then retire the pane/scene.

Task 1 is therefore progressed but not complete; checking it complete now would hide active JavaFX-host
responsibilities that still require extraction and desktop runtime validation.

Focused continuation evidence: Java compiled 309 main and 76 test sources. The 51-test workspace,
Pre Scan browser-session, WebView, registry, TEST RUN coordinator, action-contract, and WebSocket
lifecycle suite passed with zero failures, errors, or skips. The React project, deployed React resources,
and Bot Job Details design were unchanged.

## Decision log

- D-001: One canonical roadmap file; reviewers append evidence here.
- D-002: One mode owner for today's feature: Bot Job Details TEST RUN.
- D-003: No React change for a direct JavaFX-to-Java TEST RUN call.
- D-004: `ALL + numbered block` currently means FROM_SELECTED and must be named explicitly internally.
- D-005: Do not migrate by copying the older Engine method over newer Scanner behavior.
- D-006: Stable block ID, immutable request context, and integrated GOTO tests are prerequisites for claiming execution parity.
