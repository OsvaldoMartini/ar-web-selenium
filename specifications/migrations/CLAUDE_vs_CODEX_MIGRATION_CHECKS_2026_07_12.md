# Claude vs Codex Migration Checks — 2026-07-12

Status: Claude and CODEX investigation passes complete; the CODEX remaining Bot Job Details controls migration is implemented, tested, React-deployed, Java-packaged, and committed. Desktop runtime validation and the explicitly unchecked follow-up tasks remain pending.

2026-07-28 shared note: Memory List dependency-group selection has moved from Java capability
expansion to a pure React/TypeScript resolver over the currently loaded GridItem/GridItemComp graph.
The resolver covers parent/child, Variables, IF/ENDIF, LOOP/REFRESH_LOOP, and recursive Component
`GOTO`/`EXCEL GOTO` destination blocks. Java now supplies raw variable ownership metadata and keeps
transactional completeness validation plus fresh-ID persistence only. Detailed evidence and pending
runtime acceptance are recorded in `BOT_JOB_DETAILS_COMPONENT_DECOMPOSITION_2026_07_24.md`.
Both `GOTO` and `EXCEL GOTO` retain their source/target block and parent/child semantics, and graph
revisions now include variable ownership so a stale variable relationship cannot be persisted.

This is the canonical investigation and roadmap for aligning local Scanner TEST RUN execution with the external AR Web Engine. Future Claude and Codex reviews must update this file instead of creating parallel roadmaps.

## Review ledger

| Reviewer | Status | Evidence recorded |
|---|---|---|
| Claude | Complete for independent `executeJob()`/Engine parity pass | Independently re-derived P0-3 (confirmed the exact NPE mechanism by line) and found three items not yet in this document: Engine never calls `fixExcelGoto` (P0-3b below), Engine's Excel reader call drops the clientNamed alias map (currently inert), and a dead duplicate `ELSE` branch in Engine's `checkActionToJump`. See "Claude's independent findings" section below. No Java/Maven build was run. |
| CODEX | Complete for investigation, toolbar migration, and automated validation | Compared both execution methods and their bootstrap, data-loading, status, browser, React, and lifecycle boundaries. Migrated the remaining Bot Job Details controls to React, removed their reachable JavaFX toolbar implementation, compiled and packaged Java, and completed the verification evidence in the CODEX section below. |

## Repositories and immutable comparison points

| Project | Branch | Commit inspected | Role |
|---|---|---|---|
| `D:\Projects\AllinWeb\ar-web-selenium` | `refactor/perform-actions-decomposition` | `4360f037a9591365d857ae814bec89bfe40fb2ad` implementation commit | Scanner, JavaFX host, embedded Playwright execution, and React toolbar backend |
| `D:\Projects\AllinWeb\ar-web-engine` | `VERSION-4-2-NEW` | `f890e833d9aad9a8abbfb455e789e3d74c7817a5` | External Selenium engine |
| `D:\Projects\AllinWeb\abr-react-ts-grid` | `VERSION-4.6` | `86256ab80bbf170d23d4283d589d9d2827128b0a` | React migration UI |

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
- [x] Task: Extract the reachable Pre Scan workflow and its browser/driver lifecycle into the
      JavaFX-free `PreScanWorkflowService` and `PreScanBrowserSession`. The service now owns refresh,
      safe selector defaults, actionable-element filtering, page settling, OCR/name resolution,
      diagnostic persistence, element tests, status sequencing, failure recovery, and the scan lease;
      the pane retains only thread dispatch plus WebSocket/modal presentation adapters. Zero-caller
      legacy helpers remain covered by the later cleanup task.
  - [x] Extract isolated Playwright driver creation/reuse, refresh, action dispatch, shutdown, failed-open
        cleanup, and the single-scan lease into the JavaFX-free `PreScanBrowserSession`. OCR/status/workflow
        orchestration remains in the pane and keeps the parent task open.
- [x] Task: Extract reachable scanner preparation, environment/block selection, missing-path rejection,
      duplicate-launch prevention, modal open/close, failure routing, and launch-lease recovery into the
      UI-independent `BotJobScannerCoordinator`; remove the pane-owned `isScannerButtonClicked` flag.
- [x] Task: Extract remaining native file, external Engine, organization, capability, payload, and
      workspace-close operations behind typed ports.
  - [x] Extract Excel/report opening, report-directory validation, BAT creation, external Engine
        preflight/command/log construction, detached launch, and collision tracking into the JavaFX-free
        `BotJobNativeOperationService` with typed property, desktop, and Engine ports. JavaFX retains only
        the native chooser presentation.
  - [x] Extract active-operation close gating, exact WebView/session retirement, workspace-registry close,
        transfer-grant cleanup, Pre Scan browser shutdown, and cleanup-failure ordering into the
        UI-independent `BotJobWorkspaceCloseCoordinator`. The pane retains only its Stage close call.
  - [x] Centralize project/license capability policy in `BotJobWorkspaceCapabilityService`, route
        organization presentation through `BotJobOrganizationCoordinator`, and move Pre Scan plus empty
        Bot Job/Component grid payload construction into `BotJobPreScanPayloadService` and
        `BotJobGridPayloadService`.
- [ ] Task: Remove zero-caller helpers, move remaining WebSocket/mobile persistence ownership out of
      `ARViewBotJobScene`, runtime-validate close/reopen and A -> B switching, then retire the pane/scene.
  - [x] Remove all five direct `SimpleWebSocketServer -> ARViewBotJobPane` dependencies. Socket workspace,
        toolbar, metadata-sync, Pre Scan command, and Pre Scan element-test calls now use the generation-safe
        JavaFX-free `BotJobWorkspaceController`; close/failed activation retires the exact registered host.
  - [ ] Extract the legacy `bot-job-scene` WebSocket client and mobile insertion/persistence workflow,
        redirect remaining scene callers, remove zero-caller compatibility helpers, and retire pane/scene.
    - [x] Server-side AR Mobile inserts now validate the active Bot Job through `BotJobDetailsWorkspaceRegistry`
          and persist through `PreScanApplyService`; `ARViewBotJobScene.initialize` no longer opens the
          `bot-job-scene` loopback WebSocket client. Scene/pane retirement and desktop validation remain open.

Task 1 is therefore progressed but not complete; checking it complete now would hide active JavaFX-host
responsibilities that still require extraction and desktop runtime validation.

Focused continuation evidence: Java compiled 317 main and 84 test sources. The 82-test workspace,
native operation, Pre Scan workflow/browser-session, scanner coordinator, WebView, registry, TEST RUN coordinator,
action-contract, and WebSocket
lifecycle suite passed with zero failures, errors, or skips. The React project, deployed React resources,
and Bot Job Details design were unchanged.

## CODEX - licensed-user menu and complete Auto Test catalog (2026-07-17)

This section is the canonical handoff for the new Main Dashboard Auto Test surface. It preserves the
existing migration rules: React owns the presentation, Java remains the authoritative packaged-data
boundary, the dashboard reuses its existing WebSocket session, and no JavaFX UI was reintroduced.

### Delivered behavior

- [x] Added the supplied Lucide User SVG as the 15 x 15 top-right menu trigger. The menu identifies
      the license owner/current licensed system user and shows license status before the `Auto Test`
      entry.
- [x] Added `Auto Test` as a draggable, non-modal React workspace. It uses the OCR/Memory visual
      language (colors, font sizes, border, shadow, spacing, and header treatment) only. OCR/Memory
      column names, order, positions, markup, and behavior were not changed; Auto Test owns its own
      test-specific columns.
- [x] Added a generated, packaged catalog and `automationTests.list` WebSocket response. The catalog
      is an inventory only: headed/live/manual/generated suites remain visibly classified, and no
      unsafe blanket Run All action is exposed.
- [x] Added the licensed system user to the safe license bootstrap DTO without exposing license file
      contents or other machine details.
- [x] Added a React-owned Playwright suite that starts CRA and mocks bootstrap/WebSocket/new-tab
      behavior before application JavaScript executes. It runs without the Java backend and safely
      covers every Main Dashboard command, all sort selections, find/clear, row selection,
      delete cancel/confirm, the exact User SVG, license menu, full Auto Test list/filter/drag/
      refresh/close workflow, and browser page errors.
- [x] Added a second Java/Playwright contract for the clean-deployed packaged React bundle. It uses
      the complete packaged catalog and the same mocked side-effect boundary, so no database,
      external engine, live URL, or destructive backend operation is invoked.

### Floating Main Dashboard template continuation (2026-07-17)

- [x] Extracted `FloatingWorkspaceFrame` as the shared React foundation for fixed, non-modal,
      pointer-draggable pages. It owns viewport clamping, responsive repositioning, interactive-
      control drag exclusions, pointer cleanup, and the existing OCR/Memory visual frame language.
- [x] Converted the principal `Main Dashboard` into that floating workspace with a drag grip in its
      existing blue title bar. Dashboard commands, table columns, column names/order, row behavior,
      and user/license content were not changed.
- [x] Migrated `Auto Test` to the same shared frame and stopped nested drag events at the owning
      workspace, so moving Auto Test never moves Main Dashboard. Both workspaces remain visible and
      non-modal while Auto Test is open.
- [x] Extended both Playwright contracts to verify fixed/non-modal semantics, Main Dashboard drag,
      user-menu click isolation, independent nested-window drag, responsive viewport clamping,
      post-drag command use, and Main Dashboard survival after Auto Test closes.

### Address-bar-free desktop app shell continuation (2026-07-17)

- [x] Replaced the normal default-browser startup with a direct Chrome/Edge/Chromium application-
      mode launch. The command uses `--app=<loopback URL>`, requests a `1240 x 820` window, and does
      not use a command shell; therefore the persistent tab strip and address bar are not shown.
- [x] Added `desktopShell=1` as the production-only layout contract. Main Dashboard fills the full
      browser client area with no gray browser canvas behind it, no shadow/gutter, and responsive
      `100vw x 100dvh` sizing. Normal `/` navigation retains the floating, draggable dashboard for
      development; in desktop-shell mode the native app window is the movable outer window while
      Auto Test and future child workspaces remain independently draggable.
- [x] Changed the browser title, manifest identity, and native theme color from `API Test`/black to
      `AR Web`/`#0b5394`, matching the dashboard title bar.
- [x] Kept the previous `Desktop.browse`/OS opener as a compatibility fallback when no Chromium
      executable can be found or launched. Chrome/Edge app mode removes the normal address bar but
      intentionally retains standard native window controls and browser security disclosure.
- [x] Added deterministic no-shell launcher tests plus React and packaged Java Playwright assertions
      for exact full-client bounds at `1240 x 820` and `700 x 900`, disabled root-panel drift in
      desktop-shell mode, title/theme deployment, and the unchanged floating-browser mode.

### Bot Job and Page Scanner desktop-shell continuation (2026-07-17)

- [x] Removed the ordinary React `window.open(..., '_blank')` Bot Job path. `Open Job` and row
      double-click now send only the canonical `mainDashboard.openBotJob` command; the Java host
      opens `/?desktopShell=1&openBotJob=<id>` through the same Chromium `--app` launcher used by
      Main Dashboard, so Bot Job Details has no normal tab strip or address bar.
- [x] Added one reusable `DesktopWorkspaceShell` outer template for Bot Job Details, Components,
      Page Scanner Grid, and Pre Scan. It preserves every existing grid column/control while using
      the Main Dashboard `1240 x 820`, Arial, blue-border, non-modal drag contract in normal browser
      mode and exact `100vw x 100dvh` full-client geometry in desktop-shell mode.
- [x] Repaired the previously disconnected Pre Scan navigation boundary. Only a successful,
      request-correlated Bot Job action response may map `botJob -> botJobTasks`,
      `components -> componentTasks`, or `preScan -> preScannerGrid`; failed, stale, and mismatched
      responses cannot switch the active React workspace.
- [x] Kept the established Page Scanner implementation rather than duplicating it. Once
      `preScannerGrid` mounts, `Page Scanner` sends `PRE_SCAN_PAGE`; the Java workflow opens/reuses
      its visible isolated Playwright client browser, scans the selected web page, resolves names,
      and publishes status/elements back into the Page Scanner Grid.
- [x] Added controller, React Playwright, launcher, Main Dashboard, and packaged Bot Job Playwright
      regressions for no ordinary tab, full-client/normal draggable geometry, Bot Job -> Pre Scan
      navigation, the canonical Page Scanner payload, and rendered scan completion status.

### OCR Config and OCR Results floating-workspace continuation (2026-07-17)

- [x] Corrected the OCR mount boundary in `GridItemScann`. OCR Config and OCR Results had been
      mounted inside every rendered scanner block header, which produced one copy per block and no
      page at all when the scanner grid had no groups. Each page is now mounted exactly once.
- [x] Replaced the modal-derived full-screen backdrops and global `:has(...)` positioning override
      with a reusable `FloatingWorkspacePortal` built on `FloatingWorkspaceFrame`. Each OCR page is
      portaled directly under `document.body`, so the Page Scanner/Desktop shell cannot contain,
      clip, or move it.
- [x] Standardized both pages on the established AR Web template: blue `#0b5394` draggable header,
      Arial typography, blue rounded frame, white content, responsive viewport clamping, unique
      semantic labels, and dedicated close controls. Pointer interaction brings either page above
      its floating peer.
- [x] Preserved the complete OCR WebSocket contract and all existing profile, parameter, cleanup,
      test, result, approval, XPath, and accepted-name behavior. No backend production protocol
      change was needed.
- [x] Proved OCR Config and OCR Results are independent non-modal pages: either opens directly,
      each moves without moving Page Scanner, both coexist after `Test current page`, closing one
      leaves the other open, the URL does not change, and no additional browser page is created.
- [x] Removed the obsolete `OCRFloatingPanel.scss` backdrop/absolute-positioning workaround.
- [x] Versioned the React implementation as frontend commit `c5e5922` on `VERSION-4.6` and
      regenerated the backend automation inventory from that committed source head.

### OCR Config and OCR Results detached multi-display continuation (2026-07-18)

This section supersedes the 2026-07-17 body-portal implementation and its "no additional browser
page" conclusion. A DOM portal can escape a React parent but cannot leave its browser viewport, so
it cannot satisfy the required three-monitor placement.

- [x] Removed `FloatingWorkspacePortal` from OCR Config and OCR Results. Bot Job Details/Page
      Scanner now contains zero Config/Results page DOM and only sends `ocrWorkspace.open`.
- [x] Added standalone `openOcr=config|results` React entry routes. Each route validates its unique
      `ocr-config-*` or `ocr-results-*` session, skips `mainDashboardBootstrap`, owns one WebSocket,
      fills the exact desktop client, and retains the established Arial/blue/white OCR design.
- [x] Added strict Java Chromium application-window launch for both OCR pages. It uses `--app` and
      `--new-window`, deliberately has no default-browser fallback, address bar, or tabs, and leaves
      the operating-system title bar available for moving each window to another display.
- [x] Added the backend-owned `OcrWorkspaceCoordinator`. It binds an unguessable workspace session
      to the actual originating `scannerGrid`/`preScannerGrid` transport, organization, job, optional
      URL scope, and draft parameters. Context survives a page reload and expires after four hours;
      active entries are bounded and failed launches roll back their provisional context.
- [x] Added transport-bound `ocrWorkspace.open`, `ocrWorkspace.bootstrap`, and
      `ocrWorkspace.applySuggestions` contracts. Envelope session spoofing cannot select the reply
      or scanner destination. Config may launch an independent Results window; only a Results
      session may publish validated/deduplicated XPath/name suggestions, and only to its bound source
      scanner.
- [x] Reworked Config and Results into full-client page controllers. Config owns profile CRUD,
      cleanup, and Test Current Page. Results bootstraps its immutable scope, runs OCR, owns approvals,
      sends accepted names through Java, and closes only after Java confirms delivery.
- [x] Added deterministic multi-window coverage: a scanner page plus separate Config and Results
      Playwright pages use three distinct sessions concurrently; neither OCR page is mounted in the
      scanner; both fill `1240 x 820`; Config can request another Results window; closing one page
      leaves the other two alive. Headless tests cannot prove a physical compositor move across
      monitors, so actual placement on displays 1/2/3 remains a manual desktop smoke check.
- [x] Clean-built and deployed all 45 React build files into the Java resource tree with zero
      relative-path/SHA-256 differences.
- [x] Versioned the detached multi-display implementation as frontend commit `86256ab` on
      `VERSION-4.6` and backend implementation commit `4360f037` on
      `refactor/perform-actions-decomposition`.

### Singleton Bot Job Details, Page Scanner, and OCR window continuation (2026-07-20)

This continuation supersedes two earlier assumptions without erasing their history: the
2026-07-17 Bot Job path launched a new Chromium application window for every open request, and the
initial detached Page Scanner roadmap proposed one workspace per active Bot Job. The required
invariant is now **one global physical Bot Job Details panel, one global physical Page Scanner
panel, one global OCR Config panel, and one independent global OCR Results panel** for the AR Web
process.

- [x] Added backend `BotJobDetailsWindowCoordinator` ownership around one unguessable
      `bot-job-window-<UUID>` control session. The first request launches the native panel; later
      Main Dashboard requests publish `botJobDetails.windowTarget` with the latest Bot Job and
      workspace epoch to that persistent connection rather than calling `--new-window` again.
- [x] Kept the Bot Job control connection independent from `botJobTasks`, `componentTasks`, and
      other content sockets. This lets the same physical panel replace its route, clear job-specific
      state, and reconnect the selected content surface even when the prior surface was not the Bot
      Job task grid.
- [x] Reduced Page Scanner native-window ownership from a per-Bot-Job map to one global physical
      slot. A repeat request for the same trusted job/epoch reuses and focuses that panel.
- [x] Defined cross-job scanner retargeting as physical reuse with logical replacement. The
      coordinator allocates a fresh `page-scanner-<UUID>`, retires the previous scan workflow,
      mutation ledger, browser ownership, and OCR source binding, then publishes
      `pageScanner.workspaceRetarget` to the existing panel. The new session prevents late scan
      chunks, Apply/block acknowledgements, OCR callbacks, and stale close events from Job A from
      being accepted after the panel moves to Job B.
- [x] Preserved the one scanner native panel during a Bot Job Details target switch while
      invalidating the old Bot Job's authoritative workspace epoch. A scanner request from the new
      Bot Job performs the fresh-session retarget; it never transfers the old job's transport
      identity.
- [x] Added a bounded initial-connection grace rule. A newer request received while a panel is
      still starting updates its pending target and does not launch a duplicate.
- [x] Defined Alt+F4/disconnect recovery: the next valid request may launch one replacement for the
      latest target, but cannot create concurrent copies. Same-job repeat requests focus/reuse the
      connected panel.
- [x] Complete the React listeners and production-bundle proof. Bot Job Details validates its
      exact control session, replaces history without opening a tab, resets/remounts for the
      published job/epoch, and focuses the existing panel. Page Scanner accepts retarget only on
      its old trusted connection, replaces the route/session, reconnects, and remounts under the
      fresh logical session; a same-session notification only focuses.
- [x] Reduced detached OCR ownership to one process-wide native window per OCR kind. Reopening OCR
      Config or OCR Results for its current scanner context focuses the corresponding panel. A
      request from another Page Scanner/Bot Job reuses that same physical panel, allocates a fresh
      `ocr-config-*` or `ocr-results-*` logical identity, and publishes
      `ocrWorkspace.windowRetarget`; Config and Results remain two independent movable windows.
- [x] Added strict OCR retarget validation, URL replacement, keyed React remounting, and backend
      active-session checks. Late operations from the retired OCR or Page Scanner transport are
      rejected, so state and results from Job A cannot mutate or appear in Job B after retargeting.
- [x] Added a two-second reconnect grace to Bot Job Details, Page Scanner, OCR Config, and OCR
      Results ownership. A transient WebSocket replacement retains the reserved physical window;
      only a transport that remains disconnected after the grace period may cause one replacement
      launch.
- [x] Serialized blocking and acknowledged asynchronous WebSocket writes per physical JSR-356
      `Session`. This removes Jetty's `Blocking message pending 10000 for BLOCKING` race when scan
      chunks/status, pings, OCR callbacks, and workspace-retarget events write concurrently. The
      `10000` value is Jetty's hexadecimal blocking-state flag (`0x10000`), not a 10,000 ms timeout.
- [x] Run the automated singleton-window verification gate. Evidence includes coordinator unit tests
      for first launch, connected retarget, same-target focus, latest-target delivery on reconnect,
      launch grace, publication rollback, strict session validation, stale disconnect isolation,
      and one Alt+F4 replacement; frontend tests for route/state/socket replacement and malformed
      event rejection; and production-bundle Playwright coverage proving Page Scanner, OCR Config,
      and OCR Results each retarget in their existing browser page without increasing page count.
- [ ] Run the manual Windows smoke test that opening Jobs A, B, then A leaves exactly one Bot Job
      Details panel, one Page Scanner panel, one OCR Config panel, and one OCR Results panel,
      including Alt+F4 replacement and multi-monitor drag.

The full design, protocol, lifecycle rules, and acceptance checklist are recorded in
`ROADMAP_DETACHED_PAGE_SCANNER_WORKSPACE_2026_07_20.md`.

### Complete inventory snapshot

- Regenerated at 2026-07-20 from `ar-web-selenium`, `abr-react-ts-grid`, and `ar-web-engine`.
- 972 displayed catalog rows.
- 938 code test cases in 257 source files: 772 Java/JUnit cases in 209 files and 166
  React Jest/Playwright cases in 48 files.
- 19,452 generated API requests grouped under 21 generated Bash/curl suites rather than rendering
  19,452 duplicate DOM rows.
- 20,390 total automated cases when code cases and generated API requests are combined.
- 9 manual artifacts and 4 support-only artifacts are retained and clearly labeled.
- `ar-web-engine` was audited and truthfully reports zero committed automated tests. Its local/live
  Selenium launch profiles are manual, high-side-effect production automation and are excluded from
  Run All eligibility.

Regenerate the versioned inventory with:

```powershell
node scripts\generate-automation-test-catalog.mjs
```

### Verification evidence

- [x] `npm test -- --runInBand --watchAll=false src/components/auto-test/AutoTestWorkspace.test.tsx`
      - 2 passed, 0 failed.
- [x] `npm run test:e2e`
      - 4 React-only Playwright navigation tests passed; no backend was started. They exercise the
        floating Main Dashboard and Auto Test, the normal draggable Bot Job frame, the exact full-
        client desktop shells, correlated Pre Scan navigation, and the Page Scanner command/status.
- [x] `npm test -- --watchAll=false --runInBand OCRConfigPanel.test.tsx OCRTestResultsPanel.test.tsx`
      - 7 passed, 0 failed, including portal placement, non-modal semantics, drag-handle markers,
        independent close callbacks, typed parameter save, profile protection, approvals, and XPath.
- [ ] `npm test -- --watchAll=false --runInBand` (complete legacy Jest sweep)
      - 37 suites / 139 tests passed. The sweep remains red in three unrelated pre-existing suites:
        the untouched CRA `App.test.tsx` still expects `Learn React`, `AboutPanel.test.tsx` expects
        the removed `receiveDataFromJava` bridge, and `CloneJobManager.test.tsx` has an invalid
        out-of-scope `WebSocket` Jest mock reference. All OCR-focused suites passed in this run.
- [x] `npm test -- --runInBand --watchAll=false src/components/bot-job-details/useBotJobDetailsController.test.tsx src/components/workspace/WorkspaceHeader.test.tsx src/components/bot-job-details/BotJobDetailsHeader.test.tsx src/components/scanner/ScannerWorkspaceHeader.test.tsx`
      - 31 passed, 0 failed, including the correlated Bot Job surface-navigation contract.
- [x] `npm run build`
      - optimized React build completed; warnings are pre-existing project lint/dependency warnings.
- [x] Clean-deployed 45 build files and verified source/destination SHA-256 parity.
- [x] `mvn -Dtest=BotJobDetailsToolbarPlaywrightTest test`
      - 1 passed, 0 failures, 0 errors, 0 skips against the deployed production bundle, including
        independent OCR Config/Results portals, drag isolation, coexistence, close behavior, and
        same-page navigation.
- [x] `mvn '-Dtest=AutomationTestCatalogServiceTest,MainDashboardAutoTestPlaywrightTest' test`
      - 2 catalog tests passed; the unrelated Main Dashboard browser case was safely skipped after
        the environment denied a second Chrome process (`spawn EPERM`). Its equivalent React-only
        Playwright contract passed in the four-test frontend run above.
- [x] `mvn -Dtest=AutomationTestCatalogServiceTest,LicenseServiceTest,DesktopAppBrowserLauncherTest,ARWebSocketServerBindingTest test`
      - 22 passed, 0 failures, 0 errors, 0 skips, including five direct Chromium app-mode launcher
        contracts that never start a real browser or command shell.
- [x] `mvn -Dtest=MainDashboardAutoTestPlaywrightTest test`
      - 1 passed, 0 failures, 0 errors, 0 skips, including real headless Chrome assertions against
        the deployed bundle, floating workspace behavior, and the full-client desktop shell.
- [x] `mvn -Dtest=DesktopAppBrowserLauncherTest,PreScanWorkflowServiceTest,PreScanBrowserSessionTest,BotJobWorkspaceControllerTest test`
      - 17 passed, 0 failures, 0 errors, 0 skips across the app launcher and existing visible-client
        Page Scanner workflow boundaries.
- [x] `mvn -Dtest=BotJobDetailsToolbarPlaywrightTest,MainDashboardAutoTestPlaywrightTest test`
      - 2 passed, 0 failures, 0 errors, 0 skips in real headless Chrome against the clean-deployed
        production bundle, including Bot Job -> Pre Scan -> Page Scanner.
- [x] `npm test -- --watchAll=false --runInBand OCRConfigPanel.test.tsx OCRTestResultsPanel.test.tsx ScannerToolbar.test.tsx ScannerWorkspaceHeader.test.tsx`
      - 4 suites / 21 tests passed for the full-window OCR panels and their scanner entry controls.
- [x] `npm run test:e2e`
      - 4/4 React Playwright tests passed, including concurrent scanner, detached Config, and
        detached Results pages with unique sockets, full-client geometry, Config -> Results launch,
        apply acknowledgement, no parent OCR DOM, no bootstrap collision, and independent close.
- [x] `npm run build`
      - Optimized React production build completed. Reported lint/dependency warnings are the
        existing project-wide warnings; no TypeScript/build error was introduced.
- [x] `mvn '-Dtest=OcrWorkspaceCoordinatorTest,DesktopAppBrowserLauncherTest,SimpleWebSocketServerSessionLifecycleTest' test`
      - 23 passed, 0 failures/errors/skips for strict app-window launch, unique/expiring contexts,
        defensive parameter copies, source-only suggestion delivery, and reload takeover.
- [x] `mvn -Dtest=BotJobDetailsToolbarPlaywrightTest test`
      - 1 passed, 0 failures/errors/skips in real headless Chrome against the clean-deployed bundle;
        Bot Job/Pre Scan sends detached OCR launch requests and mounts no Config/Results page.
- [x] Regenerated `automation-tests.json`: 899 catalog rows, 859 code cases, 19,452 generated API
      cases, and 20,311 total automated cases. Clean-deployed 45 React files with zero hash/path
      differences.
- [x] 2026-07-20 singleton continuation verification:
      - Eight focused React suites passed 32/32 tests; the full Jest sweep passed 43/46 suites and
        158/160 tests, with only the three already-recorded stale legacy suites remaining red.
      - `npm run test:e2e` passed 4/4 after updating the mock protocol to the persistent Bot Job
        control session, detached Page Scanner, and in-place Page Scanner/OCR retarget contracts.
      - The five critical Java coordinator/transport suites passed 63/63 tests. A real-Chromium
        `BotJobDetailsToolbarPlaywrightTest` passed and kept Page Scanner, OCR Config, and OCR Results
        in their existing pages across fresh logical-session retargets.
      - `mvn clean package -DskipTests` completed successfully (419 production and 209 test sources),
        and the 45-file React/backend deployment has zero relative-path SHA-256 mismatches.
      - Regenerated inventory: 972 catalog rows, 938 code cases, 19,452 generated API cases, and
        20,390 total automated cases; `AutomationTestCatalogServiceTest` passed 2/2 afterward.
      - Versioned the implementation as frontend commit `786357d` on `VERSION-4.6` and backend
        commit `896ae61e` on `refactor/perform-actions-decomposition`.
- [ ] Add execution controls only after each framework has an allowlisted runner, persisted result
      contract, cancellation/timeout ownership, and explicit confirmation for headed/live suites.

### Selenium retirement continuation (2026-07-18)

- [x] Established Playwright as the only Scanner browser launcher. No production source constructs
      a Selenium browser driver, and Scanner startup no longer validates a WebDriver executable.
- [x] Removed the always-empty WebDriver registry, dead ARWebDriver helper chain, five dead
      Selenium-oriented production classes, and six non-JUnit socket-injection programs that could
      not run without a Selenium driver.
- [x] Removed `use_playwright` and `playwright_selenium_fallback`; failed Playwright actions can no
      longer be routed into an unreachable Selenium fallback by configuration.
- [x] Routed Scanner stop, QUIT, execution-tail close, Scanner close, and application shutdown
      through the Playwright lifecycle. Browser close reuses the Playwright executor; application
      shutdown terminates it; executor cleanup now runs even when browser close fails.
- [x] Kept `path_web_driver` only for the separately launched external Engine and removed it from
      mandatory Scanner properties and Scanner/browser error messages.
- [x] Added explicit Guava ownership so final Selenium dependency removal will not accidentally
      remove a currently transitive runtime dependency.
- [x] Added the phased completion roadmap in
      `specifications/migrations/SELENIUM_TO_PLAYWRIGHT_REMOVAL_2026_07_18.md`.
- [x] Replaced the Selenium scanner adapter with browser-neutral Playwright support for page state,
      review files, live element snapshots, screenshots, and support capture enrichment.
- [x] Ported workspace previous/next-tab controls, `PerformListElements`, OCR/diagnostic capture, and
      report screenshots to Playwright-only browser APIs.
- [x] Removed the dead page-scan service, Selenium preload/actionExecutor injectors, duplicate
      packaged plugin source, always-null insert/update enrichment, Selenium logger suppressor, and
      additional unreachable locator/recovery helpers.
- [x] Compiled 415 production and 203 test Java sources; passed 66 focused migration/scanner/action
      tests, with five browser-launch cases skipped because browser download is disabled.
- [x] Passed the complete safe backend sweep with only the external Access diagnostic excluded:
      711 tests, 0 failures/errors, 19 environment/fixture skips. The earlier unfiltered 708-test default
      sweep had exactly one failure: `PerformDBEngineAccessTest` could not find its configured live
      Access database path; no migration test failed.
- [x] Regenerated the complete automation inventory: 894 displayed rows, 860 code cases, and
      19,452 generated API requests.
- [x] Current working-tree checkpoint: removed the unused `BrowserJsUtils` Selenium bridge and its
      uncalled iframe/highlight facade methods. Ported `EngineDialogs` browser messaging and QUIT
      cleanup to `ARPlaywrightDriver`, eliminating Selenium from that class. The direct source
      footprint is now 14 production files, 1 JUnit file, and 0 packaged plugin sources. The exact
      checkpoint compiled 415 production and 203 test sources and passed the complete safe backend
      sweep: 711 tests, 0 failures/errors, and 19 environment/fixture skips.
- [ ] Remaining gate: migrate 14 production files and the final Selenium-typed JUnit file; packaged
      plugin sources are already clear. Remove `selenium-java` only when the repository-wide
      Selenium source search is empty.

## Decision log

- D-001: One canonical roadmap file; reviewers append evidence here.
- D-002: One mode owner for today's feature: Bot Job Details TEST RUN.
- D-003: No React change for a direct JavaFX-to-Java TEST RUN call.
- D-004: `ALL + numbered block` currently means FROM_SELECTED and must be named explicitly internally.
- D-005: Do not migrate by copying the older Engine method over newer Scanner behavior.
- D-006: Stable block ID, immutable request context, and integrated GOTO tests are prerequisites for claiming execution parity.

## Claude — Memory List drag & drop fix + test bench (2026-07-24)

Task thread: user reported Memory List row reordering "worked in `npm start` (real Chrome) but
did NOT work inside Playwright." Investigated, root-caused, fixed, and verified through browser
automation.

### Root cause

`react-beautiful-dnd@13.1.1` does not register its `Droppable` under React 18 `StrictMode` in dev
(`index.tsx:399` wraps the whole app in `<React.StrictMode>`). The double-invoke of effects
un-registers the droppable, so the reorder drag silently no-ops. This is a known rbd + React 18
incompatibility, not a component bug.

Separately, `MemoryList.tsx` renders rows only from the Java WebSocket snapshot (`snapshot.items`),
so with `npm start` and no backend the list is empty — nothing to drag, which masked the above.

### What Claude changed (frontend only, `abr-react-ts-grid` `VERSION-4.6`, no backend touched)

- [x] Added `src/components/MemoryDragDemo.tsx` — standalone, backend-free test bench: 10 synthetic
      rows, reorder held in local React state, reachable via `http://localhost:3000/?memoryDragDemo=1`.
      Committed `bad3087` (`CLAUDE:` prefix), pushed to `origin/VERSION-4.6`.
- [x] Introduced a reusable `StrictModeDroppable` wrapper (delays one `requestAnimationFrame`
      before mounting the real `Droppable`) in both the demo and the real `MemoryList.tsx`, and
      swapped the bare `<Droppable>` in `MemoryList.tsx` for it. Fixes drag in the deployed app too.
- [x] `npx tsc --noEmit` clean for `MemoryDragDemo.tsx`, `MemoryList.tsx`, and `index.tsx`
      (only pre-existing `node_modules/i18next` TS-4.9 errors remain, unrelated).

### Verification (claude-in-chrome browser automation against the live `npm start`)

- [x] `left_click_drag` (stepped native drag) row 1 → slot 3: reordered, status "Moved item 1 from
      position 1 to 3." Confirms rbd's mouse sensor works when the drag is stepped.
- [x] Keyboard sensor (focus handle → Space lift → ArrowDown → Space drop) on row 2: reordered to
      position 2, deterministic single step.

### Why the user's Playwright drag failed (and the fix for their suite)

rbd's mouse sensor requires: primary `mousedown` → a `mousemove` crossing the ~5px threshold →
several intermediate `mousemove`s across animation frames → `mouseup`. A naive Playwright
`locator.dragTo()` / single down-move-up does it in one jump and never leaves idle.

- Recommended e2e path: **keyboard sensor** (`handle.focus()` → `Space` → `ArrowDown` → `Space`),
  fully deterministic, no pixel math.
- Alternative: stepped `page.mouse.move(..., { steps })` with small `waitForTimeout`s between the
  threshold-crossing move and the travel move.

### Remaining (not done this pass)

- [ ] Task: When ready to deploy, run `npm run build` in `abr-react-ts-grid`, wipe
      `ar-web-selenium/src/main/resources/build`, and copy the fresh build (per the standard
      sequence in "Validation and deployment record"). The real-`MemoryList` StrictMode fix only
      reaches the running app after a rebuilt jar (user-owned Java build).
- [ ] Task (optional): add a Playwright spec for the demo (`?memoryDragDemo=1`) exercising the
      keyboard-sensor reorder, so rbd drag has regression coverage.

## CODEX — Page Scanner element repository and current-page self-healing (2026-07-24)

Task thread: build a reusable Banca Stato web-element repository by scanning many application pages,
then use it during `executeJob()` when authored locators drift.

Detailed roadmap:
`ROADMAP_PAGE_SCANNER_ELEMENT_REPOSITORY_2026_07_24.md`

### Confirmed current behavior

- [x] The detached Page Scanner does persist every non-empty retained scan result into
      `scanned_element`.
- [x] Re-scanning the same scope/hash updates the row, increments `scan_count`, and refreshes
      `last_scanned_at`.
- [x] A distinct locator hash inserts another row; missing elements are not deleted.
- [x] A plain scan does not create Bot Job instructions. `pageScanner.apply` remains the explicit
      instruction/reference mutation.
- [x] The detached scan does not update the older `element_locator`/`element_locator_rename`
      repository; the two repositories currently diverge.
- [x] Diagnostic `elementDTO-PS-BJ.json` files are overwritten per scan and are not the cumulative
      element repository.

Code path:

1. `GridItemScann.tsx` sends `pageScanner.scan`.
2. `SimpleWebSocketServer.handlePageScannerCommand(...)` maps it to `PRE_SCAN_PAGE`.
3. `BotJobDetailsWorkspaceHost` calls `PreScanWorkflowService.scan(...)`.
4. `PreScanWorkflowService.DefaultDiagnosticsPort.persist(...)` calls
   `PerformDataBase.upsertScannedElements(...)`.
5. `ScannedElementRepository.upsert(...)` performs the transactional insert/update.

### Read-only production evidence

The production SQLite database
`D:\Projects\ARWebBancaStato\ARWeb\database.db` was queried read-only on 2026-07-24:

- 537 `scanned_element` rows for Bot Job 5;
- 15,885 cumulative scan observations;
- three stored `page_url` values;
- latest update `2026-07-24 10:51:49`;
- 536/537 rows have `defined_name`;
- 56 same-page duplicate `defined_name` groups;
- zero rows currently persist raw OCR text/confidence.

This confirms that repeated Page Scanner runs are already building and updating the database
registry. It also confirms that name uniqueness cannot be assumed.

### Critical gaps found

- [ ] Persistence saves `context.endpointUrl()` instead of the actual active
      `browser.currentUrl()`. Manual navigation can therefore scan one page while labeling its rows
      with another URL.
- [ ] The unique key is `(home_banking_id, bot_job_id, element_hash)`, and `element_hash` excludes
      page identity. Equal locator signatures on different pages collide.
- [ ] Scan-time database errors are converted to `[0,0]`; the final Page Scanner `done` state does
      not guarantee that persistence succeeded.
- [ ] The detached workflow retains only results classified as input/button/output/label, not every
      DOM element returned by the scanner.
- [ ] OCR audit fields exist but are not written by the current scanned-element upsert SQL.

### Existing execution healing and its limits

- [x] `executeJob()` uses Playwright and consults `scanned_element` after failed CLICK/OTHER and
      INSERT actions.
- [x] The current resolver supports exact XPath/custom XPath, exact CSS, unique exact name,
      coordinate-disambiguated duplicate name, and fuzzy name.
- [ ] The resolver loads every historical row for the Bot Job and is not scoped to the current
      Playwright page.
- [ ] `InstructionLoad.name` is not a direct live Playwright locator. The resolver only uses it to
      select a stored registry row and then retries that row's locator.
- [ ] Name matching excludes `client_named` and HTML `attrib_name`.
- [ ] OUTPUT/text operations do not use scanned-repository healing.
- [ ] Coordinates are attempted before registry healing, so a stale coordinate can act before the
      safer current-page name path.
- [ ] Boolean action results do not distinguish `NOT_FOUND` from an action already attempted and
      failed; healing therefore lacks a strict one-side-effect guarantee.

### Agreed implementation order

- [ ] Phase 0: back up production data and freeze normalized page-identity rules.
- [ ] Phase 1: persist the actual current page and return a correlated typed persistence receipt.
- [ ] Phase 2: add an append-only migration for page-scoped element identity and freshness.
- [ ] Phase 3: implement page-aware repository/name resolution including all approved aliases.
- [ ] Phase 4: validate one unique candidate against the live current Playwright page.
- [ ] Phase 5: integrate structured failure states, healing before coordinates, OUTPUT support, and
      exactly one final side-effecting action.
- [ ] Phase 6: expose repository counts, duplicates, stale observations, and healing diagnostics.
- [ ] Phase 7: run focused multi-page, cross-page-refusal, migration, and one-action verification.

### Decisions added by this investigation

- D-007: The cumulative repository is `scanned_element`; diagnostic JSON files are not repository
  storage.
- D-008: Scanning and instruction creation remain separate operations.
- D-009: A candidate from another page must never heal a current-page instruction.
- D-010: Ambiguous name matches are refused; first-row selection is not an accepted fallback.
- D-011: Coordinates remain the last fallback and cannot precede page-aware repository resolution.
- D-012: A Page Scanner `done` result must eventually include authoritative database persistence
  outcome and counts.
- D-013: Do not edit the applied `M20260704_ScannedElement` migration; page-aware schema work gets a
  new append-only migration.

### CODEX implementation update — page-scoped scanner persistence (2026-07-24)

- [x] Added collision-first `ScannedPageIdentity` (`url-v1`) using the exact live HTTP(S) URL.
      Query ordering/duplicates and SPA fragments are preserved; invalid live URLs are refused.
- [x] Added append-only `M20260724_ScannedElementPageScope` and registered it after the existing
      migrations. It adds/backfills `page_key`, converts locator identity to a page-scoped hash,
      and creates page/hash plus page/name indexes without editing the applied 2026-07-04 migration.
- [x] Detached and legacy scanner paths capture the active Playwright URL and refuse the repository
      write when navigation changes during scanning/OCR.
- [x] Re-scans now update only organization + Bot Job + exact page + locator. Equal locators on
      different pages remain separate observations.
- [x] Generated custom XPath Apply is scoped by the current Page Scanner URL obtained server-side.
- [x] `executeJob()` repository healing now queries the active Playwright page only, recognizes
      `client_named`/HTML `attrib_name`, prefers custom XPath, and includes OUTPUT/text retry.
- [x] Added focused source tests for URL identity, live-vs-endpoint persistence, navigation refusal,
      migration backfill/idempotency/indexes, cross-page isolation, page-local rescan/custom XPath,
      and name aliases.
- [ ] User-owned validation: run the focused Java tests and package the backend. Codex did not run
      Maven and did not modify the production Banca Stato database.
- [ ] Remaining safety work: live DOM uniqueness validation, registry healing before coordinates,
      freshness/retirement state, OCR audit columns, and a correlated typed WebSocket receipt.

Decisions:

- D-014: New scanner observations require a valid live Playwright HTTP(S) URL; no shared unknown
  bucket is allowed for new writes.
- D-015: `url-v1` preserves raw query and fragment data to prevent silent cross-page merging.
- D-016: The existing SQLite/Access unique constraint remains in place; `element_hash` is made
  page-scoped so cross-page rows coexist without a destructive table rebuild.

## Claude — Memory List drag correction: root cause is the stale jar, not StrictMode (2026-07-24, later)

Follow-up after the user reported "inside Java is not working" and asked for a direct Memory List
diagnostic + a Java drag & drop test. Superseding correction to the section above — nothing there is
deleted, per this file's contract.

### Corrected diagnosis

- The `StrictMode` + react-beautiful-dnd problem is **dev-only** (`npm start`). Inside the packaged
  jar the app serves the **production** build, where React StrictMode does **not** double-invoke
  effects, so a plain `Droppable` works. StrictMode was therefore **not** why drag failed inside Java.
- The real reason drag "does not work inside Java": the React build is packaged **into the jar** at
  `mvn package` time. The running jar is dated **07-17/07-22**, older than the drag code (**07-24**),
  so it serves an old bundle. Copying files into `src/main/resources/build` does nothing until the
  jar is repackaged. Verified by timestamp: deployed jar 07-17 21:51 vs bundle 07-24.
- Backend `REORDER` is correct (traced end to end): `commandPayload` keeps `orderedItemKeys`,
  `canonicalCommand` maps `SELECT_BLOCK→SELECT_TARGET_BLOCK`, `reorder()` validates + rewrites
  `state.order` + bumps revision, `publishSnapshot` echoes the new order. Row keys match on both
  sides (`kind + ":" + rawKey`).

### Regression found and removed

The earlier `StrictModeDroppable` wrapper gated the list behind a `requestAnimationFrame`. rAF is
**paused in hidden/background tabs**, so that wrapper would render the Memory List **empty** if its
detached window ever opened in the background — a production risk introduced by the "fix." Confirmed
live: with `document.hidden === true`, the rAF-gated list showed 0 rows; a plain `Droppable` showed
all 10 rows while still hidden.

- [x] Reverted `MemoryList.tsx` and `MemoryDragDemo.tsx` to a plain `<Droppable>` (no rAF gate).
- [x] Render the dev demo routes **outside** `React.StrictMode` in `index.tsx`, which matches
      production behavior (no double-invoke) and lets a plain `Droppable` work in `npm start`.

### Deliverables this pass

- [x] Frontend shortcut to test the **real** Memory List with synthetic data, no backend:
      `http://localhost:3000/?memoryListDemo=1`. Added a `demoMode` prop to `MemoryList` that seeds a
      10-row snapshot and keeps every command local. Verified live: all 10 rows + droppable render
      even in a hidden tab.
- [x] Runtime console monitor on the drag path (`[MemoryList][drag]` in `handleDragEnd`): logs
      drag-end, abort reasons, no-op reorders, and the `REORDER` send. Open DevTools in the jar,
      drag a row, and it prints exactly where the path stops.
- [x] Java drag & drop test without WebSocket/DB/JavaFX: extracted the pure reorder core into
      `com.allinweb.ch.socket.MemoryListReorder` and added `MemoryListReorderTest`
      (accepts complete permutations, rejects wrong count / null / unknown / duplicate / null-key;
      exhaustive adjacent-swap and full-reversal cases). Run:
      `mvn -Dtest=MemoryListReorderTest test` (user-owned; assistant does not run Maven).
- [x] Built React (`main.1a51dabd.js`, 45 files) and clean-copied into `resources/build`.

### Still user-owned

- [ ] Rebuild the jar (`mvn clean package`) so the running app finally contains the drag code +
      new bundle. This is the actual unblock for "inside Java."
- [ ] Run `mvn -Dtest=MemoryListReorderTest test` to see the backend drag contract go green.

## Claude — Memory List: replaced react-beautiful-dnd with native HTML5 drag (2026-07-24, latest)

rbd proved too fragile: it depends on `requestAnimationFrame`, which the browser **pauses for
hidden/occluded tabs**, so its reorder silently died there and was nearly impossible to drive or
verify. Replaced it with plain native drag in `MemoryList.tsx`.

- [x] `MemoryList.tsx` now uses native draggable rows (`draggable` + `onDragStart`/`onDragOver`/
      `onDrop`/`onDragEnd`) and a pure `reorderByIndex(from,to)` core. rbd import removed. Same
      optimistic snapshot update + `REORDER` command to the backend — only the drag mechanism changed.
- [x] Step-by-step `[MemoryList][drag]` logs: GRABBED / MOVE / DROP / REORDERED / send, with
      before/after key arrays and indices.
- [x] `window.__mlReorder(from,to)` hook triggers the whole pipeline without a physical drag (for
      occluded tabs and automated tests).
- [x] Verified live in a HIDDEN tab (which rbd could not survive): dispatched real
      `dragstart→dragover→drop` reorders correctly and logs every step; `__mlReorder` reorders
      correctly. Backend `MemoryListReorderTest` passed 10/10 in the user's `mvn` build.
- [x] Backend unchanged (`MemoryListReorder` + `REORDER` command still correct). React commit
      `033afec`; deployed bundle `main.98eaaef4.js`.
- [ ] User: rebuild the jar to pick up the native-drag bundle; drag then works with a real mouse
      in the foreground Memory List window.

Note: `MemoryDragDemo.tsx` (the `?memoryDragDemo=1` scratch bench) still uses rbd; the real
`MemoryList` and `?memoryListDemo=1` are native and robust.

## CODEX — Independent Command Editor CRUD investigation (2026-07-24)

Task thread: the detached Command Editor opens empty and must become an independent, real-time CRUD
workspace with Block/Instruction selection, every Web Field, and every command.

Detailed roadmap:
`ROADMAP_COMMAND_EDITOR_INDEPENDENT_CRUD_2026_07_24.md`

### Confirmed immediate defect (fixed in the checkpoint below)

- [x] Java already merges variables, Web Fields, Blocks, command definitions, the selected draft,
      graph revision, and capabilities into `commandEditor.workspaceBootstrapResponse`.
- [x] `CommandEditorPage.tsx` consumes only the target from that response and discards the merged
      editor data.
- [x] The child then attempts a redundant `commandEditor.bootstrap`.
- [x] The child passive effect can run before the parent effect assigns `targetRef`; the request is
      silently skipped or carries a stale binding.
- [x] `commands`, `webFields`, and `graphRevision` therefore remain empty and the command actions
      stay disabled.
- [x] P0 implementation: consume one atomic workspace snapshot, synchronize the target before child
      use, remove the duplicate bootstrap dependency, and add loading/error/retry states.

### Confirmed independent-CRUD gaps

- [x] The detached workspace is bound to one fixed instruction, not one Bot Job with mutable
      Block/Instruction selection.
- [x] Bootstrap has no complete `instructions` collection or selected-Block state.
- [x] Create-before, create-after, and edit already exist through `commandEditor.apply`.
- [x] Detached command deletion and first-command creation in an empty Block do not exist.
- [x] Command Editor mutations update Bot Job Details, but Bot Job Details/Page Scanner mutations do
      not currently push an authoritative snapshot back into Command Editor.
- [x] Existing safe delete/graph logic is embedded in the generic WebSocket mutation path and must
      be extracted rather than exposing raw `UPDATE_BLOCKS` to the detached page.
- [x] Current reads use shared mutable `PerformLists`; complete CRUD needs immutable owner-scoped
      read DTOs.
- [x] Existing mutation paths require a transaction/generated-key/owner-scope audit before they can
      be called complete CRUD.
- [x] At investigation time no detached Command Editor hydration/lifecycle/realtime integration
      test existed. The first-open and selection-correlation React coverage was added in the
      implementation checkpoint below; lifecycle and two-way realtime integration remain open.

### Read-only data evidence

The production SQLite database was queried read-only on 2026-07-24. Bot Job 5 contained 16 Blocks,
150 Instruction rows, 139 native Web Field rows, and 11 command rows at inspection time. Across all
jobs, 62 distinct raw action values existed. The data is present; the empty page is not caused by an
empty database.

### Agreed implementation order

- [x] Phase 0: fix first-open hydration and add a page-level regression test.
- [ ] Phase 1: freeze the supported-command and historical-row classification.
- [ ] Phase 2: return one immutable complete workspace snapshot.
- [ ] Phase 3: add backend-authoritative Block/Instruction selection. The non-empty
      Block/Instruction slice is implemented; empty-Block selection and deletion recovery remain.
- [ ] Phase 4: extract one owner-scoped transactional mutation foundation.
- [ ] Phase 5: complete create/update, including empty-Block append.
- [ ] Phase 6: add delete preview, confirmation, atomic delete, and selection recovery.
- [ ] Phase 7: add two-way realtime snapshots across Command Editor, Bot Job Details, and Page
      Scanner.
- [ ] Phase 8: finish UX, focused tests, React build, resource deployment, commit, and push.

### Implementation checkpoint - 2026-07-24

- [x] React hydrates target, Blocks, Instructions, Web Fields, command catalogue, variables, draft,
      capabilities, and revisions from one complete workspace response.
- [x] Detached page mode no longer sends the redundant child bootstrap.
- [x] Added explicit loading, empty, error, and Retry states.
- [x] Backend returns owner-filtered detached copies of all Blocks/Instructions, enriches
      Instructions with production Block metadata, and sorts deterministically.
- [x] Added `commandEditor.select` with registered-transport identity, active-workspace/owner
      validation, Block membership validation, request correlation, `bindingEpoch` rotation, and
      `selectionRevision`.
- [x] Added Block/Instruction selectors and stale request/binding/snapshot rejection in React.
- [x] Failed selections and failed retarget snapshots preserve the previous backend binding.
- [x] Focused React result: 2 suites and 27 tests passed.
- [x] React production build succeeded and was mirrored to backend resources as
      `main.362b81d6.js` and `main.c23c7909.css`.
- [x] Added focused Java source coverage for ordering, Block enrichment, and owner validation.
      Maven/Java execution was intentionally not run under the repository standing rule.
- [ ] Phase-2 immutable direct-read repository remains open. This slice reduces shared-list risk
      with immediate owner-filtered detached copies but does not claim a transactional immutable
      multi-table snapshot.
- [ ] Empty-Block append, typed delete preview/delete, selection recovery, and two-way external
      mutation synchronization remain open.

### Decisions

- D-017: A command remains an Instruction row; do not introduce a parallel command table.
- D-018: One atomic Command Editor workspace snapshot is the hydration and refresh source of truth.
- D-019: Bind the detached editor to the active Bot Job/workspace epoch; keep Block and Instruction
  selection mutable and backend-owned.
- D-020: Display every supported command and every historical command row. Unsupported historical
  rows remain visible and read-only; incompatible commands are disabled with a Java reason.
- D-021: Every successful mutation publishes acknowledgement first, then authoritative snapshots.
- D-022: Do not authorize generic `UPDATE_BLOCKS` from the detached editor. Extract and reuse typed
  mutation services and graph validators.
- D-023: Complete CRUD requires owner-scoped SQL, generated keys, one transaction, idempotent
  request IDs, and stale workspace/selection/content revision rejection.

## CLAUDE ⇆ CODEX — Bot Job Details decomposition Phase 1 review (2026-07-24)

Roadmap:
`BOT_JOB_DETAILS_COMPONENT_DECOMPOSITION_2026_07_24.md`

### Review of Claude's first leaves

- [x] Reviewed `FindBar` commits `d0b82a2` and `0780216`.
- [x] Reviewed `BlockCollapseToggle` commit `35184e4`.
- [x] Confirmed both leaves preserve state and business handlers in `GridItem`.
- [x] Corrected FindBar's effective compiled CSS parity (`250px` width and `10px` right padding).
- [x] Moved the existing Memory-count reopen action into `FindBar`, matching the roadmap boundary.
- [x] Restored the literal `is-collapsed` class for DOM/external-automation parity.
- [x] Recorded one process concern: the initial FindBar extraction normalized many unrelated line
      endings. It did not produce an identified runtime defect, but future leaf commits must avoid
      EOL-only churn to reduce merge-conflict and review risk.

### Next two tasks completed by CODEX

- [x] Added presentational `BlockStatusToggle.tsx` + `.module.scss` + focused tests.
- [x] Added presentational `ExecutionStateOverlay.tsx` + `.module.scss` + focused tests.
- [x] Preserved block-status persistence through the original `handleBlockStatus` callback.
- [x] Preserved execution green/red/yellow animation and row stacking while moving the overlay
      styles out of `Griditem.module.scss`.
- [x] Frontend commit: `eb7b4db`.

### Focused verification and deployment

- [x] Focused Jest: 4 suites, 15 tests passed. No complete test suite was run.
- [x] React production build succeeded with existing lint warnings.
- [x] Deployed `main.d40f66d4.js` and `main.f87986ad.css` to
      `src/main/resources/build`.
- [x] Verified source/deployment parity: 45 files on each side and no relative-path/SHA-256 delta.
- [x] Maven/Java compilation was not run.

Next unclaimed leaf: `EmptyBlocksPlaceholder`.

## CODEX - GridItemComp production parity and component-safe Memory flow (2026-07-27)

### Production diagnosis

- [x] The former `GridItemComp.tsx` was an independent legacy copy of `GridItem.tsx`. Its 2,400+
      lines had drifted from the canonical Bot Job grid, so later button, command, drag/drop,
      Memory List, validation, and realtime fixes were present in only one copy.
- [x] Component row and block requests did not have one explicit routing policy. A forged or stale
      target session could therefore select the wrong table family.
- [x] The component blue-arrow path bypassed the global Memory List and used the legacy direct
      injection pipeline, whose multiple connections/commits could leave a partially copied graph.
- [x] Component mutations could be followed by a stale process-cache snapshot, visually undoing a
      successful database write.
- [x] Instruction-array-only snapshots dropped empty component Blocks, so empty Blocks disappeared
      after refresh and could not be deleted, rolled back, or reordered reliably.
- [x] Component block reorder previously renumbered cached rows instead of persisting the submitted
      permutation. Block status also used two independent commits for the Block and its children.

### Frontend correction

- [x] `GridItemComp` is now a thin wrapper around the canonical `GridItem` with
      `workspaceMode="COMPONENT"`. All row/block buttons, command actions, status controls,
      move arrows, drag/drop handlers, find behavior, delete/rollback behavior, and Memory buttons
      are therefore the same implementation rather than a second copy.
- [x] Added one typed workspace policy:
      `componentTasks` + `componentsUpdate` + `COMPONENT_ROW_MOVE`.
- [x] Preserved the Components block-header design in `Griditem.module.scss`, including the dark
      blue `#0b5394` background, 20px height, white text, radius, padding, and shadow.
- [x] The row `+` stages one component instruction in the global Memory List.
- [x] The block `+` stages every eligible instruction in the block.
- [x] The blue arrow stages one typed whole-component Block in Memory and never sends
      `COMPONENT_INJECT`.
- [x] Component Memory items carry stable keys, authoritative source IDs, and a graph revision.
      Duplicate clicks are de-duplicated; stale source revisions are refused by the backend.
- [x] Full component Block catalogues are maintained independently from instruction rows, retaining
      empty Blocks across initial bootstrap and authoritative updates.
- [x] Empty Blocks are real row-drop targets. Moving a row into an empty component Block uses the
      same validated `COMPONENT_ROW_MOVE` transaction and does not delete unrelated pre-existing
      empty Blocks.
- [x] The first Components connection now requests an explicit bootstrap after its physical socket
      is registered, eliminating the former first-open race where the Java snapshot was published
      before the browser could receive it.
- [x] Bootstrap success is acknowledged before grid publication, but publication completion is now
      observed. A presentation-thread refresh failure emits a correlated
      `instructionEditor.resyncRequired` to the same physical page instead of leaving an empty
      Components grid with a false green status.
- [x] A failed authoritative refresh after a committed mutation now raises
      `instructionEditor.resyncRequired`; the grid disables further mutation until refreshed.
- [x] Refused component mutations also reload the authoritative snapshot, restoring any
      optimistically changed empty-Block catalogue, row order, status, or name.
- [x] The detached Components header now reports controller bootstrap state and exposes an
      explicit Refresh action. The Command Editor accepts and hydrates both typed
      `botJobTasks` and `componentTasks` workspaces.

### Backend correction

- [x] Direct `COMPONENT_INJECT` is refused with instructions to use Memory List.
- [x] `ComponentMemoryApplyService` performs mixed Memory apply on one connection and one
      transaction. It validates Bot Job ownership, component ownership/revision, target Block,
      command parents/GOTO Blocks, conditionals, variables, references, generated IDs, concurrent
      layout changes, and idempotent request IDs before commit.
- [x] Component instruction moves validate every destination Block against the active organization
      before updating any row.
- [x] Component Block order uses the complete submitted owner-scoped permutation and one
      transaction; unknown, duplicate, missing, or non-contiguous entries are rejected.
- [x] Block active status and all owned child instruction statuses are persisted in one transaction,
      including empty Blocks.
- [x] Component create/rollback/split, Command Editor, and Variable Editor paths now use
      owner-scoped validation and authoritative component snapshots.
- [x] The physical WebSocket session is authoritative. Component transport cannot relabel itself as
      Bot Job Details; the only cross-surface operations are exact test/hover destination mappings.
- [x] Bot Job grid writes also require the exact currently registered `botJobTasks` physical
      transport and the active registry binding. Submitted Bot Job/organization identity is
      validated and canonicalized before any table is read.
- [x] Authorization failures for graph preview, Memory capabilities, and Variable Editor operations
      are returned directly to the offending physical connection; a forged logical session cannot
      inject a correlated refusal into another page.
- [x] Component Test Click/Input resolves the source instruction freshly for each request so an ID
      collision with a Bot Job instruction cannot execute the wrong row.
- [x] Partial row moves resolve an omitted parent from its stored authoritative Block. Rollback
      normalizes the sole surviving destination Block to order 1.
- [x] Destructive Block rollback validates the submitted instruction graph revision and the
      complete pre-change Block catalogue inside the same database transaction. A concurrent row
      move or newly-created/reordered empty Block therefore refuses the stale rollback instead of
      being overwritten or deleted.
- [x] Block catalog loading now includes `export_file`, so empty component Blocks retain their
      Excel export metadata through bootstrap, refresh, and mutation recovery.
- [x] Early licence/routing failures and capability/editor authorization failures are returned to
      the offending physical WebSocket transport. Secondary cache reload failures are preserved
      without replacing the original mutation result.

### Focused verification

- [x] React focused regression result: 8 suites, 32 tests passed.
- [x] Covered component row/block Memory staging, blue-arrow Memory-only behavior, stable
      de-duplication, response correlation, component row-move routing, empty-Block persistence,
      empty-Block drop targets, complete rollback catalogues, block reorder payloads, component
      Command Editor hydration, and Components header refresh/status behavior.
- [x] Added focused Java source tests for Memory apply transaction/rollback/idempotency,
      component block create/rollback/status/order, foreign move destinations, WebSocket route
      authorization/correlation, component bootstrap with empty Blocks, snapshot policy, stale
      rollback revisions, concurrent empty-Block catalogue changes, and correlated bootstrap
      publication failure recovery.
- [x] Java/Maven tests were intentionally not run for this checkpoint.
- [x] Follow-up deployment on 2026-07-27: `npm run build` completed successfully with warnings,
      producing `main.2e8a4913.js` and `main.3000f6dd.css`.
- [x] Mirrored the React `build` directory into `src/main/resources/build`: 45 source files,
      45 deployed files, and zero relative-path/SHA-256 differences.

### Decisions

- D-024: Maintain one canonical instruction-grid implementation; select table/session behavior
  through a typed workspace policy.
- D-025: Components are reusable source data. Applying a component to a Bot Job must always pass
  through the global Memory List and its transactional backend service.
- D-026: A committed component mutation is followed only by an authoritative database reload.
  Never publish the legacy nested component cache as a fallback.
- D-027: Empty Blocks are first-class workspace entities and must be transported separately from
  instruction rows.
- D-028: Logical session IDs inside JSON are routing metadata only. Physical registered transport
  identity plus the active workspace registry are the authorization boundary for grid writes.
- D-029: A refused optimistic component mutation still requires an authoritative database snapshot;
  generic process-cache snapshots are never a recovery source.
- D-030: A destructive rollback must validate both the instruction revision and complete Block
  catalogue on the same transaction/connection used for its writes; a preflight check on another
  connection is diagnostic only and is never the concurrency boundary.

## CODEX - Nullable deletion and authoritative Component block staging (2026-07-27)

### Incident correction

- [x] Traced the production delete failure to null unboxing of `SplitDTO.getParentId()`.
- [x] Confirmed null parents are valid root metadata, including Component-derived Click rows.
- [x] Changed delete execution to use authoritative stored metadata.
- [x] Ordinary parent deletion now resolves a transitive dependent closure and uses the existing
      atomic graph-delete transaction for instructions, variables, and references.
- [x] Backend fix committed and pushed as `c3422325`.

### Component mapping

- [x] Identified the requested Components display-order block 18, `Check payment`, as database
      `component_block.id = 36`.
- [x] Mapped its 15 instructions, two variables, 25 references, IF/ELSE/ENDIF, LOOP, PAUSE,
      GET/CK/E, parent links, and variable links.
- [x] Found that the existing block `+` was filtering the aggregate through row-level `canAdd`,
      staging only instructions 42 and 43 and omitting the dependent graph.
- [x] Routed the existing Components block-header `+` to one typed whole-block Memory item.
- [x] Kept the obsolete blue arrow removed.
- [x] The existing transactional `ComponentMemoryApplyService` remains the sole whole-block copy
      path and remaps generated block, instruction, variable, reference, parent, and GOTO IDs.
- [x] Frontend source committed and pushed as `c8ca242`.
- [x] `npm run build` completed successfully with existing repository warnings; broad tests were
      intentionally deferred.
- [x] Deployed `main.5fdca76c.js` and `main.73f5e771.css` to backend resources.
- [x] Verified deployment parity: 45 source files, 45 target files, zero SHA-256 differences.

### Roadmap

- [x] Added
      `ROADMAP_COMPONENT_MEMORY_VARIABLE_AND_MULTI_EXECUTION_2026_07_27.md`.
- [x] Updated `specifications/VARIABLE_SYSTEM_REDESIGN.md` with Component aggregate rules.
- [ ] Next safe backend phase: one shared transitive dependency-closure service for preview,
      Memory selection, copy, move, and cascade deletion.
- [ ] Easiest independent dashboard phase: add the non-executing `Execution` checkbox column.
- [ ] Searchable Application Type, Name/Description editing, and authoritative realtime metadata
      publication follow.
- [ ] Headed/headless multi-launch and its tests remain explicitly last.

### Decisions

- D-031: A null parent is valid root metadata. Delete and copy paths must remain nullable end to
  end and may never infer corruption from null alone.
- D-032: A whole reusable Component block is one versioned aggregate. The block `+` stages one
  typed `BLOCK` item and never a filtered list of instruction items.
- D-033: Multi-launch cannot wrap the current single Launch call in a loop. It requires an
  explicit run coordinator, execution mode, isolation/concurrency policy, per-job state, and
  cancellation.

## CODEX - Variables Phase 0A started (2026-07-27)

### Read-only production audit

- [x] Audited `D:\Projects\ARWebBancaStato\ARWeb\database.db` without changing rows.
- [x] Found 22 Bot Job variables and 5 Component variables.
- [x] Found zero orphan or cross-owner variable links and zero missing variable owners.
- [x] Found one duplicate owner: Component instruction 44 owns variables 1 and 2; variable 2 is
      unused.
- [x] Found stale `variable_id=1` on Component Wait instruction 45.
- [x] Found Bot Job 18 cloned consumers 190-192 with missing parents; their Component source
      196-198 is structurally correct.
- [x] Confirmed current producer ordering has no GET-after-consumer violation.

### Backward-compatible implementation

- [x] Added canonical variable action/name policy.
- [x] New variables receive `VAR-<instructionId>-<normalized instruction name>`.
- [x] The Variable Editor/create-variable path refuses new duplicate declarations for one owner
      instruction transactionally.
- [x] Existing-variable updates require variable ID + selected Web Field + workspace owner, which
      prevents a stale/forged ID from modifying another Web Field's declaration.
- [x] Dependent operation loading and rewriting also require the selected `variable_id`, preventing
      cross-rewrites when legacy duplicate declarations share one Web Field.
- [x] Recorded that this is not yet a global database invariant; copy/import bypass paths remain
      in Phase 0B scope until repaired data can receive unique indexes.
- [x] GET ordering now protects E, CK, PDF CHECK, and CSV CHECK during row movement.
- [x] Component/Memory dependency normalization uses the same consumer policy.
- [x] Component Memory apply rejects E/CK/PDF CHECK/CSV CHECK selections without their matching GET
      and leaves the target Bot Job unchanged.
- [x] Preserved current SET runtime semantics: SET writes a literal and is not yet required to
      follow GET.
- [x] Focused Maven verification: 45 tests passed with zero failures/errors; no broad suite was
      run.
- [ ] Next: repair/audit service, explicit backup, deterministic legacy cleanup, then unique
      owner-instruction indexes.

### Decisions

- D-034: `variable.instruction_id` owns the Web Field declaration; GET is the runtime producer
  command referencing that declaration.
- D-035: Do not add a uniqueness constraint until duplicate/stale production rows have been
  reported and deterministically repaired.
- D-036: SET cannot be validated as a GET consumer until the Engine distinguishes literal SET from
  variable-source SET and executes those modes accordingly.

## CODEX - Detached Variables relationship workspace (2026-07-27)

### Bot Job-scoped implementation

- [x] Added a **Variables** action to Bot Job Details.
- [x] Added one fixed detached `variablesManager` page; reopening retargets/focuses the singleton
      instead of creating duplicate Variables windows.
- [x] Bound page authority to the active Bot Job Details registry and exact live
      `botJobTasks` transport. Browser-supplied Bot Job identity is not accepted as authority.
- [x] Added Pages Open presentation, native focus participation, local Close behavior, reload
      generation/grace, retirement tombstone, and forced-close fallback.
- [x] Added a Bot Job-scoped backend graph containing declarations, Blocks, commands, active state,
      `DECLARES`/`WRITES`/`READS`/`ASSIGNS_LITERAL`/`INVALID_LINK` edges, summary, revision, and
      diagnostics.
- [x] Effective health/order diagnostics consider active instructions in active Blocks while still
      transporting inactive links for authoring visibility.
- [x] Persisted variable and instruction graph mutations queue an exact-Bot-Job realtime update.
      Per-step execution/status traffic does not rebuild or republish this graph.
- [x] Kept registry access, SQL, and WebSocket sends outside the Variables state monitor; focused
      concurrency coverage verifies the previous lock-inversion path cannot deadlock.
- [x] Sanitized graph-load failures before they cross the WebSocket boundary.

### React relationship explorer

- [x] Added a TEMP-pattern detached page with title/status, Pages counter, local Close, summary,
      Find, health filters, Expand all/Collapse all, and responsive independent scrolling.
- [x] Added a collapsible variable tree and selected flow:
      declaration Web Field -> GET producer -> variable memory -> E/CK/PDF CHECK/CSV CHECK readers.
- [x] Rendered current SET compatibility separately as
      `literal SET -> declaration Web Field`; the UI does not falsely claim current SET reads a
      prior GET variable.
- [x] Added separate selected-variable and whole-Bot-Job diagnostics.
- [x] Added strict canonical snapshot parsing. A malformed variable/command/edge or incomplete
      revision/binding cannot replace the last valid graph.
- [x] Added request correlation, older-workspace rejection, same-workspace binding rotation,
      10-second request timeout, first-load Retry, and last-valid-snapshot preservation.
- [x] Frontend source committed and pushed as `e05503e`.

### Verification and deployment

- [x] Focused backend result: 22 tests passed with zero failures/errors.
- [x] Focused frontend result: 4 suites, 29 tests passed with zero failures.
- [x] `npm run build` completed successfully with existing repository warnings.
- [x] Produced `main.f69dd91d.js` and `main.3451644a.css`.
- [x] Mirrored the React build to backend resources: 45 source files, 45 target files, and zero
      relative-path/SHA-256 differences.
- [x] Production database rows were not modified.
- [ ] P3 remains deferred: execution initial/current value streaming, pause-time value editing,
      and Resume require a safe Engine run-scoped variable API.
- [ ] Organization-wide variable expansion is deferred until the Bot Job-scoped execution model
      is runtime-accepted.
- [ ] Shared hardening follow-up: all fixed detached pages should receive one-use launch nonces
      before session takeover. Variables currently follows the same loopback-only fixed-session
      boundary as the existing fixed pages.

### Decisions

- D-037: Variables P2 is scoped to one authoritative active Bot Job. Organization aggregation is
  a later expansion and cannot mix execution memories across Bot Jobs.
- D-038: The declared relationship graph and live execution values are separate contracts.
  P2 exposes declarations/relationships; P3 owns run-scoped initial/current values.
- D-039: Current literal SET is an assignment into the declaration Web Field, not a variable
  consumer. The UI and edge direction must preserve actual Engine behavior.
- D-040: A failed or stale refresh never clears the last valid Variables graph.
- D-041: Realtime Variables publication is mutation-driven and revision-deduplicated; execution
  paint/status events are not graph mutations.

## CODEX - React-owned exact instruction deletion (2026-07-28)

- [x] Replaced Java positional IF/LOOP delete expansion with a strict versioned exact-ID contract.
- [x] React calculates the delete set from the rendered graph and uses the same immutable plan for
      the confirmation modal and WebSocket request.
- [x] Conditional deletion includes linked boundary rows only; positional body rows are preserved.
- [x] LOOP deletion includes its explicit anchor family only; positional body rows are preserved.
- [x] Parent ownership remains Block-local, while explicit variable producer/consumer dependencies
      may cross Blocks inside the same owner.
- [x] React supplies explicit surviving-parent repairs; Java persists those repairs and the exact
      confirmed IDs in one database transaction.
- [x] Removed the legacy `InstructionDeleteImpactService` and hidden sole-EXCEL-GOTO deletion path.
- [x] Focused verification: 44 React tests and 7 Java tests passed.
- [ ] Manual verification remains on a disposable production-shaped Bot Job copy.
- [x] Added the shared active tracker:
      `specifications/migrations/ACTIVE_BUGS_TO_FIX_2026_07_28.md`.

### Decision

- D-042: DELETE_INSTRUCTION semantics belong to the React/TypeScript rendered-graph planner.
  Java accepts contract version 2 only, validates request/revision/owner integrity, and persists
  exactly the submitted instruction IDs and parent repairs without semantic group expansion.
