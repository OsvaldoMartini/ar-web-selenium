# Remaining Legacy Panels to React — Canonical Migration Roadmap

Date: 2026-07-12
Status: Active — Phase 0 and Bot Job Details Phases 2A-2C are delivered, automated-tested, React-deployed, and Java-packaged; desktop runtime validation and the explicitly unchecked lifecycle follow-ups remain pending.

## Objective

Remove every user-facing JavaFX panel while keeping Java as the authoritative backend during this migration. React owns layout, controls, validation display, confirmation, and status. Java owns persistence, execution, browser automation, native file operations, and lifecycle until those services are separately migrated.

This is the canonical umbrella roadmap for the remaining panel work. Existing feature roadmaps remain supporting detail; completion state is recorded here against current source, not against planned component names.

## Current runtime inventory

| Surface | Current state | Required outcome |
|---|---|---|
| Bot Job Details (`ARViewBotJobPane`) | React owns the migrated controls; JavaFX still hosts WebViews and substantial activation, Pre Scan, scanner, native-operation, and lifecycle orchestration | Reduce the pane/scene to a temporary WebView/window host, then retire it after runtime parity |
| Clone Job (`ARSaveClonePane`) | Fully active JavaFX page | React clone form backed by one transactional service |
| AR Web Factory / Scanner (`ARScannedElementPane`) | Active hybrid: JavaFX controls around `GridItemScann` | React scanner workspace with backend-owned scan/browser state |
| Startup/recovery Configuration (`ARConfigurationPane`) | Conditional JavaFX page used before normal services are available | Restricted React recovery mode with a boot-safe IPC bridge |
| Organization/environment editor (`ARNewHomeBankingPane`) | React manager exists, but legacy modal remains reachable | Complete React Advanced environment fields, redirect every caller, retire modal |
| Shared alerts/dialogs | Mixed JavaFX dialogs, React modal, and `window.confirm` | One accessible React dialog/status system using structured backend errors |
| Old main/list/new-job wrappers | Mostly inactive compatibility residue | Zero-reference audit and deletion after active routes are proven |

## Non-negotiable migration rules

- Do not copy database or execution logic into browser React code.
- Every component owns a `.module.scss`; do not add new global page CSS.
- Shared headers are presentational. They receive the WebSocket already owned by the page controller; they never create a second socket for the same session.
- Every mutation uses a typed request with `requestId` and a structured response.
- Backend state is authoritative for job identity, environment, blocks, execution state, disabled actions, and license capabilities.
- Do not expose controls merely because a dormant JavaFX button exists. Scanner, GEN FLOW, and API Tool are currently not mounted in the Bot Job Details toolbar.
- Remove a JavaFX node only in the same slice that provides its reachable React replacement.
- Native file/directory choosers and `Desktop.open` remain behind a native-desktop adapter until a cross-platform replacement is approved.
- Tests never write to the production BancaStato config/database; use isolated snapshots and localhost browser surfaces.

## Shared React component architecture

```text
src/components/workspace/
  WorkspaceHeader.tsx
  WorkspaceHeader.module.scss
  WorkspaceToolbar.tsx
  WorkspaceToolbar.module.scss
  WorkspaceStatus.tsx
  WorkspaceStatus.module.scss
  GridFindBar.tsx
  GridFindBar.module.scss

src/components/bot-job-details/
  BotJobDetailsPage.tsx
  BotJobDetailsPage.module.scss
  BotJobDetailsHeader.tsx
  BotJobDetailsHeader.module.scss
  BotJobMetadataEditor.tsx
  BotJobMetadataEditor.module.scss
  BotJobExecutionControls.tsx
  BotJobExecutionControls.module.scss
  BotJobFileActions.tsx
  BotJobFileActions.module.scss
  BotJobDetails.types.ts
  useBotJobDetailsController.ts

src/components/scanner/
  ScannerWorkspaceHeader.tsx
  ScannerWorkspaceHeader.module.scss
  ScannerToolbar.tsx
  ScannerToolbar.module.scss
  ScannerExecutionPanel.tsx
  ScannerExecutionPanel.module.scss
```

`WorkspaceHeader` is reused by `GridItem`, `GridItemComp`, `GridItemScann`, Pre Scan, and future Clone/Configuration pages. Feature headers compose it and expose only actions valid for their session.

## Phase 0 — shared foundation and first real-button removal

- [x] Create reusable `WorkspaceHeader` with identity, connection, status, typed action buttons, responsive layout, and `WorkspaceHeader.module.scss`.
- [x] Create `BotJobDetailsHeader`, its controller/types, and its own `.module.scss` without creating a second WebSocket.
- [x] Create `ScannerWorkspaceHeader` with its own `.module.scss` and reuse the shared header in normal Scanner mode.
- [x] Mount the Bot Job Details header in `GridItem`, `GridItemComp`, and Pre Scan `GridItemScann` sessions.
- [x] Add `botJobDetails.action` / `botJobDetails.actionResponse` for `REFRESH`, `SHOW_BOT_JOB`, idempotent `SHOW_COMPONENTS`/`HIDE_COMPONENTS`, `SHOW_PRE_SCAN`, and `CLOSE`.
- [x] Validate that the requested Bot Job ID matches the currently open pane before accepting a workspace action.
- [x] Remove the replaced Refresh, Pre Scan, Components, and Close JavaFX buttons and handlers from the mounted Bot Job Details layout.
- [x] Add focused React header tests and a Java action-parser test.
- [x] Add typed `botJobDetails.bootstrap` and `botJobDetails.state` payloads for description, project type, environment, navigation time, blocks, capabilities, and execution state.
- [ ] Move all grid message consumers from “last message only” to cursor/reducer processing so lifecycle events cannot be skipped.
- [ ] Extract shared `GridFindBar` from Bot Job, Component, Scanner, and Mobile grids.
- [x] Add backend contract tests for action envelopes, wrong-job rejection, session isolation, idempotent request replay, optimistic revision conflicts, and atomic metadata publication. Tests are source-ready; Java execution remains user-owned.

Exit criterion: the React header is the only reachable owner of the four migrated controls, and no duplicate socket/session is created.

## Phase 1 — complete the Organization/environment editor

Parity gap: React currently carries `priority`, `searchConfig`, and `optionsConfig` in its data type but does not render/edit them; the legacy modal does.

- [x] Add a collapsible Advanced section for Priority, Search Config, and WebDriver Options, with `OrganizationAdvancedFields.module.scss`.
- [x] Add template/default generation parity and preserve the existing structured backend validation responses.
- [ ] Add `environment.changed` publication so open pages refresh selectors without reopening.
- [x] Redirect the Bot Job Details “Organizations / Environments” action to the React manager.
- [ ] Redirect the remaining Clone Job and Scanner “Organizations / Environments” actions to React.
- [ ] Complete service/WebSocket contract coverage for the Organization editor. The focused React Advanced-fields test is complete.
- [ ] Delete `ARNewHomeBankingPane` and `ARNewHomeBankingScene` only after a zero-caller audit.

## Phase 2 — complete Bot Job Details

### 2A — metadata and environment

- [x] Bootstrap name, description, project type, organization, selected environment, environment URL, blocks, navigation time, execution state, revision, and license-aware capabilities.
- [x] Implement `BotJobMetadataEditor` with Edit/Save/Cancel, stable environment ID selection, edit-base revision preservation, retry, and its own `.module.scss`.
- [x] Extract `updateBotJobDetails` orchestration into a UI-independent direct-database service with transport-bound correlation, structured validation/conflicts, bounded idempotency ledgers, and an atomic persistence/revision transition.
- [x] Remove the JavaFX info-bar identity, Edit/Save, environment selector, refresh-environments, and legacy environment button; React is the reachable owner.
- [x] Handle socket reconnect/bootstrap timeout, malformed/cross-job state rejection, same-revision license restoration, capability revocation, and committed-save/desktop-sync failure without stale re-submission.

### 2B — TEST RUN execution controls

- [x] Implement `BotJobExecutionControls` with block dropdown, reload, ALL/ONE, TEST RUN, STOP, and live state.
- [x] Use stable `blockId`; the server resolves canonical block order and URL. Never trust client order or endpoint URL.
- [x] Preserve the valid selection union: Execute All can only use ALL; a numbered block can use ALL or ONE.
- [x] Publish `IDLE`, `STARTING`, `RUNNING`, `STOPPING`, `PASSED`, `FAILED`, and `INTERRUPTED` terminal events, with executor-owned terminal outcomes and acknowledged delivery.
- [x] Keep controls busy until the owned executor reaches a terminal event, while allowing prompt run-owned STOP during startup.
- [x] Remove the corresponding JavaFX combo, reload, toggle, TEST RUN, and STOP nodes after integrated React/Java/Playwright parity tests.

### 2C — remaining toolbar/file actions

- [x] Migrate Navigation Time through typed state/update operations.
- [x] Migrate Excel open/generate and Report list/open using native-desktop ports and React confirmations.
- [x] Migrate Export, Import, restore date, path selection, and BAT generation, with transactional restore and unique atomic export publication.
- [x] Extract the real external Engine Launch workflow; do not reuse the current Main Dashboard placeholder dialog.
- [x] Migrate Launch with capability/preflight/result events and detached-process collision tracking.
- [ ] Reduce `ARViewBotJobPane` to a WebView/window host, then retire its pane/scene after runtime verification.
  - [x] Extract exact TEST RUN/STOP ownership, startup cancellation, terminal outcome monitoring, and runtime-state publication.
  - [x] Harden job-switch WebView/session/cache teardown and remove the dead API-tool WebView.
  - [x] Extract workspace activation/cache/grid refresh and scanner-selection policy into the JavaFX-free `BotJobWorkspaceService`, with fail-closed cache transitions and focused backend tests.
  - [x] Move the isolated Pre Scan Playwright driver and single-scan lifecycle guard into the JavaFX-free `PreScanBrowserSession`.
  - [x] Extract the reachable Pre Scan refresh/scan/OCR/diagnostic/element-test/status workflow into the JavaFX-free `PreScanWorkflowService`; the pane now supplies only transport/presentation adapters.
  - [x] Extract scanner preparation, launch concurrency, modal open/close, and failure recovery into the UI-independent `BotJobScannerCoordinator`; remove the pane-owned launch flag.
  - [x] Extract Excel/report desktop opening, BAT creation, Engine preflight/command/log construction, detached launch, and collision tracking into the JavaFX-free `BotJobNativeOperationService`.
  - [x] Extract active-operation close gating, WebView/session retirement, registry/transfer cleanup, and Pre Scan shutdown ordering into the UI-independent `BotJobWorkspaceCloseCoordinator`.
  - [x] Extract capability, organization, Pre Scan payload, and empty-grid payload ownership into UI-independent services/coordinators.
  - [ ] Move remaining WebSocket/mobile persistence ownership out of `ARViewBotJobScene`, then complete desktop runtime validation and retirement.
    - [x] Route all `SimpleWebSocketServer` Bot Job workspace calls through the generation-safe `BotJobWorkspaceController` instead of importing the JavaFX pane.

## Phase 3 — Clone Job

Target components:

```text
src/components/clone-job/
  CloneBotJobPage.tsx
  CloneBotJobPage.module.scss
  CloneJobForm.tsx
  CloneJobForm.module.scss
```

- [ ] Add `cloneJob.bootstrap`, `cloneJob.create`, `cloneJob.cancel`, and `cloneJob.openOrganizations` contracts.
- [ ] Preserve source job, unique-name validation, explicit target environment, name, description, and URL behavior.
- [ ] Extract the complete clone chain into one validated transaction/compensating service.
- [ ] Perform all validation before creating or copying Excel files.
- [ ] Return structured success/failure and refresh the Main Dashboard list.
- [ ] Redirect `mainDashboard.cloneBotJob` to the React page.
- [ ] Retire `ARSaveClonePane` and `ARSaveCloneScene`.

## Phase 4 — AR Web Factory / Scanner

- [ ] Bootstrap job, URL, blocks, browser state, focus profile, OCR/plugin state, and execution state.
- [ ] Split `ScannerToolbar`, `ScannerExecutionPanel`, block selector, and bulk actions into separate components with `.module.scss` files.
- [ ] Migrate visible Page Scanner, OCR, focus/search, hidden-field toggle, navigation, refresh, clear-grid, block selection, Pre-Launch, STOP, and status controls.
- [ ] Do not revive hidden Clone/Hover Pick, Send DOM, Request Support, or Update Plugins controls without a product decision.
- [ ] Extract scan/browser/execution lifecycle into UI-independent services.
- [ ] Remove `ARScannedElementScene` direct pane-field access and replace `TargetElementHelper` pane coupling with an execution context.
- [ ] Retire `ARScannedElementPane`/Scene only after scanner, element tests, block creation, OCR, support, and shutdown pass.

## Phase 5 — startup/recovery Configuration

The normal WebSocket cannot be assumed available on configuration failure, so this is not a normal page route.

- [ ] Split configuration logic from JavaFX/native chooser calls.
- [ ] Add restricted `ConfigManager` recovery mode: path selection, validation, save, retry, and exit only.
- [ ] Add a boot-safe IPC bridge independent of a valid database/license/normal server.
- [ ] Continue license, database, server, and dashboard initialization exactly once after a valid save.
- [ ] Test missing, malformed, inaccessible, and valid configuration cases.
- [ ] Retire `ARConfigurationPane` and `ARConfigurationScene`.

## Phase 6 — shared dialogs and final retirement

- [ ] Create accessible `AppDialog` and `StatusBanner`, each with a `.module.scss`.
- [ ] Replace JavaFX `PerformMessage`, `ARAlertPane`, global `AlertModal` SCSS, and `window.confirm` incrementally.
- [ ] Use preview/confirm operations for destructive work and structured error codes instead of HTML-formatted backend text.
- [ ] Remove inactive `ARMainPane`, `ARNewBotJobPane`/Scene, `ARViewBotJobListPane`/Scene, and `AbstractARScannedElementPane` after zero-reference checks.
- [ ] Remove React-only JavaFX wrappers only when one React window/router owns their navigation.

## Required validation and delivery gate for every slice

1. React component tests for buttons, disabled/busy state, keyboard behavior, errors, and confirmation.
2. Backend pure/service tests plus WebSocket contract tests.
3. Playwright tests against localhost/mocked socket, followed by loopback integration.
4. No production BancaStato config/database mutation.
   Automated tests use `D:\Projects\ar-web-selenium\Config-4.2\TESTS.config`. When production-shaped
   reference data is necessary, `D:\Projects\ARWeb-Linux\ARWeb\database.db` and
   `D:\Projects\ARWeb-Linux\Config-4.2\ARWeb.config` are read-only inputs; mutation-capable tests use
   isolated copies.
5. `git diff --check` on touched paths.
6. Build `abr-react-ts-grid` with `npm run build`.
7. Delete the deployed backend build contents, copy the complete new React build, and compare manifests/hashes.
8. Do not compile/package the Java backend unless the user separately authorizes it. That later
   authorization was received on 2026-07-12 for the current migration work.

## Implementation ledger

| Date | Slice | State | Evidence |
|---|---|---|---|
| 2026-07-12 | Shared workspace header + Bot Job/Components/Pre Scan navigation + Refresh/Close | Frontend delivered; backend source ready for user runtime validation | React commit `c3e077a` pushed to `origin/VERSION-4.6`; 3 focused suites / 4 tests passed; production build succeeded; 45 deployed files matched by path, length, and SHA-256; backend static re-audit passed without compiling Java |
| 2026-07-12 | Typed Bot Job Details bootstrap/state + metadata/environment migration + Organization Advanced fields | Frontend delivered/deployed; backend compiled and packaged; desktop runtime remains user-owned | React commits `a06619e` and `88eac85` pushed to `origin/VERSION-4.6`; final frontend head/remote `88eac85272819711c156b77c88c88b03850f60f9`; 9 focused React suites / 23 tests passed; 45 deployed files had zero SHA-256 differences; Java compiled 296 main and 61 test sources; 43 targeted Java tests passed; shaded `target/AR_Web_Scanner-4.2.jar` built at 384,155,105 bytes, SHA-256 `AC097EE3F2A4043CFEBC0E7E4287084D6A52E3300357901F56F80F3A42333F02` |
| 2026-07-12 | Remaining Bot Job Details execution/data/file controls + semantic metadata form + JavaFX toolbar removal | Delivered/deployed/packaged; desktop runtime remains user-owned | React commits through `30d6f331e6519c8bec9211470d1a76e6d8d74363` pushed to `origin/VERSION-4.6`; 9 focused React suites / 31 tests passed; headless localhost/mock-socket Playwright passed; 45 deployed files matched by path, length, and SHA-256; Java compiled 304 main and 71 test sources; 65 focused Java tests plus the final 19-test affected subset passed; backend implementation commit `3cd86f87c734d0507725dcfc3be3edef1b3a1689`; shaded `target/AR_Web_Scanner-4.2.jar` is 384,197,039 bytes with 58,696 entries and SHA-256 `5D8F4E3BB844AE93FB685F16B8D50D2BB96311B61071F524A849771AE2FAE82E` |
