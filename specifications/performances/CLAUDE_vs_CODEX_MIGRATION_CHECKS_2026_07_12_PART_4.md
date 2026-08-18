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

