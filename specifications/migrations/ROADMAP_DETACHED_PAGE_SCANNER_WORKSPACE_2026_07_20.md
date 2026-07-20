# Detached Page Scanner Workspace Roadmap

Date: 2026-07-20

Status: IN PROGRESS - detached workspace and focused automated verification complete; generation hardening, full-suite, and physical multi-monitor acceptance remain

Backend baseline: `ar-web-selenium`, branch `refactor/perform-actions-decomposition`, commit `b7e35e37122558e40a0d3f794de2e6bc1fd4b0fd`

Frontend baseline: `abr-react-ts-grid`, branch `VERSION-4.6`, commit `86256ab80bbf170d23d4283d589d9d2827128b0a`

Implementation snapshot (2026-07-20):

- Backend-owned `page-scanner-<UUID>` workspaces, strict application-window launch, bootstrap, close, expiry, bounds, exact-session reconnect, and Bot Job epoch binding are implemented.
- Native-window ownership is now global rather than per Bot Job: at most one Bot Job Details application window, one Page Scanner application window, one OCR Config application window, and one independent OCR Results application window may exist for the AR Web process.
- Bot Job Details keeps one unguessable `bot-job-window-<UUID>` control WebSocket for the lifetime of its native panel. Opening another Bot Job publishes `botJobDetails.windowTarget` to that connection and remounts the existing panel for the latest Bot Job/epoch instead of launching another window.
- A cross-job Page Scanner request reuses the existing physical scanner window but allocates a fresh `page-scanner-<UUID>` logical session. The old scanner state, queued work, mutation ledger, browser ownership, and OCR source binding are retired before `pageScanner.workspaceRetarget` moves the panel to the new trusted Bot Job context. This prevents late results or OCR callbacks from leaking into the newly selected job.
- OCR Config and OCR Results each own one global physical slot. A same-context request focuses the existing corresponding panel; a cross-scanner/job request publishes `ocrWorkspace.windowRetarget`, assigns a fresh kind-specific logical session, replaces the route, and key-remounts the reused physical window without mixing old state.
- Bot Job Details, Page Scanner, and both OCR coordinators use a two-second reconnect grace so a transient WebSocket replacement cannot launch a duplicate native panel.
- All blocking and acknowledged asynchronous server writes share one fair per-transport gate, preventing Jetty's concurrent-write `Blocking message pending 10000 for BLOCKING` failure.
- Repeating either action for the same Bot Job focuses/reuses the existing panel. Closing a panel with Alt+F4 leaves no connected native window; the next valid request may launch one replacement, never an additional concurrent copy.
- The green **PRE SCAN** action now requests a detached Page Scanner and leaves Bot Job Details mounted.
- The strict detached React route, scanner-specific header, complete existing grid, Memory List, OCR actions, visible **Search Hidden Fields**, and scanner-only close path are implemented.
- Scan, refresh, clear, row test, Apply, and block creation use dedicated `pageScanner.*` requests on the authoritative detached transport. Apply and block mutations are request-ID correlated and retry-safe.
- Detached scan, refresh, and element-test work is serialized through a bounded per-workspace command lane; Clear and Close discard queued work and report correlated overload/failure states.
- Apply validates the complete selection before writing and persists instructions plus references atomically. Block creation and block-order shifts are also transactional. A committed mutation is distinguished from a later Bot Job real-time refresh warning.
- Closing, expiring, or superseding a Page Scanner publishes an exact-session lifecycle event, retires only its scanner resources, and prevents subsequent writes into a closed or newly selected Bot Job.
- OCR launched by a detached scanner resolves organization, Bot Job, and home URL from the backend coordinator rather than client-supplied identity.
- The production frontend build has been clean-copied into the backend resource bundle and verified byte-for-byte by relative-path SHA-256 (45 files, zero mismatches).
- Focused frontend, backend, persistence rollback, and real-Chrome multi-window contract tests pass. The Chrome test executed with zero skips after browser-launch permission was granted.
- Still open before final acceptance: scan-generation/final-marker caching and late-running-result suppression, a real-backend Apply-to-Bot-Job integration test, full Maven gate, and manual multi-monitor/native-window verification.

## 1. Objective

Change the green **PRE SCAN** action in Bot Job Details so it opens an independent Page Scanner operating-system window instead of replacing the Bot Job Details React surface.

The detached Page Scanner must:

- use the same AR Web floating-workspace colors, Arial font, sizes, spacing, and controls already used by the current Pre Scan page;
- open as a Chromium application window with no visible address bar or tabs;
- be movable as an independent native window to any monitor;
- leave Bot Job Details open, mounted, connected, and usable in its original window;
- preserve the current isolated Playwright page-scanning behavior;
- preserve the complete current result grid, selection, Memory List, block selection/creation, OCR, row testing, filtering, pagination, and cleanup features;
- show and support every explicitly required toolbar control, including **Search Hidden Fields**;
- send selected elements to the bound Bot Job and update Bot Job Details in real time;
- close independently without closing Bot Job Details or any TEST RUN browser.
- reuse the one global Page Scanner native panel when another Bot Job requests Pre Scan, without ever displaying two scanner panels for different jobs.

This roadmap is a separation and transport migration. It is not a visual redesign and must not rename, reorder, add, or remove scanner result columns.

## 2. Meaning of "floating" and "independent"

The correct implementation is the existing detached OCR native-window pattern, not another `FloatingWorkspaceFrame` inside the Bot Job browser page.

- `DesktopAppBrowserLauncher` starts Chrome or Edge with `--app=<loopback-url>`, `--new-window`, and `--window-size=1240,820`.
- Chromium application mode hides the address bar and tabs. The internal loopback URL still exists for transport, but it is not visible in the UI.
- The operating-system window can be dragged between monitors through its native window frame.
- `DesktopWorkspaceShell` fills that native window when `desktopShell=1`; internal React dragging is intentionally disabled in this mode.
- The existing Memory List remains independently draggable inside the Page Scanner content area.

A DOM-only floating panel is insufficient because browser viewport coordinates cannot move a panel onto another physical display.

## 3. Investigation Summary

### 3.1 Current entry flow

The green button is defined in frontend `BotJobDetailsHeader.tsx` and sends `SHOW_PRE_SCAN`. The current response reports `activeSurface="preScan"`; `useBotJobDetailsController.ts` maps that value to the fixed `preScannerGrid` session and calls `onSurfaceOpen`. `src/index.tsx` then replaces the current React root with:

```text
DesktopWorkspaceShell
  -> GridItemScann mode="preScan"
```

Consequences today:

- Bot Job Details is unmounted from that window.
- No new native window is launched.
- The fixed `preScannerGrid` WebSocket identity prevents safe independent/reloaded session ownership and cross-job retargeting.
- The detached OCR URL bootstrap exists, but there is no detached Page Scanner URL bootstrap.

### 3.2 Current backend scan flow

`BotJobDetailsWorkspaceHost` currently owns the Pre Scan workflow and routes these flat WebSocket operations:

- `PRE_SCAN_PAGE`
- `PRE_SCAN_REFRESH_PAGE`
- `PRE_SCAN_CLEAR_GRID`

`PreScanWorkflowService` already provides the functional behavior that must be retained:

- isolated Playwright browser open/reuse;
- page-settle wait;
- configurable element selectors;
- hidden-element scanning;
- actionable-element filtering;
- OCR/name resolution and diagnostic persistence;
- status, reset, and element callbacks;
- Test Click and Test Input against the isolated page.

Status and element chunks are currently published to the single fixed `preScannerGrid` destination. `BotJobPreScanPayloadService` also builds payloads for that fixed destination.

### 3.3 Current add-to-Bot-Job flow

The current intended insertion path is:

```text
result row/group +
  -> local draggable Memory List
  -> choose or create target block
  -> Apply
  -> SEND_ALL_ELEMENTS_DTO
  -> PreScanApplyService when the scanner element pane is closed
  -> database instructions/references
  -> updateInstructions snapshot to botJobTasks
```

The persistence service already updates the live Bot Job task session after a successful insert. However, the detached migration must fix these weaknesses:

- Apply is sent toward `scanner-element-pane` instead of the actual source workspace.
- Backend routing changes based on global scanner-pane open state.
- Apply has no correlated success/failure acknowledgement.
- The frontend clears Memory List immediately after sending, so a transport or persistence failure can lose the user's selection.
- Retry/reconnect has no idempotency protection and could create duplicate instructions.

### 3.4 Proven detached-window precedent

The OCR implementation is the required model:

- `OcrWorkspaceCoordinator` creates unguessable, unique logical sessions.
- Job and organization context stays in Java and is not exposed in the URL.
- A four-hour TTL permits reload/reconnect while bounding stale state.
- Bootstrap and mutations use the registered WebSocket transport identity, not an envelope-supplied destination.
- `ARWebSocketServer.openOcrWorkspaceDesktopShell(...)` launches strictly in application mode and never falls back to a normal address-bar browser.
- Approved OCR suggestions are sent only to the scanner workspace that opened the OCR page.

Auto Test's internal floating frame is not a valid precedent for cross-monitor movement because it remains nested in the Main Dashboard browser viewport.

## 4. Required UI Capability Contract

All existing scanner behavior must be reused or extracted from `GridItemScann`; it must not be duplicated into a second diverging grid.

| Area | Required behavior in detached Page Scanner |
|---|---|
| Status | Preserve Waiting, Scanning/running, done, empty, and failed states, including `Loading the Page - Opening isolated browser...`. |
| Page Scanner | Start a scan of the bound Bot Job environment through its isolated Playwright session. |
| OCR Config | Open the existing independent OCR Config application window, bound back to this Page Scanner session. |
| OCR Results | Open the existing independent OCR Results application window, bound back to this Page Scanner session. |
| Refresh Web Page | Reload the scanner-owned page and report correlated progress/result status. |
| Clear Grid | Clear the current result generation and prevent late chunks from repopulating it. |
| Focus | Preserve every current selector profile: factory default, all interactive, select options, inputs, clickables, outputs, and data IDs. |
| Search by | Preserve the selector input and current placeholder: `button, label, input, data-testid`. |
| Search Hidden Fields | Make the control visible and pass its value to Playwright scanning. This explicit requirement supersedes the earlier legacy-hidden exclusion. |
| Search | Run the scan with the current Focus/search/hidden values. |
| Result tools | Preserve Find, Keep All, Clear Keeps, Delete Unchecked, Clear Grid All, group collapse, raw ID, test-ID, and OCR toggles. |
| Rows | Preserve keep/select, rename, add to Memory, force flags, Test Input, Test Click, and delete. |
| Memory List | Preserve in-page dragging, selected elements, target-block choice, create block, Apply, and clear/close behavior. |
| Pagination | Preserve rows-per-page and paging behavior. |
| Layout | Preserve current column positions/names, widths, colors, font sizes, and overall design. |

`GridItemScann.module.scss` currently applies `display: none` to `.preScanHiddenControl`, which hides **Search Hidden Fields**. The migration must give this required checkbox its own visible style. The same class also hides legacy DOM review/support actions; those unrelated actions must remain hidden unless separately implemented and requested.

## 5. Target Architecture

```text
Main Dashboard
  |
  | mainDashboard.openBotJob { botJobId }
  v
BotJobDetailsWindowCoordinator (one global physical native window)
  |- launches once with botJobWindowSession=bot-job-window-<UUID>
  `- retargets the persistent control WebSocket with botJobDetails.windowTarget
          |
          v
Bot Job Details native app window (latest Bot Job/epoch; botJobTasks remains connected)
  |
  | pageScannerWorkspace.open { botJobId, requestId }
  v
PageScannerWorkspaceCoordinator (one global physical native window)
  |- validates the actual botJobTasks transport and active Bot Job
  |- reuses the panel for the same Bot Job
  |- allocates a fresh page-scanner-<UUID> before a cross-job retarget
  |- owns PageScanner context + isolated PreScanWorkflowService/browser
  `- launches strict Chromium --app URL only when no live panel exists
          |
          v
Detached Page Scanner native app window
  |- DesktopWorkspaceShell (1240 x 820, no address bar)
  |- page-scanner-<UUID> WebSocket
  |- existing scanner controls/result grid/Memory List
  |- opens detached OCR Config/Results windows
  `- pageScanner.apply
          |
          v
Validated pane-free persistence transaction
  |- correlated Apply response to page-scanner-<UUID>
  `- authoritative updateInstructions snapshot to botJobTasks
          |
          v
Bot Job Details updates in real time without manual refresh
```

WebSockets are the transport choice. The application already uses them for scanner status, result chunks, OCR coordination, and Bot Job instruction snapshots; adding REST would add a second state/correlation path without improving this real-time workflow.

## 6. Workspace Identity and Bootstrap Contract

### 6.1 Route

Use a strict application-mode route containing only route type and an unguessable logical session:

```text
http://127.0.0.1:<bound-port>/?desktopShell=1&openPageScanner=preScan&pageScannerSession=page-scanner-<UUID>
```

Do not put organization ID, Bot Job ID, environment URL, credentials, or OCR parameters in the URL.

### 6.2 Coordinator entry

Add `PageScannerWorkspaceCoordinator`, modeled on `OcrWorkspaceCoordinator`. Each registry entry must contain at least:

```text
workspaceSessionId
sourceBotJobSessionId
botJobId
botJobName
homeBankingId
homeUrlId
endpointUrl
browserType/optionsConfig
createdAt/expiresAt
latest status/search/result generation
workspace lifecycle state
```

The coordinator must own the Pre Scan browser/workflow associated with the workspace. It must not depend on whichever scanner pane happens to be globally open.

### 6.3 Concurrency rule

The original first-release proposal of one Page Scanner workspace **per active Bot Job** is superseded by the global native-window rule below.

- The application owns at most one physical Bot Job Details panel and one physical Page Scanner panel, regardless of how many Bot Jobs are opened from Main Dashboard.
- OCR Config and OCR Results each have their own independent process-wide physical slot. Config
  requests never reuse the Results panel and Results requests never reuse the Config panel, but a
  second request of the same kind must reuse that kind's existing physical window.
- Opening Bot Job B while Bot Job A is displayed retargets the existing Bot Job Details panel through its persistent `bot-job-window-<UUID>` control connection. The window history/route, active Bot Job identity, workspace epoch, surface state, and content WebSocket are replaced in place.
- Reopening the currently displayed Bot Job publishes/focuses the existing target and does not create a second panel.
- Requesting Page Scanner again for the same trusted Bot Job/epoch reuses and focuses its existing `page-scanner-<UUID>` workspace.
- Requesting Page Scanner for another Bot Job reuses the same physical panel but **must not reuse the logical scanner session**. The coordinator allocates a fresh `page-scanner-<UUID>`, retires the previous workflow resources, and sends `pageScanner.workspaceRetarget` to the old panel connection so React reconnects and remounts under the new session.
- **PRE SCAN** is the detached-panel open/retarget action. The **Page Scanner** toolbar control inside that panel starts scanning through its current logical session; it must never launch another floating/native panel.
- The fresh logical scanner session is a security and correctness boundary: late scan chunks, Apply/block responses, OCR callbacks, and stale close events from the previous Bot Job cannot be accepted by the new workspace.
- A cross-context OCR request follows the same physical-reuse/logical-replacement rule: it publishes
  `ocrWorkspace.windowRetarget` on the old trusted connection and moves the existing kind-specific
  panel to a fresh `ocr-config-*` or `ocr-results-*` identity. Same-context requests are focus-only.
- While the first app window is still inside its bounded connection grace period, a newer request updates the pending target rather than launching a second window.
- If the native panel was closed with Alt+F4 or its control transport is otherwise gone, the next valid request may launch one replacement. Relaunch preserves the singleton invariant and must never accumulate abandoned browser windows.
- An established transport receives a two-second reconnect grace before recovery. This applies to
  Bot Job Details, Page Scanner, OCR Config, and OCR Results so normal socket replacement/reload
  cannot be mistaken for a closed native window.

The logical session remains unique and reload-safe. Exact-session reconnect may replace only the old transport for that same workspace; it must never take over another Bot Job or OCR session.

### 6.4 Bot Job Details control channel

The Bot Job Details panel requires a control WebSocket that outlives its current content surface because the panel may switch among `botJobTasks`, `componentTasks`, and other routed workspaces.

```text
route:      ?desktopShell=1&openBotJob=<id>&botJobWindowSession=bot-job-window-<UUID>
operation:  botJobDetails.windowTarget
payload:    { controlSessionId, botJobId, workspaceEpoch, homeBankingId }
```

Only the coordinator's exact active control session is accepted. On initial connection or reconnect, the backend republishes the latest authoritative target. React validates the session and positive target values, replaces the route without opening a tab, clears job-specific state, reconnects the correct content surface, and focuses the existing native window.

### 6.4 Bootstrap

`src/index.tsx` must validate the route and mount only `PageScannerWorkspace`. The new window then requests bootstrap over its registered WebSocket transport. The backend derives all context from the coordinator entry.

Bootstrap should return:

- Bot Job display identity and environment label;
- current status and active scan generation;
- current Focus, search terms, and hidden-fields setting;
- sorted target block choices;
- the bounded latest result snapshot/chunk metadata, if one exists;
- capability flags needed by the current scanner UI.

An unknown, malformed, expired, mismatched, or forged session must render a safe unavailable state and must not expose job data.

## 7. WebSocket Protocol Contract

Use typed, correlated Page Scanner messages rather than continuing to trust the legacy top-level destination fields.

| Request | Required response/event | Purpose |
|---|---|---|
| `pageScannerWorkspace.open` | `pageScannerWorkspace.openResponse` | Validate Bot Job source, allocate/reuse session, and launch app window. |
| `pageScanner.workspaceRetarget` | reconnect/remount under the supplied fresh session | Rebind the existing physical scanner panel to another Bot Job without carrying logical workflow state across jobs. A same-session event only focuses the existing panel. |
| `pageScannerWorkspace.bootstrap` | `pageScannerWorkspace.bootstrapResponse` | Restore backend-owned workspace context after first connection/reload. |
| `pageScanner.scan` | status, reset, chunk, complete events | Start one generation with Focus/search/hidden values. |
| `pageScanner.refresh` | correlated status/response | Reload only this workspace's isolated page. |
| `pageScanner.clear` | correlated response plus reset | Invalidate the visible generation and suppress late results. |
| `pageScanner.testElement` | `pageScanner.testElementResponse` | Run Test Click/Input on this workspace's isolated page. |
| `pageScanner.createBlock` | `pageScanner.createBlockResponse` | Create a block for the bound Bot Job and return canonical block choices. |
| `pageScanner.apply` | `pageScanner.applyResponse` | Persist selected DTOs once and return inserted count/IDs or a structured error. |
| `pageScanner.close` | local close completion | Retire only this Page Scanner workspace and its isolated browser resources. |

Every mutating or long-running request must carry a `requestId`. Scan events must also carry a `scanId`/generation and, for chunks, `chunkIndex`, `chunkCount` or an explicit final marker.

Routing and validation rules:

1. The actual registered WebSocket transport session is authoritative.
2. If a payload also includes a session ID, it must match the transport or be rejected.
3. Resolve organization, Bot Job, home URL, and browser ownership from the coordinator entry.
4. Validate that the Bot Job is still active and that a target block belongs to that Bot Job.
5. Reject stale scan generations and late mutations after close/expiry/job switch.
6. Use an idempotency/request ledger for Apply and block creation so reconnect/retry cannot duplicate rows.
7. Bound payload counts, text lengths, selector lengths, and cached result size.

During migration, legacy `PRE_SCAN_*` constants may be adapted internally, but the detached frontend must use the typed transport. Remove the fixed-session adapter after parity tests pass.

## 8. Real-Time Apply and Block Flow

The detached Apply operation must be authoritative and acknowledged:

1. The user adds rows/groups to Memory List locally.
2. The user chooses an existing target block or creates one.
3. The frontend sends `pageScanner.apply` with `requestId`, target block ID, selected DTOs, and any supported row flags.
4. The server authenticates the request from `page-scanner-<UUID>` and resolves the bound Bot Job from the coordinator.
5. The server validates that the block belongs to that job and invokes pane-free Pre Scan persistence directly. Global `scanner-element-pane` visibility must not choose the code path.
6. Instructions and references are committed once.
7. The backend reloads the canonical instruction view.
8. The backend publishes the authoritative instruction snapshot to the source `botJobTasks` session.
9. The backend sends `pageScanner.applyResponse` to the detached scanner with the same `requestId`, success/failure, inserted count/IDs, and message.
10. The frontend clears only the acknowledged elements. On failure or disconnect it retains Memory List for retry.

Block creation follows the same request/response rule and republishes the canonical block list to both the Page Scanner and Bot Job Details where applicable.

## 9. Scan, Clear, and Refresh Concurrency

The current scan, refresh, and clear paths can race. The detached release must define one serialized workspace command lane:

- only one scan or refresh may control a workspace browser at a time;
- starting a new scan creates a new generation;
- Clear invalidates the current visible generation immediately;
- if the underlying Playwright scan cannot be interrupted safely, its subsequent events are discarded server-side as stale;
- closing/expiring a workspace cancels queued work and shuts down only its isolated Pre Scan browser;
- status events keep the exact user-facing progress stages already relied upon by the UI;
- result chunks are deduplicated within one generation and never merged across generations.

## 10. Window and Lifecycle Rules

- Bot Job Details and Page Scanner each have one process-wide native-window owner; no Bot Job ID may be used as a key that permits parallel physical panels.
- Launch through `DesktopAppBrowserLauncher.launch(...)` exactly as detached OCR does.
- Never call `openInBrowser(...)` for the detached Page Scanner because its fallback can expose a normal address-bar browser.
- If Chrome/Edge application mode is unavailable, return a correlated failure to Bot Job Details; do not navigate or close the main page.
- The Page Scanner close control must call the scanner-specific close path and `window.close()`. It must not send Bot Job `CLOSE`.
- Closing Page Scanner closes only its own UI transport, coordinator entry, queued work, and isolated Pre Scan Playwright browser. It must not close Bot Job Details, the TEST RUN browser, or unrelated workspaces.
- Closing Page Scanner must not forcibly close already-detached OCR UI windows. A later OCR action whose source scanner is gone must return a structured disconnected/expired result.
- Closing or switching away from the bound Bot Job invalidates further scanner mutations. The Page Scanner may display an expired/read-only message, but it must never write into a newly selected job.
- Switching Bot Jobs does not transfer the old Page Scanner transport identity. A later scanner-open request for the new Bot Job retargets the same physical window with a new logical session.
- A stale WebSocket close callback must not remove a newer exact-session replacement established by reload.
- Blocking status/chunk/ping sends and acknowledged asynchronous control sends must share a single
  outbound gate per physical WebSocket `Session`; JSR-356 remotes do not permit concurrent writes.
- Alt+F4/disconnect permits the next valid open action to relaunch one panel for the latest target. The coordinator must apply a connection grace period so repeated clicks while that replacement starts do not launch duplicates.
- TTL, strict one-active-physical-workspace capacity, ID validation, and launch rollback follow the proven OCR coordinator policies with the stricter Page Scanner singleton limit.

## 11. Implementation Phases

### Phase 0 - Characterization and contract lock

- [ ] Add tests that freeze the existing Pre Scan selector profiles, search payload, hidden flag, status text, result grouping, row tools, Memory List, block choices, OCR entry points, and pane-free persistence.
- [ ] Record the current 1240 x 820 shell geometry and computed visual tokens for comparison.
- [ ] Add a test proving the current green action switches the same surface; this test will be deliberately inverted in Phase 2.
- [x] Confirm the existing isolated scanner browser behavior remains the baseline and is not confused with the address-bar-free Page Scanner UI window.

### Phase 1 - Backend coordinator and strict launcher

- [x] Add `PageScannerWorkspaceCoordinator` with unique IDs, backend-owned context, TTL, a one-global-physical-window rule, launch rollback, and bounded state. The earlier per-job provisional rule is superseded.
- [x] Add Page Scanner session prefix validation to `ScannerWorkspaceSessions` or a dedicated classifier.
- [x] Add `ARWebSocketServer.openPageScannerDesktopShell(...)` and a validated/encoded URL builder.
- [x] Use `DesktopAppBrowserLauncher.launch(...)` directly with no ordinary-browser fallback.
- [x] Add exact-session reload takeover without allowing cross-session takeover.
- [x] Add open/bootstrap/close request models and unit tests.

### Phase 2 - Detached frontend route and shared shell

- [x] Add route parsing for `openPageScanner` and `pageScannerSession` before the generic dashboard bootstrap.
- [x] Mount a dedicated `PageScannerWorkspace` inside `DesktopWorkspaceShell` when the route is valid.
- [x] Reuse/extract the existing Pre Scan portion of `GridItemScann`; do not fork its result-grid implementation.
- [x] Replace `BotJobDetailsChrome` in the detached page with a scanner-specific header that displays bound job identity and closes only this window.
- [x] Change the green Bot Job Details button to `OPEN_PAGE_SCANNER`/`pageScannerWorkspace.open` semantics.
- [x] Keep `botJobTasks` mounted and remove the frontend `onSurfaceOpen(preScannerGrid)` behavior for this action.
- [x] Preserve the exact existing visual design, result columns, toolbar order, and 1240 x 820 application-window size.
- [x] Make **Search Hidden Fields** visible without exposing the unrelated hidden DOM/support controls.

### Phase 3 - Session-aware scanning transport

- [x] Move PRE SCAN commands from fixed `preScannerGrid` routing to the actual `page-scanner-<UUID>` transport.
- [x] Parameterize `BotJobPreScanPayloadService` and every status/block publisher with the coordinator workspace destination.
- [ ] Add request/scan correlation, ordered chunks, final markers, stale suppression, and bounded cached bootstrap state.
- [x] Serialize scan/refresh/clear/close against the workspace-owned Playwright browser.
- [x] Preserve Focus expansion, selector text, hidden scanning, OCR resolution, actionable filtering, status messages, and diagnostic dumps.
- [ ] Accept incoming results only for the current workspace, bound Bot Job, and active generation.

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

- Working baselines are the backend/frontend commits listed at the top of this document. The
  frontend implementation is committed as `786357d` on `VERSION-4.6`; the backend implementation
  commit ID is recorded by the follow-up documentation checkpoint after the implementation commit.
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
