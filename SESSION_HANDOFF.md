# Session Handoff

Date: 2026-07-14

## Repository

- Path: `D:\Projects\AllinWeb\ar-web-selenium`
- Branch: `refactor/perform-actions-decomposition`
- Current status when written: only `SESSION_HANDOFF.md` is being modified for this handoff update; no migration source changed in this terminal
- Backend base commit for this handoff: `909abdd0 docs: add migration document index to handoff`

Recent backend commits before this handoff update:

```text
909abdd0 docs: add migration document index to handoff
7135ad8b refactor: remove retired bot job details legacy blocks
0a42bb20 refactor: isolate bot job details presentation
b582dbd3 refactor: retire bot job details scene
562c4eda refactor: detach bot job details from pane lifecycle
```

## User Constraints

- Continue the migration away from JavaFX.
- The user explicitly selected the Scanner / AR Web Factory migration as the next large initiative and authorized starting it in the next terminal.
- Scanner-related React and backend/service changes are in scope for that initiative.
- Do not modify the Bot Job Details design as part of the Scanner work.
- The user explicitly authorized committing and pushing this handoff-only update.
- Do not infer authorization to deploy artifacts or mutate production data.
- This terminal was explicitly limited to updating this handoff. No migration implementation, test, build, package, deployment, or runtime validation was performed here.
- Production data/config available for runtime validation when needed:
  - Database: `D:\Projects\ARWeb-Linux\ARWeb\database.db`
  - Production config: `D:\Projects\ARWeb-Linux\Config-4.2\ARWeb.config`
- Automated/backend tests should use:
  - `D:\Projects\AllinWeb\ar-web-selenium\Config-4.2\TESTS.config`
- Preserve production data. Runtime navigation is OK; avoid destructive mutations.

## Current Migration State

Completed and pushed:

- `ARViewBotJobPane.java` was replaced by `BotJobDetailsWorkspaceHost.java`.
- `BotJobDetailsWorkspaceHost` no longer extends `ARPane`.
- `ARViewBotJobScene.java` was deleted.
- All source callers were redirected to `ARMainDashboardPane.openBotJob(...)`.
- `BotJobDetailsPresentationPort` was added.
- `ARMainDashboardPane` now owns the JavaFX presentation duties for Bot Job Details:
  - JavaFX thread execution
  - the single React WebView surface
  - organization modal presentation
  - scanner modal open/close/current-job
  - test-run delegation to `ARScannedElementPane`
  - native directory/report choosers
  - window title updates
- `BotJobDetailsWorkspaceHost` compiles with no direct JavaFX imports and no direct dependency on:
  - `ARMainDashboardPane`
  - `ARScannedElementPane`
  - `ARScannedElementScene`
  - `AROrganizationManagerScene`
- `BotJobDetailsJavaFxRetirementTest` was added to assert the pane/scene retirement boundary.
- Clone Job React/backend migration was already completed before this handoff:
  - backend clone contract/service implemented
  - React Clone Job implemented/deployed
  - `ARSaveClonePane` and `ARSaveCloneScene` deleted

## Next Initiative Selected: Scanner / AR Web Factory

The user selected the largest remaining JavaFX surface as the next initiative:

> Scanner / AR Web Factory: migrate reachable controls and lifecycle to React/services, decouple
> `ARScannedElementPane` / `ARScannedElementScene`, then delete them.

This is explicit authorization to begin that initiative in the next terminal. Do not continue it in
the terminal that wrote this handoff.

### Current backend inventory

- `ARScannedElementPane.java` still exists and is approximately 10,075 lines / 488 KB.
- `ARScannedElementScene.java` still exists and is approximately 975 lines / 47 KB.
- `AbstractARScannedElementPane.java` is a 14-line zero-reference residue; it can be removed after a
  normal zero-reference audit, but its deletion alone is not meaningful migration progress.
- The pane and scene have an eager circular singleton dependency and are not presentation-only:
  - the pane initializes `ARScannedElementScene.getInstance()`
  - the scene initializes `ARScannedElementPane.getInstance()`
  - the scene directly reads/writes pane controls and calls pane business methods
- The pane currently mixes:
  - JavaFX controls and WebView ownership
  - browser/tab/DOM operations
  - TEST RUN / STOP and terminal-result behavior
  - block creation and persistence glue
  - a roughly 2,376-line `executeJob(...)` execution engine
  - Appium/Web XML parsing and scan DTO construction
  - WebSocket publication, filesystem/CSV work, OCR, support, and plugin management
- The scene currently mixes:
  - current-job/session initialization and loopback WebSocket ownership
  - JSON command routing
  - Stage/modal and browser shutdown
  - element insert/update/test persistence and status publication
- Important active couplings remain:
  - `ARMainDashboardPane` owns scanner open/close/current-job presentation and delegates TEST RUN calls to the pane
  - `ConfigService` and `ARConfigurationPane` close scanner scene/drivers
  - `PerformListElements` and `SimpleWebSocketServer` query scanner context through the scene
  - `SimpleWebSocketServer` still calls pane support/DOM handlers
  - `TargetElementHelper` accepts a concrete `ARScannedElementPane` and writes/calls pane state

### Current React inventory

Frontend repository:

- Path: `D:\Projects\AllinWeb\abr-react-ts-grid`
- Branch: `VERSION-4.6`
- HEAD/remote when audited: `d48ecd3f1564eb26f48c5a325dbaac5d6c67f850`
- Worktree is not clean: `src/index.tsx` has a pre-existing user change.
  - Its substantive change hardcodes Bot Job `9` / `Apre Acconto` and Home Banking `2` /
    `Banca Stato` defaults.
  - The diff also contains line-ending churn.
  - Preserve this change; do not overwrite or normalize the file incidentally.

Already present:

- `GridItemScann` supports normal `scannerGrid` and `preScannerGrid` / `mode="preScan"`.
- `ScannerWorkspaceHeader` exists.
- The React scan-results editor already covers grouping, search, pagination, Keep/Delete/Clear,
  Memory List, target block/create/apply, row rename/save/tests, OCR review/config/results, and
  scan-result chunk accumulation.
- Pre Scan already has a React toolbar/status for Page Scanner, OCR, refresh, clear, focus profiles,
  search terms, and status.
- Backend prerequisites already include `PreScanBrowserSession`, `PreScanWorkflowService`,
  `PreScanApplyService`, `PlaywrightElementScanner`, OCR services, `BlockCreationService`, and
  `BotJobScannerCoordinator`.
- `ARPlaywrightDriver` already contains new-tab adoption, newest-page selection, `context.onPage`
  handling, and closed-tab fallback. Parts of the TEST RUN/Page Scanner roadmap are implemented even
  though that document is not checked off.

Still missing in normal AR Web Factory mode:

- A typed scanner bootstrap/state/action contract.
- Authoritative state for job, URL, blocks, browser/active-tab state, focus/search, OCR, capabilities,
  pending actions, and execution state.
- React-owned scanner/browser lifecycle controls; normal `scannerGrid` currently has only the
  identity/status header plus the result editor.
- Request IDs, revisions/correlation, stale-response rejection, consistent timeout/error behavior,
  and disconnected/busy gating.
- Dedicated `ScannerToolbar`, `ScannerExecutionPanel`, controller/reducer, and focused React tests.
- Removal of the hardcoded legacy `scanner-element-pane` destination used by insert/update/block/row
  messages. Keep it or provide an alias until each command has migrated.
- Explicit retained TEST RUN browser result state and a `Scan Test Result` source-selection flow.

### Visibility and scope rule

Before migrating any control, verify that it is visible and reachable in the current legacy pane.
Do not revive hidden/dormant controls without a product decision.

Currently excluded unless the user changes that decision:

- Clone / Hover Pick
- Send DOM / HTML review
- Request Support
- plugin update controls
- legacy hidden-field toggle
- other controls whose legacy JavaFX node is `setVisible(false)`

The older Pre Scan roadmap says `ARScannedElementPane` stays for other clients. That statement
conflicts with the newly selected global pane/scene retirement goal. Treat the user's latest explicit
selection as authoritative, while preserving client behavior during staged migration.

### Recommended first work in the next terminal

Do not begin by editing or deleting the 2,376-line `executeJob(...)` method.

1. Recheck both repository states and preserve unrelated changes.
2. Re-audit visible/reachable normal Scanner controls against current source; roadmap inventories are
   partly stale.
3. Create a small JavaFX-free scanner selection/execution context and change `TargetElementHelper` to
   depend on that context/callback interface instead of `ARScannedElementPane`. Keep a pane adapter so
   behavior is unchanged and add focused mapping tests.
4. Start the first end-to-end vertical slice:
   - typed `scanner.bootstrap` and `scanner.state` payloads
   - one correlated `scanner.action` envelope with `requestId`, session/job validation, pending state,
     structured success/failure, and stale-response rejection
   - React `Scanner.contract.ts` and `useScannerController.ts` with focused tests
   - render one already-visible, read-only scanner action and live status in normal `scannerGrid`,
     preferably Page Scanner or Refresh after confirming the reachable legacy behavior
   - reuse the extracted Pre Scan/Playwright services rather than copying pane logic
5. Keep the existing DTO grid and legacy `scanner-element-pane` persistence routes working during this
   first slice.
6. Remove a JavaFX control only after its React replacement passes contract, loopback, and runtime parity.

Recommended later slices:

1. Read-only scan controls: focus/search, Page Scanner, refresh, clear-grid, status, and active-tab/browser state.
2. Scene insert/update/block command routing behind a JavaFX-free service, reusing/generalizing
   `PreScanApplyService`.
3. Row tests, block creation/apply, OCR, and remaining reachable result-editor commands.
4. Pre-Launch, TEST RUN/STOP, execution ownership, and immutable run state.
5. Browser/session shutdown and configuration/dashboard caller decoupling.
6. Zero-reference audit, then retire `ARScannedElementPane`, `ARScannedElementScene`, and
   `AbstractARScannedElementPane`.

### Scanner validation gates

- Focused Java service/contract tests for session/job isolation, request replay/correlation, stale
  responses, browser ownership, and lifecycle races.
- Focused React controller/component tests for connected/disconnected, busy/disabled, success,
  failure, timeout, keyboard behavior, and one request per action.
- Localhost/mock-socket Playwright, then real loopback integration.
- Same-tab, new-tab, multiple-tab, closed-tab, isolated Pre Scan, double-click, STOP, and shutdown cases.
- Scanner row tests, block create/apply, OCR, element persistence, and browser shutdown must pass before
  pane/scene deletion.
- Use isolated test data for mutation-capable tests; keep production database/config read-only.
- Run `git diff --check`, backend compile/focused tests, frontend tests/build, deployed build
  manifest/hash comparison, and eventually the full backend suite.
- Do not claim retirement until a zero-caller audit and desktop runtime parity are recorded.

## Verification Already Done

Backend focused non-browser suite:

```text
98 tests, 0 failures, 0 errors, 0 skipped
```

Compile/test-compile after the current refactor:

```text
317 main sources
87 test sources
```

Package:

```text
mvn -DskipTests package
```

Packaged/deployed JAR:

```text
SHA-256: F880EED77054AA131F5F464F7DAB826BF9E1871196DB5ADC265D718C969F55F7
Target:  D:\Projects\ARWeb-Linux\ARWeb-Scanner\AR_Web_Scanner-4.2.jar
Backup:  D:\Projects\ARWeb-Linux\ARWeb-Scanner\AR_Web_Scanner-4.2.jar.20260714-035759.bak
```

Runtime launch:

- The deployed app launched successfully.
- Dashboard title observed: `AR Web Main Dashboard`.
- Screenshot path: `D:\Projects\AllinWeb\ar-web-selenium\target\runtime-dashboard-retired.png`
- The screenshot showed the production dashboard and Bot Job rows.

Playwright/browser status:

- A focused browser run reached the UI and failed on the two known blockers only:
  - metadata `Edit` entry point is missing
  - `CREATE_BAT` is covered by another layer
- These were known before this stop point and are not new regressions from the pane/scene retirement.
- A sandboxed browser run also failed with `spawn EPERM`, which is an environment limitation.

## Not Proven Yet

Do not mark the migration complete yet.

Still missing:

- Runtime close/reopen validation for Bot Job Details.
- Runtime A -> B Bot Job switching validation.
- Confirmation that opening Bot Job Details no longer creates a separate `ARViewBotJobScene` modal/window.
- Full backend suite after final cleanup.
- Roadmap/checklist updates with the final evidence.

There was an attempted Windows mouse automation after runtime launch. It did not prove A -> B switching because the follow-up screenshot captured only the terminal, not the dashboard. Treat runtime A/B validation as still pending.

## Cleanup Completed

`BotJobDetailsWorkspaceHost.java` no longer contains the retired implementation blocks that had been left inside comments:

- `/* Retired embedded Bot Job WebView implementation.`
- `/* Retired duplicate direct Scanner scene launcher.`

After removal, `mvn -DskipTests test-compile` passed on 2026-07-14 with 317 main sources and 87 test sources compiled.

## Roadmaps To Update Later

Only update these after final runtime evidence and full-suite evidence are collected:

- `specifications/migrations/CLAUDE_vs_CODEX_MIGRATION_CHECKS_2026_07_12.md`
- `specifications/migrations/ROADMAP_REMAINING_LEGACY_PANELS_REACT_2026_07_12.md`

Relevant current stale sections:

- `CLAUDE_vs_CODEX_MIGRATION_CHECKS_2026_07_12.md` around Task 1, near the `ARViewBotJobPane` reduction checklist.
- `ROADMAP_REMAINING_LEGACY_PANELS_REACT_2026_07_12.md` around Phase 2D and the 2026-07-14 log.

## Important Documents

Read first:

- `SESSION_HANDOFF.md` — current resume point, selected Scanner initiative, repository state, scope,
  first-slice recommendation, and validation gates.
- `specifications/migrations/ROADMAP_REMAINING_LEGACY_PANELS_REACT_2026_07_12.md` — umbrella roadmap;
  read Phase 4, but verify every claim against current source because parts are stale.
- `specifications/migrations/ROADMAP_PRE_SCAN_REACT_DASHBOARD.md` — completed Pre Scan capabilities,
  extracted services, visibility rule, and behavior that should be reused.
- `specifications/migrations/ROADMAP_TEST_RUN_PAGE_SCANNER_SESSION.md` — active-page/browser-source
  lifecycle and validation matrix; some driver work is already implemented but unchecked.
- `specifications/migrations/CLAUDE_vs_CODEX_MIGRATION_CHECKS_2026_07_12.md` — Bot Job Details,
  TEST RUN, execution semantics, and JavaFX-retirement cautions.

Migration roadmaps:

- `specifications/migrations/ROADMAP_CLONE_JOB_REACT_BACKEND.md`
- `specifications/migrations/ROADMAP_PRE_SCAN_REACT_DASHBOARD.md`
- `specifications/migrations/ROADMAP_TEST_RUN_PAGE_SCANNER_SESSION.md`
- `specifications/migrations/ROADMAP_NEW_BOT_JOB_REACT_BACKEND.md`
- `specifications/migrations/ROADMAP_NEW_ORGANIZATION_REACT_BACKEND.md`
- `specifications/migrations/ROADMAP_CONFIG_PAGE_REACT_BACKEND.md`
- `specifications/migrations/ROADMAP_OCR_CONFIG_RESULTS_REACT_BACKEND.md`
- `specifications/migrations/ROADMAP_MAIN_PAGE_REACT_DASHBOARD.md`
- `specifications/migrations/ROADMAP_LICENSE_ABOUT_ACTIVATION_REACT_BACKEND.md`
- `specifications/migrations/ROADMAP_POST_JAVAFX_NODE_TYPESCRIPT_PLATFORM.md`
- `specifications/migrations/ROADMAP_SAVE_COMPONENT_REACT_BACKEND.md`
- `specifications/migrations/ROADMAP_EXCEL_FILE_REACT_BACKEND.md`

Execution and command-logic documents:

- `specifications/migrations/INSTRUCTION_ACTION_CAPABILITY_MATRIX.md`
- `specifications/migrations/INSTRUCTION_COMMAND_RULES_AUDIT.md`
- `specifications/migrations/ROADMAP_COMMAND_CAPABILITY_ENGINE.md`
- `specifications/migrations/ROADMAP_INSTRUCTION_GRAPH_AND_DRAG_DROP.md`

Tracking, notes, and migration cautions:

- `specifications/migrations/MIGRATION_TRACKER_2026-07-11.md`
- `specifications/migrations/MIGRATION ROAD MAP MY NOTES.md`
- `specifications/migrations/IMPORTANTE STEPS MIGRATION.md`
- `specifications/migrations/GUIDANCES CLAUDE vs CODEX.md`
- `specifications/migrations/NEGATIVE IMPACTS MIGRATION.md`
- `specifications/migrations/Playwright_Migration_Roadmap.html`

General project docs:

- `README.md`
- `CLAUDE.md`
- `README-DATABASE.md`
- `README-DEBUG.md`
- `WEBDRIVER.md`
- `WebDriver-With-Load-Wait.md`
- `APPIUM README.md`
- `OCRS README.md`

## Resume Checklist

1. Read the Scanner initiative section above and the three Scanner/umbrella roadmaps listed under
   Important Documents.

2. Check both repositories before editing:

```powershell
cd D:\Projects\AllinWeb\ar-web-selenium
git status --short
git log -5 --oneline

git -C D:\Projects\AllinWeb\abr-react-ts-grid status --short
git -C D:\Projects\AllinWeb\abr-react-ts-grid diff -- src/index.tsx
git -C D:\Projects\AllinWeb\abr-react-ts-grid log -5 --oneline
```

The backend should be clean after this handoff commit is pulled. The frontend already has a
user-owned `src/index.tsx` modification; preserve it.

3. Re-audit active normal Scanner controls and message/caller ownership before choosing removal points:

```powershell
rg -n "setVisible|new Button|Button\(|scanner-element-pane|ARScannedElementPane|ARScannedElementScene" src/main/java
rg -n "scanner-element-pane|scannerGrid|preScannerGrid|ScannerWorkspaceHeader" D:\Projects\AllinWeb\abr-react-ts-grid\src
```

4. Begin with the small `TargetElementHelper` context/callback decoupling and focused tests, then build
   the typed Scanner bootstrap/state/action vertical slice described above.

5. Keep behavior additive during early slices. Do not delete the pane/scene, remove legacy message
   aliases, or touch `executeJob(...)` until equivalent routes and lifecycle evidence exist.

6. After each implemented slice, run proportionate focused Java and React tests. Compile/build when
   required by the implementation. Obtain separate authorization before deployment or production-data
   mutation.

7. Keep the older Bot Job Details proof gaps visible:
   - close/reopen runtime validation
   - Bot Job A -> B switching
   - confirmation that no retired separate scene/window appears
   - full backend suite and roadmap evidence
   - known metadata `Edit` and `CREATE_BAT` Playwright blockers

## Important Reminder

The user has now explicitly chosen Scanner / AR Web Factory as the next migration initiative. The
terminal that wrote this report was limited to the handoff only. The next terminal may begin the
Scanner work from the staged checklist above.
