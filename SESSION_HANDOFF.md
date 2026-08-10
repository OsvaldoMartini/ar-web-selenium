# Session Handoff

## Current checkpoint - 2026-08-10

The dated 2026-07-15 scanner-removal notes below remain historical backlog. The authoritative Page
Mappings delivery checkpoint is:

- P0-P7 and all 12 original review-remediation findings are implemented and pushed. P5 is backend
  `c8e722cd` plus `823ab2dc` / frontend `14b7832`; P6 is backend `668a7acb`; P7 OCR Review is
  backend `89bbce24` / frontend `4dc51aa`.
- Legacy OCR Results production source remains retired in backend `07f3fd47` and frontend
  `b2d8a59`. OCR Config and Page Mappings OCR Review remain. The current catalog contains zero
  retired OCR Results entries.
- Snapshot ACL/retention foundations are backend `478a51b2` / frontend `dfd4836`; prior lifecycle
  hardening is backend `09fa2824` / frontend `cb64ab3` plus `6750c3b`.
- Item-7 verification added retention/snapshot coverage and fixed two defects found by it: STAGED
  recovery now uses the migration-compatible empty-string FAILED representation, and private ACL
  handling supports extended-length Windows paths. Frontend retention tests are `f8dd5aa`; backend
  tests and fixes are `380841af`.
- OCR Apply reconnect recovery is frontend `449f9ea`: read-only Review is retired on disconnect,
  while a mutating Apply preserves and resends its exact serialized request before bootstrap. Any
  timeout, malformed/stale response, invalidation, retarget, or backend ambiguity remains blocked
  until a correlated bootstrap and integrity-verified capture reload.
- Backend OCR recovery is `3e365b25`: requests are authorized before the bounded ledger, identical
  reconnects attach replacement transports, successful Applies alone are replay-cached, every
  terminal subscriber is reauthorized, and abrupt worker errors cannot wedge the lane. Alias
  commit/close ambiguity is typed and returns `reloadRequired=true`; settled responses survive the
  outer read-connection close path.
- The previously reported SQLite frame `org.sqlite.core.DB.prepare(DB.java:264)` came from an old
  test fixture missing `scanned_element.defined_name`. The fixture now creates both `defined_name`
  and `client_named`; focused and broader reruns contain no recurrence.
- Frontend verification: the retention-focused run passed 15/15; the final JSDOM-only affected
  suite passed 44/44 across 8 suites. Backend verification: the snapshot/retention matrix passed
  71/71; the final non-browser OCR/session/retirement suite passed 106/106. No browser or native OCR
  process was launched.
- `npm run build` passed at frontend `a51e792` with existing repository warnings. The exact 58-file
  build mirror has zero path/hash differences and is pushed in backend `fb23c531`. Entrypoints are
  `main.eb4f02b1.js` (SHA-256
  `965E2A606FA0AA0A5744C0443BC1EF2FA97FDCE66EB83C4050BE0A5C82E83C56`) and
  `main.df7752f0.css` (SHA-256
  `912DEAE51E4B60DE97A1BEAEB74F6AAE6719EFBB54ED8D28388E77A1518AE70C`).
- Explicit `mvn compile` passed with 562 main Java sources on Java 17. Only the existing
  `InstructionLoad` Lombok and `TargetElementHelper` varargs warnings remained.
- `automation-tests.json` was regenerated and pushed in `d76362ff`, recording backend `3e365b25`
  and frontend `a51e792`: 2,341 catalog rows, 2,305 code cases, and 19,452 generated API requests.
  It contains 13 focused Page Mappings OCR cases and zero legacy OCR Results rows.
- Source, tests, catalog, and static assets are committed/pushed. No new migration was created or
  applied; the backend was not packaged or restarted. Real database inspection, live Windows ACL
  inspection, running-service health, and live desktop/browser acceptance remain open operational
  gates.
- Unrelated dirty Grid, Claude-settings, Marketing, patch, and screenshot files remain preserved
  and outside these commits. During final temporary-worktree cleanup, Git for Windows followed the
  worktree's `node_modules` junction into the original frontend checkout before failing on a path
  loop. The verified pushed tree was restored without overwriting the dirty `GridItemScann.tsx`
  (SHA-256 `7E8F12625D890E97F68EDC58482E0B1ACFB5D08A39156BF16F5D985136F04D45`), Git metadata was
  reconstructed at `a51e792`, and `npm rebuild --ignore-scripts` restored 168 dependency command
  shims without changing `package-lock.json`. The temporary worktree/junction was then removed.
  The two untracked generated `dev-server.*.log` files were deleted by the failed cleanup and were
  not recoverable from Git; final frontend status contains only the pre-existing Grid edit.

Read `specifications/performances/COPY_LAST_RESPONSE.md` and
`specifications/performances/Page Mappins PLAN 2026-08-07.md` before continuing.

Date: 2026-07-15

## Current Goal

Continue the Scanner / AR Web Factory migration removal.

Direction from the user:

- Keep Java as the minimal backend/service side.
- Move scanner frontend logic into the React/TypeScript container.
- Create/extract new TypeScript methods/functions and typed WebSocket contracts.
- Test with Playwright/browser validation as much as possible when the app can launch cleanly.
- For each medium migration modification, commit and push.
- Do not change frontend design unless required for the migration.

## Repositories

Backend:

- Path: `D:\Projects\ar-web-selenium`
- Branch: `refactor/perform-actions-decomposition`
- Status when written: clean except this handoff update
- Recent commits:
  - `ebdee6c0 test: reuse scanner contract constants`
  - `3dcb9733 refactor: reuse scanner operation id in pre scan host`
  - `34640069 refactor: reuse scanner constants in browser services`
  - `d3923855 refactor: centralize scanner search terms operation`
  - `ef626b03 refactor: reuse scanner session ids in pre scan workspace`

Frontend:

- Path: `D:\Projects\ar-react-ts-grid`
- Branch: `VERSION-4.6`
- Status when written: `src/components/GridItemScannMobile.tsx` is modified and needs the next pass
- Recent commits:
  - `ff476a6 refactor: reuse scanner sessions in grid messages`
  - `f27ff57 refactor: reuse scanner session ids in app routing`
  - `e48232c refactor: centralize scanner compatibility ids`
  - `209f8cc refactor: centralize scanner session ids`
  - `55dc621 refactor: centralize scanner baseline statuses`

## Files To Read First

Read these first before continuing:

1. Frontend active dirty slice:
   - `D:\Projects\ar-react-ts-grid\src\components\GridItemScannMobile.tsx`
   - `D:\Projects\ar-react-ts-grid\src\components\scanner\Scanner.operations.ts`
   - `D:\Projects\ar-react-ts-grid\src\components\scanner\Scanner.sessions.ts`
   - `D:\Projects\ar-react-ts-grid\src\components\scanner\Scanner.controllerStatus.ts`
   - `D:\Projects\ar-react-ts-grid\src\components\scanner\Scanner.controllerTiming.ts`

2. Frontend scanner comparison files:
   - `D:\Projects\ar-react-ts-grid\src\components\GridItemScann.tsx`
   - `D:\Projects\ar-react-ts-grid\src\components\GridItem.tsx`
   - `D:\Projects\ar-react-ts-grid\src\components\GridItemComp.tsx`
   - `D:\Projects\ar-react-ts-grid\src\index.tsx`
   - `D:\Projects\ar-react-ts-grid\src\components\scanner\*.test.ts`

3. Backend scanner contract/constants:
   - `src/main/java/com/allinweb/ch/scanner/ScannerWorkspaceOperations.java`
   - `src/main/java/com/allinweb/ch/scanner/ScannerWorkspaceSessions.java`
   - `src/main/java/com/allinweb/ch/scanner/ScannerWorkspacePayloads.java`
   - `src/test/java/com/allinweb/ch/scanner/ScannerWorkspaceOperationsTest.java`
   - `src/test/java/com/allinweb/ch/scanner/ScannerWorkspaceSessionsTest.java`

4. Backend biggest remaining runtime cleanup:
   - `src/main/java/com/allinweb/ch/websocket/SimpleWebSocketServer.java`

5. JavaFX legacy scanner files:
   - `src/main/java/com/allinweb/ch/component/pane/ARScannedElementPane.java`
   - `src/main/java/com/allinweb/ch/component/scene/ARScannedElementScene.java`

6. Smaller backend cleanup targets:
   - `src/main/java/com/allinweb/ch/service/GenFlowService.java`
   - `src/main/java/com/allinweb/ch/component/pane/PerformLists.java`
   - `src/main/java/com/allinweb/ch/component/pane/PerformListElements.java`
   - `src/main/java/com/allinweb/ch/component/pane/PerformPreLoad.java`
   - `src/main/java/com/allinweb/ch/component/pane/PerformCloseBrowser.java`
   - `src/main/java/com/allinweb/ch/plugin/PluginContext.java`
   - `src/main/java/com/allinweb/ch/component/pane/BotJobDetailsWorkspaceHost.java`

## What Is Missing To Finish

### 1. Finish The Current Frontend Mobile Scanner Slice

Current active file:

- `D:\Projects\ar-react-ts-grid\src\components\GridItemScannMobile.tsx`

Next actions:

- Review the current diff in `GridItemScannMobile.tsx`.
- Replace remaining scanner operation/session literals where safe.
- Prefer existing constants from:
  - `Scanner.operations.ts`
  - `Scanner.sessions.ts`
- Likely remaining safe replacement:
  - replace runtime `searchTerms` operation checks with `SCANNER_SEARCH_TERMS_OPERATION`
- Keep payload field names such as JSON property `searchTerms` only where they are actual wire payload keys.
- Run frontend scanner tests and build.
- Commit and push this slice.

Suggested commands:

```powershell
git -C D:\Projects\ar-react-ts-grid diff -- src/components/GridItemScannMobile.tsx
rg -n -C 3 "searchTerms|scannerTool|scannerGrid|preScannerGrid|scanner-element-pane" D:\Projects\ar-react-ts-grid\src\components\GridItemScannMobile.tsx
npm test -- --watchAll=false src/components/scanner
npm run build
git -C D:\Projects\ar-react-ts-grid diff --check
git -C D:\Projects\ar-react-ts-grid add src/components/GridItemScannMobile.tsx
git -C D:\Projects\ar-react-ts-grid commit -m "refactor: reuse scanner operation id in mobile grid"
git -C D:\Projects\ar-react-ts-grid push origin VERSION-4.6
```

### 2. Backend WebSocket Server Cleanup

Biggest remaining backend runtime file:

- `src/main/java/com/allinweb/ch/websocket/SimpleWebSocketServer.java`

Known runtime literals still to clean carefully:

- `scannerGrid`
- `preScannerGrid`
- `scannerTool`
- `scanner-element-pane`

Likely work:

- Reuse `ScannerWorkspaceSessions.SCANNER_GRID`.
- Reuse `ScannerWorkspaceSessions.PRE_SCANNER_GRID`.
- Reuse `ScannerWorkspaceSessions.SCANNER_TOOL`.
- Add `ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE = "scanner-element-pane"` if it does not exist yet.
- Update `ScannerWorkspaceSessionsTest`.
- Replace exact string comparisons and sends first.
- Be careful with regex checks such as `matches(".*scannerTool.*")`; replace with helper methods or constant-based contains logic only when behavior is identical.
- Keep compatibility destinations until React owns the replacement route.

Suggested focused tests after this slice:

```powershell
& 'D:\Installed\apache-maven-3.9.16\bin\mvn.cmd' '-Dtest=ScannerWorkspaceSessionsTest,ScannerWorkspaceOperationsTest,ScannerWorkspaceServiceTest,ScannerWorkspaceRequestLedgerTest,ScannerWorkspaceRequestTest,ScannerWorkspaceResponseTest' test
git diff --check
```

### 3. JavaFX Legacy Scanner Pane Cleanup

The big Java-side migration target remains:

- `src/main/java/com/allinweb/ch/component/pane/ARScannedElementPane.java`

This file is large and behavior-sensitive. Split it into small safe commits.

Known cleanup areas:

- Old scanner WebSocket sends.
- Hardcoded scanner session destinations.
- Direct JavaFX control state mixed with scanner business logic.
- Browser/tab/DOM operations.
- TEST RUN / STOP and terminal-result behavior.
- Block creation and persistence glue.
- Appium/Web XML parsing and scan DTO construction.
- OCR, filesystem/CSV, support, and plugin management.

Do not start by deleting or rewriting the large execution engine. First extract small service/context boundaries and preserve behavior.

### 4. Small Backend Cleanup

After `SimpleWebSocketServer.java`, continue with smaller files that still contain scanner/session/searchTerms literals or old Java-side contract knowledge:

- `ARScannedElementScene.java`
- `GenFlowService.java`
- `PerformLists.java`
- `PerformListElements.java`
- `PerformPreLoad.java`
- `PerformCloseBrowser.java`
- `PluginContext.java`
- `ScannerWorkspacePayloads.java`
- `BotJobDetailsWorkspaceHost.java`

Keep these changes small and commit each medium slice.

### 5. Tests Cleanup

Some tests still hardcode scanner contract values:

- `scannerGrid`
- `preScannerGrid`
- `scannerTool`
- `scanner-element-pane`
- `searchTerms`

This is lower risk than runtime code. Update tests after runtime constants are stable.

Prioritize tests that validate new constants:

- `ScannerWorkspaceOperationsTest`
- `ScannerWorkspaceSessionsTest`
- frontend scanner contract/helper tests under `src/components/scanner`

### 6. End-To-End Validation

Required final validation path:

- Full frontend build.
- Focused frontend scanner tests.
- Focused backend Maven scanner tests.
- Backend compile/package when practical.
- Playwright/browser validation if the app can be launched cleanly.

Do not claim scanner pane/scene retirement until:

- Runtime routes are migrated or compatibility-routed.
- React owns the reachable scanner controls.
- Java side is reduced to services/backends.
- Zero-reference audit confirms `ARScannedElementPane` and `ARScannedElementScene` can be deleted.
- Desktop runtime parity is recorded.

## Already Completed In This Migration Pass

Frontend extraction/cleanup already pushed:

- scanner response matching helpers
- scanner action status helpers
- bootstrap/action response handling helpers
- transport message builder
- controller reset state helper
- bootstrap/action request eligibility helpers
- message cursor helper
- request id formatting helper
- controller failure statuses
- controller timing
- baseline statuses
- scanner operation constants
- scanner session constants
- scanner compatibility ids
- app routing and grid message reuse of scanner session constants

Backend extraction/cleanup already pushed:

- scanner request body parser
- scanner workspace operation ids
- scanner workspace session ids
- pre scan workspace session reuse
- scanner search terms operation constant
- browser service scanner constant reuse
- pre scan host scanner operation id reuse
- scanner contract constant test reuse

## Recent Verification

Frontend recent verification:

- `npm test -- --watchAll=false src/components/scanner`
  - previously passed: 19 suites / 70 tests
  - known warnings: React `act` deprecation, CRA Babel preset warning, worker force-exited warning
- `npm run build`
  - previously passed
  - known existing ESLint warnings in unrelated files

Backend recent verification:

- Focused Maven scanner/service tests previously passed.
- Known Maven warnings:
  - duplicate `javafx-maven-plugin`
  - deprecation/unchecked compile warnings

## Current Important Caution

The frontend file `GridItemScannMobile.tsx` is already modified. Read and preserve that diff before editing.

Do not change frontend CSS/design while doing the scanner contract cleanup unless a functional migration requires it.
