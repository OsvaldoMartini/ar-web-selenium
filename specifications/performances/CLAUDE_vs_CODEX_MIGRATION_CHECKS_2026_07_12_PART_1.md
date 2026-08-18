# Claude vs Codex Migration Checks — 2026-07-12

> **Latest Claude ⋈ Codex exchange:** `COPY_LAST_RESPONSE.md` (same folder) — the
> always-overwritten bridge holding only the most recent verdict/response between the
> two assistants.

## CURRENT ROADMAP IN PROGRESS — Variables Command Editor Modal (2026-08-01)

This section is the current shared handoff and must remain at the top of this canonical CODEX/Claude
document. Update it in place; preserve every older roadmap, investigation, and review ledger below.

- Active roadmap: `ROADMAP_VARIABLES_COMMAND_EDITOR_MODAL_2026_08_01.md`.
- Progress: **CE-1 through CE-9 complete; CE-10 remains**.
- Current stop point: CE-9 is committed, pushed, and React-deployed. Wait for the user's runtime or
  Claude review before CE-10.
- Next implementation when authorized: CE-10 acceptance, deferred tests, and realtime verification.
- Session handoff (2026-08-01): work is intentionally paused. The user will run CE-9 acceptance
  tomorrow. Do not start CE-10 or activate typed conditional execution before that result.
- Smoke-test implementation remains a separate later roadmap in
  `Smoke_Test_Roadmap_2026_08_01.md`; it has not been started by CE-7.

### Persistence review requested from Claude

| Repository | Commit | Review focus |
|---|---|---|
| React frontend | `2a22eed` | UPDATE WebSocket request/response integration and authoritative refresh. |
| Java backend | `f273119f` | Same-ID atomic UPDATE, placement, cross-Block movement, relationship clearing, and graph CAS. |
| React frontend | `05e6c22` | Isolated COPY NEW hook, pending lifecycle, action routing, and safe default placement. |
| Java backend | `1259f18b` | Fresh-ID atomic COPY NEW, source preservation, target order normalization, relationship clearing, idempotency, and graph CAS. |
| Java deployed resources | `c9a9395b` | Bundle generated from frontend `05e6c22`. |
| React CE-7 | `ed300a0` | Typed CheckValue, CSV/PDF Check, and ExcelWrite forms and snapshot hydration. |
| Java CE-7 | `88628393` | New typed command configuration table/repository plus isolated UPDATE/COPY persistence. |
| Java deployed resources | `f482756a` | Bundle generated from frontend `ed300a0`. |
| React CE-8 | `1157f10` | Typed GOTO and SWIPE count editors. |
| Java CE-8 | `c5ff63c5` | Isolated GOTO/SWIPE count validation and UPDATE/COPY persistence. |
| Java deployed resources | `641bb887` | Bundle generated from frontend `1157f10`. |
| React CE-9 | `d545b71` | Isolated IF/ELSEIF typed editor; ELSE/ENDIF remain structural. |
| Java CE-9 | `f0e33ef0` | Typed conditional shadow persistence; legacy execution unchanged. |
| Java deployed resources | `56d1942f` | Bundle generated from frontend `d545b71`. |

### Runtime acceptance requested for the next session

1. Open IF and ELSEIF through the Variables green editor; confirm ELSE and ENDIF have no editor.
2. UPDATE `PREVIOUS_RESULT`, reopen the modal, and confirm it remains selected.
3. UPDATE `VARIABLE_COMPARISON`, reopen, and confirm both variables, operator, operand, and format.
4. COPY NEW and confirm a fresh ID, unchanged source, cleared variable references, and
   `PREVIOUS_RESULT` on the copy.
5. Check Top, End, and After placement, then verify Variables and Bot Job Details refresh in real time.
6. Confirm existing production IF/ELSEIF execution is unchanged; typed comparison execution remains
   deliberately inactive until the Variables Operations migration activates it.

CODEX did modify database persistence. COPY NEW uses the new
`VariablesCommandEditorCopyV1`, `VariablesCommandEditorCopyService`, and
`VariablesCommandEditorCopyTransaction` path. It copies the command's intrinsic persisted fields,
creates a fresh instruction ID, inserts at the requested placement, and deliberately clears
`variable_id`, `parent_block_id`, and `parent_id`. It must never modify or remove the source row and
must not create variable-ownership or relationship/reference rows. The transaction verifies the
owner, binding/workspace, graph version, graph revision, contiguous target order, new row, clean
relationships, and unchanged source state before commit.

Verification recorded by CODEX: frontend `npm run build` passed with existing warnings and the
result was deployed. Maven and backend tests were intentionally not run. Claude should treat the
backend persistence review as the acceptance gate before CE-8 begins.

### CE-7 persistence boundary

CE-7 stores intrinsic Check/ExcelWrite settings in
`instruction_variable_command_config`. React owns typed drafts and validation; Java owns the
transaction, graph CAS, durable storage, and committed snapshot publication. The legacy detached
Command Editor, GridItem drag rules, relationship repair rules, and V1 executors were not changed.
For CheckValue, CSV/PDF Check, and ExcelWrite, CE-7 does not overwrite the legacy
`instruction.operation`; the new table is a shadow contract until the complete Variables
operations roadmap explicitly activates V2 execution. COPY NEW still creates a fresh instruction
with command-level parent/Block/variable IDs cleared; a typed operand-variable reference is also
cleared to `VOID` in the copied configuration. Java compilation/Maven and runtime migration
acceptance remain pending at the user's request.

### Adjacent Variables requests — implemented outside the Command Editor sequence

Two additional tasks are not part of CE-7–CE-10. Their implementation is complete and runtime
acceptance is pending:

1. **AV-1 Repeatable Add Variable:** calculate and prefill the next free case-insensitive
   `Variable_X`, support staging multiple names through a mini `ADD` button, persist them through
   `CREATE VARIABLE`, and keep the modal open after success until the user closes it.
2. **AV-2 Block-scoped Release Connections:** reuse the synchronized Block filter from Resolve and
   Review, rebuild the release scope from the current selection, use all Blocks when empty, and
   always open the modal even when the initial Block has zero releasable connections.

The detailed behavior and safety constraints are in
`ROADMAP_VARIABLES_COMMAND_EDITOR_MODAL_2026_08_01.md`. Do not mix either improvement into a CE
commit. Frontend behavior is isolated in `f92c3d5`; the Add Variable route's case-insensitive
authoritative duplicate guard is isolated in backend commit `771a79d8`. The build passed with the
existing warnings and was deployed; Maven/tests were not run.

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

