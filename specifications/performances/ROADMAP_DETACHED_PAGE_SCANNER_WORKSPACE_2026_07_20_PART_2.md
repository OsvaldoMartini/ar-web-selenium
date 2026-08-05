### Phase 4 - Acknowledged mutations and Bot Job real-time updates

- [x] Replace `scanner-element-pane`-targeted detached Apply with `pageScanner.apply` on the source transport.
- [x] Validate job/block ownership and call pane-free persistence regardless of other pane visibility.
- [x] Add idempotent Apply request handling and structured `pageScanner.applyResponse`.
- [x] Retain Memory List entries until success is acknowledged; retain failed entries with an actionable error.
- [x] Publish the canonical post-commit instruction snapshot to `botJobTasks` so Bot Job Details updates immediately.
- [x] Add correlated block-create success/failure and canonical block refresh.
- [x] Route Test Click/Input to this workspace's isolated Playwright session and return correlated results.

### Phase 5 - OCR and full feature parity

- [x] Extend OCR source-session validation to accept registered detached Page Scanner sessions.
- [x] Verify OCR Config/Results bootstrap remains backend-bound to the correct organization/job/home URL.
- [x] Verify accepted OCR names return only to the Page Scanner that opened the OCR workspace.
- [x] Preserve Find, keep/delete controls, group toggles, OCR review, pagination, row editing/flags/tests, Memory List dragging, block selection, and all current alerts.
- [x] Keep currently hidden/unrouted DOM review and support actions out of the visible contract unless a separate requirement enables and implements them.

### Phase 6 - Lifecycle, security, and cleanup

- [x] Make detached Close retire only the Page Scanner and its isolated scanner browser.
- [x] Invalidate safely on Bot Job close/switch and reject all late mutations.
- [ ] Test TTL expiry, the maximum-one workspace bound, launch failure rollback, reconnect, stale close, invalid prefix, forged job IDs, and oversized payloads.
- [ ] Ensure logs include request ID, scan ID, logical workspace, Bot Job ID, operation, duration, count, and outcome without logging credentials/page secrets.

### Phase 6A - Singleton native-window retargeting

- [x] Add `BotJobDetailsWindowCoordinator` with one global `bot-job-window-<UUID>` reservation, a persistent control connection, latest-target publication, connection grace, launch rollback, and disconnected-window relaunch.
- [x] Route every Main Dashboard Bot Job open through that coordinator instead of unconditionally launching `--new-window`.
- [x] Reduce Page Scanner physical-window capacity to one and make cross-job requests reuse that panel with a fresh logical scanner session.
- [x] Preserve the scanner native panel during Bot Job host switching while retiring the old job's authoritative content/workflow binding.
- [x] Verify frontend route replacement, state reset, content-socket reconnection, and remount keys for both control protocols against the production bundle.
- [x] Verify that rapid repeated clicks, an in-flight initial launch, stale close callbacks, retarget publication failure, and Alt+F4 each preserve the one-window invariant through coordinator and deployed-bundle tests.

### Phase 6B - Singleton OCR native-window retargeting and transport hardening

- [x] Reduce OCR Config and OCR Results ownership to one independent global physical slot per kind.
- [x] Make same-context requests focus/reuse the existing corresponding panel without clearing its
      draft/result state.
- [x] Reuse the same physical OCR panel across scanner/Bot Job contexts with a fresh logical session,
      `ocrWorkspace.windowRetarget`, URL replacement, and keyed React remount.
- [x] Reject stale and cross-kind OCR transports/events after a retarget.
- [x] Apply the reconnect grace to both OCR kinds so a transient WebSocket disconnect cannot launch
      another application window.
- [x] Serialize every blocking and acknowledged asynchronous write for one WebSocket transport and
      cover callback completion plus synchronous-send failure release.

### Phase 7 - Automated and manual verification

- [x] Add frontend route/controller/component tests for every required control and message envelope implemented in this increment.
- [x] Add backend coordinator, launcher, protocol, workflow, apply, publisher, and lifecycle tests for the implemented contract.
- [x] Replace the same-shell expectation in `BotJobDetailsToolbarPlaywrightTest` with a detached-window contract.
- [ ] Add integration coverage proving Bot Job Details stays mounted and receives instruction updates after Page Scanner Apply.
- [x] Build the frontend production bundle and clean-copy it into `src/main/resources/build`.
- [x] Verify deployed frontend files against the source build by relative path and SHA-256.
- [ ] Run focused Java tests, the frontend test/build suite, Java Playwright navigation tests, and the full Maven package/test gate appropriate to the branch.
- [ ] Manually validate native dragging across displays because headless Playwright cannot test Windows monitor topology or compositor behavior.

### Phase 8 - Retire fixed same-shell behavior

- [ ] Remove `activeSurface="preScan"` navigation and the fixed `preScannerGrid` frontend switch after detached parity passes.
- [ ] Remove or isolate fixed-session status/payload/block publishers.
- [ ] Remove legacy flat PRE SCAN command adapters once no deployed client uses them.
- [ ] Update `ROADMAP_PRE_SCAN_REACT_DASHBOARD.md`, the main migration checks document, and operator documentation with final commits and verification evidence.

## 12. Primary Files Expected to Change

### Backend repository: `ar-web-selenium`

New classes will likely include:

- `src/main/java/com/allinweb/ch/socket/PageScannerWorkspaceCoordinator.java`
- typed Page Scanner request/response/context models in the socket/model boundary
- focused coordinator/protocol/launcher tests

Existing files likely affected:

- `src/main/java/com/allinweb/ch/socket/ARWebSocketServer.java`
- `src/main/java/com/allinweb/ch/socket/DesktopAppBrowserLauncher.java`
- `src/main/java/com/allinweb/ch/socket/SimpleWebSocketServer.java`
- `src/main/java/com/allinweb/ch/socket/WebSocketSessionManager.java`
- `src/main/java/com/allinweb/ch/socket/OcrWorkspaceCoordinator.java`
- `src/main/java/com/allinweb/ch/model/ScannerWorkspaceSessions.java`
- `src/main/java/com/allinweb/ch/model/ScannerWorkspaceOperations.java`
- `src/main/java/com/allinweb/ch/model/BotJobWorkspaceAction.java`
- `src/main/java/com/allinweb/ch/component/pane/BotJobDetailsWorkspaceHost.java`
- `src/main/java/com/allinweb/ch/component/pane/MainDashboardPresentationAdapter.java`
- `src/main/java/com/allinweb/ch/facade/PreScanWorkflowService.java`
- `src/main/java/com/allinweb/ch/facade/PreScanBrowserSession.java`
- `src/main/java/com/allinweb/ch/facade/BotJobPreScanPayloadService.java`
- `src/main/java/com/allinweb/ch/facade/PreScanApplyService.java`
- `src/main/java/com/allinweb/ch/facade/ScannerBlockUpdatePublisher.java`
- `src/main/java/com/allinweb/ch/facade/BotJobWorkspaceCloseCoordinator.java`
- `src/main/java/com/allinweb/ch/socket/InstructionRealtimePublisher.java`
- `src/test/java/com/allinweb/ch/runner/BotJobDetailsToolbarPlaywrightTest.java`

### Frontend repository: `abr-react-ts-grid`

New/extracted files will likely include:

- `src/components/page-scanner/PageScannerWorkspace.tsx`
- `src/components/page-scanner/PageScannerWorkspaceHeader.tsx`
- `src/components/page-scanner/usePageScannerController.ts`
- typed Page Scanner protocol definitions and focused tests

Existing files likely affected:

- `src/index.tsx`
- `src/components/GridItemScann.tsx`
- `src/components/GridItemScann.module.scss`
- `src/components/bot-job-details/BotJobDetailsHeader.tsx`
- `src/components/bot-job-details/useBotJobDetailsController.ts`
- `src/components/Scanner.sessions.ts`
- `src/components/Scanner.operations.ts`
- `src/components/useWebSocket.tsx`
- `src/components/workspace/DesktopWorkspaceShell.tsx`

## 13. Verification Matrix

| Layer | Required proof |
|---|---|
| Coordinator unit | Unique logical sessions, one global physical slot, TTL, bounds, launch rollback, bootstrap ownership, expiry, and reconnect behavior. |
| Bot Job window coordinator | One global control session, first launch, connected retarget, latest-target delivery on connect/reconnect, launch grace, publication failure, strict ID validation, stale disconnect, and one relaunch after Alt+F4. |
| Page Scanner retarget coordinator | One global physical slot, same-job focus/reuse, cross-job fresh logical session, old workflow/ledger/browser retirement, initial-launch retarget, publication rollback, stale job close isolation, and one replacement after Alt+F4. |
| Launcher unit | Exact encoded `--app` route, `desktopShell=1`, 1240 x 820, and no normal-browser fallback. |
| Protocol unit | Actual transport wins; forged session/job/block rejected; request IDs correlated; retries idempotent. |
| Workflow unit | Page open/reuse, settle, selector/hidden propagation, OCR resolution, chunking, refresh, clear generation, test input/click, and shutdown. |
| Frontend unit | Direct route, unavailable route, all controls visible, exact command fields, stale event rejection, Memory retention until ACK, and scanner-only close. |
| Frontend retarget unit | Exact control-session validation; Bot Job route/state/socket replacement; scanner route/session/remount replacement; same-target focus; malformed/stale event rejection. |
| Multi-window Playwright | Green button leaves Bot Job mounted; detached route renders only Page Scanner; OCR windows remain separately launchable. |
| Real-time integration | Apply once inserts once, ACKs scanner, and updates the visible Bot Job instruction grid without refresh. |
| Lifecycle integration | Reload replaces only its own transport; scanner close leaves Bot Job active; Bot Job switch prevents stale writes. |
| Production bundle | Frontend source build and backend-deployed bundle match by relative path and SHA-256. |
| Manual Windows | No address bar/tabs; exact visual template; native window moves between all available monitors; independent minimize/restore/close. |

## 14. Acceptance Criteria

- [ ] Clicking the green **PRE SCAN** button opens one new address-bar-free Page Scanner application window.
- [ ] Opening Bot Jobs A, B, then A from Main Dashboard leaves exactly one Bot Job Details native panel and shows the latest requested job in that same panel.
- [ ] Repeated clicks while Bot Job Details is connecting do not create another native panel; after Alt+F4, one later click launches exactly one replacement for the latest job.
- [ ] Bot Job Details remains visible, mounted, connected, and usable in its original window.
- [ ] The Page Scanner is not nested inside the Bot Job Details DOM or clipped by its container.
- [ ] The Page Scanner window uses the same 1240 x 820 template, colors, font sizes, spacing, and current result-grid structure.
- [ ] The native Page Scanner window can be dragged to another monitor.
- [ ] Waiting/status text, Page Scanner, OCR Config, OCR Results, Refresh Web Page, Clear Grid, Focus, Search by, Search Hidden Fields, and Search are visible and functional.
- [ ] Search Hidden Fields actually changes the `includeHidden` Playwright scan behavior.
- [ ] Every existing visible result-grid, Memory List, block, OCR, row-test, filtering, and pagination feature remains functional.
- [ ] Scan results and status are isolated to the correct Page Scanner/Bot Job and cannot leak into another session.
- [ ] Opening Page Scanner for Bot Job A and then Bot Job B leaves exactly one Page Scanner native panel, reuses its physical window, and changes to a fresh logical scanner session for B.
- [ ] Reopening Page Scanner for the same Bot Job reuses/focuses its current session and does not clear valid scanner state or launch another panel.
- [ ] Clicking the **Page Scanner** toolbar control starts a scan in the already-owned panel and never opens a second Page Scanner window.
- [ ] Repeated **OCR Config** clicks leave exactly one OCR Config native panel; opening it from a
      different Page Scanner/Bot Job reuses that panel with a fresh logical Config session.
- [ ] Repeated **OCR Results** clicks leave exactly one OCR Results native panel; opening it from a
      different Config/scanner context reuses that panel with a fresh logical Results session.
- [ ] OCR Config and OCR Results remain two separate native windows that can be placed on different
      monitors at the same time; neither is nested in Bot Job Details or Page Scanner.
- [ ] A transient OCR/Page Scanner WebSocket reconnect does not create a duplicate native window,
      and late messages from a retired logical session are ignored/rejected.
- [ ] Late A scan chunks, mutation acknowledgements, OCR callbacks, and close events are rejected after the physical scanner panel retargets to B.
- [ ] Clear cannot be undone by late chunks from an older scan.
- [ ] Apply is acknowledged, retry-safe, and clears Memory List only after confirmed persistence.
- [ ] Applied instructions appear in the already-open Bot Job Details page in real time.
- [ ] OCR Config/Results opened from Page Scanner remain independent native windows and send results back only to their source scanner.
- [ ] Closing Page Scanner does not close Bot Job Details or the TEST RUN browser.
- [ ] Closing/switching Bot Job prevents the old Page Scanner from mutating another job.
- [ ] App-mode launch failure is shown in Bot Job Details and never falls back to a browser with an address bar.
- [ ] Automated suites, production bundle parity, and the physical multi-monitor smoke test pass.

## 15. Non-Goals

- Redesigning the Page Scanner, changing column positions/names, or changing established colors/font sizes.
- Replacing WebSockets with REST.
- Reintroducing Selenium; the isolated browser remains Playwright-owned.
- Merging the isolated scanner browser with TEST RUN's active page. That work remains governed by `ROADMAP_TEST_RUN_PAGE_SCANNER_SESSION.md`.
- Reviving currently hidden and unrouted DOM review/support controls.
- Hiding or redesigning the separate client website browser controlled by Playwright; this roadmap removes browser chrome from the Page Scanner UI window.

## 16. Completion Evidence

Evidence recorded 2026-07-20:

- Working baselines are the backend/frontend commits listed at the top of this document. The final
  implementation commits are frontend `786357d` on `VERSION-4.6` and backend `896ae61e` on
  `refactor/perform-actions-decomposition`; this evidence update is the follow-up documentation
  checkpoint.
- Frontend singleton/retarget verification ran eight suites with 32 tests and zero failures,
  including Bot Job control, Page Scanner, OCR Config, OCR Results, strict kind/session validation,
  focus-only repeats, fresh cross-context sessions, URL replacement, and keyed remounting. The
  complete Jest sweep passed 43 of 46 suites and 158 of 160 tests; its three failing suites are the
  pre-existing stale `App.test.tsx` and `AboutPanel.test.tsx` assertions plus the pre-existing
  `CloneJobManager.test.tsx` Jest mock-hoisting error. `npm run build` completed successfully with
  only the established project-wide lint/dependency warnings.
- Backend focused verification ran `mvn "-Dtest=PageScannerWorkspaceCoordinatorTest,PageScannerMutationLedgerTest,BotJobPreScanPayloadServiceTest,ScannerWorkspaceSessionsTest,DesktopAppBrowserLauncherTest,OcrWorkspaceCoordinatorTest,SimpleWebSocketServerSessionLifecycleTest,BotJobWorkspaceControllerTest,BotJobDetailsWorkspaceRegistryTest,ScannerInsertPersistenceServiceTest,PerformDataBasePageScannerTransactionTest,BlockCreationServiceTransactionTest,PageScannerTaskGateTest,BotJobDetailsToolbarPlaywrightTest" test`. It covered coordinator, mutation ledger, dynamic payload destination, session classification, launcher, OCR binding, WebSocket lifecycle/allowlist, workspace lifecycle locking, scanner persistence, transaction rollback, task gating, and the toolbar contract: 77 tests total, zero failures/errors. The initial sandboxed combined run executed 76 and skipped the Chrome contract because the sandbox rejected the browser child process with `spawn EPERM`.
- The current critical coordinator/transport gate contains 63 passing unit tests across
  `BotJobDetailsWindowCoordinatorTest`, `PageScannerWorkspaceCoordinatorTest`,
  `OcrWorkspaceCoordinatorTest`, `WebSocketSessionManagerTest`, and
  `SimpleWebSocketServerSessionLifecycleTest`. It covers all four physical singleton slots,
  reconnect grace, rollback, stale-session rejection, and serialized blocking/acknowledged writes.
  Real-browser verification was executed with `mvn -Dtest=BotJobDetailsToolbarPlaywrightTest test`:
  1 test passed with zero failures/errors/skips. It proves Page Scanner, OCR Config, and OCR Results
  each change to a fresh cross-job logical session inside the exact same Playwright `Page`, with no
  page-count increase; same-session notifications remain focus-only.
- The launcher unit contract passed for a strict `desktopShell=1&openPageScanner=preScan&pageScannerSession=page-scanner-<UUID>` Chromium `--app` route, 1240 x 820 application-window sizing, malformed-route rejection, and no ordinary-browser fallback in the Page Scanner launch path.
- Persistence verification includes all-or-nothing instruction/reference insertion, rollback on reference failure, atomic block-order shift plus block insertion, and rollback restoring the prior order.
- Frontend `build/` and backend `src/main/resources/build/` contain 45 files each; relative-path
  SHA-256 comparison found zero mismatches. The deployed entry bundle is
  `static/js/main.3183ab17.js`.
- The generated automation inventory now contains 972 catalog rows, 938 code cases, 19,452
  generated API cases, and 20,390 total automated cases. Its stale-catalog failure during the first
  full sweep was resolved by regeneration; `AutomationTestCatalogServiceTest` then passed 2/2.
- The first clean backend sweep executed 766 tests: 764 passed, one fixture-dependent comparison
  was skipped, and the sole failure was the pre-regeneration catalog count. A later unfiltered sweep
  confirmed the catalog but hit the known external `PerformDBEngineAccessTest` missing Access-path
  diagnostic and skipped browser-dependent cases in that run; focused real-browser and singleton
  gates above passed independently. A fully green environment-independent Maven sweep is therefore
  not claimed.
- The React-only navigation gate was updated to the persistent Bot Job control session and detached
  singleton protocol. `npm run test:e2e` passed 4/4, including Bot Job staying mounted, Page Scanner
  A-to-B in-place retarget, and exactly one Config plus one Results page each retargeting in place.
- `mvn clean package -DskipTests` completed successfully after compiling 419 production sources and
  209 test sources and producing the shaded application JAR.
- A fully green unfiltered Maven sweep, a real-backend Apply-to-visible-Bot-Job integration test,
  scan-generation/late-running-result suppression, and manual physical multi-monitor verification
  remain pending and must not be inferred from the focused results above.
- Automated singleton-window evidence is complete: coordinator/frontend suites cover one Bot Job Details owner, one Page Scanner owner, launch grace, fresh cross-job scanner sessions, same-job focus/reuse, stale-session isolation, and Alt+F4 replacement; the deployed-bundle browser contract proves in-place scanner retarget without increasing page count. Physical Windows/multi-monitor acceptance remains manual.
- No deviation from the visual/column contract has been accepted.
