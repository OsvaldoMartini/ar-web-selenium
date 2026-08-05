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

