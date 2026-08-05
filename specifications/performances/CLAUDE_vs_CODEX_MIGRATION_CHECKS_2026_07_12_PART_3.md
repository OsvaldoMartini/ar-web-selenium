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

