# PRE SCAN React Dashboard Roadmap

## Status (2026-07-09)

- Phase 1 Frontend session support: DONE (`preScannerGrid` -> `GridItemScann mode="preScan"`).
- Phase 2 Backend PreScan service: DONE as deviation - logic lives inline in `ARViewBotJobPane`
  (`runPreScan` etc.), not a separate `PreScanService`; extraction still desirable.
- Phase 3 Toolbar: DONE as deviation - `PRE SCAN` button is a view toggle (PRE SCAN <-> BOT JOB);
  scan runs from the dashboard's Page Scanner button; status streams as WAITING/SCANNING phases.
- Phase 4 Dashboard container: DONE (swaps into componentBox; reset-then-chunks; browser reused).
- Phase 5 Persistence boundary: DONE - `PreScanApplyService` persists Apply when AR Web Factory
  is closed (pane path untouched when open). Pre-scan itself writes no instructions.
- Phase 6: items 1-4 DONE. Focus profile (1) is honored via the search terms it expands to;
  search terms (2), OCR config shortcut (3), clear grid (4) via PRE_SCAN_* commands.
  Items 5 (HTML review) and 6 (support request) DEFERRED by rule: their legacy buttons are
  setVisible(false) in ARScannedElementPane and the dashboard buttons are display:none —
  do NOT migrate legacy-hidden features (Banca Stato only needs the visible ones). The
  backend slice was reverted (297ddfd9). Item 7 (scanner diagnostics) follows the same rule
  if its legacy entry point is hidden.

Migration rule (2026-07-09): before migrating any ARScannedElementPane feature, check its
control's visibility in the legacy pane. setVisible(false) => skip, do not port.

## Excluded from migration (user decision 2026-07-09 - Banca Stato needs visible features only)

- DOM review / support request (legacy buttons hidden; backend slice reverted 297ddfd9)
- Clone / HOVER PICK (cloneElementsButton, checkCloneElement, coords fields)
- "For Click" / "For Input" / "For Output (Excel Export)" checkboxes (the scanner's decided
  typeElement is the source of truth in the dashboard flow)
- configureButton (opens bank config scene - reachable elsewhere)
- Plugin update button / hints (hidden)
- Search Hidden Fields toggle button (hidden in legacy; dashboard has its own checkbox)
- Pre Launch / STOP / countdown (already owned by ARViewBotJobPane toolbar)

## Remaining for 100% (visible + needed features only)

1. DONE (6e6b52cf): Row "Test Click" / "Test Input" run pane-free on the ISOLATED
   preScanDriver when AR Web Factory is closed; results stream to the dashboard status bar
   (replaces the legacy result modal + checkNotShowTestMsg).
2. DONE (30a0ac3 FE / 16c80990 bundle): row save in preScan mode carries the Memory List's
   selected block, or asks the user to pick one.
3. Row details button (DETAILS_ELEMENT_DTO): only fills the legacy pane's text fields -
   hide in preScan mode (nothing to fill without the pane).
4. UPDATE_ALL_ELEMENTS_DTO (re-scan locator refresh): pane-only path today; verify the
   button is exposed in preScan mode and either port or hide.
5. OCR "Accept OCR Name" suggestions target scanner sessions only; route to preScannerGrid
   too (low priority - pre-scan already auto-resolves names during the scan).
6. Retirement: once 1-2 are done and verified, Banca Stato workflow no longer needs
   ARScannedElementPane for element picking; keep the pane for other clients per the
   original migration rule.
- Phase 7 Parity (added): DONE - select DTO normalization (decided-category convention),
  category grouping in React grids, `elementDTO-PS-BJ.json` + AI variant dumps, OCR name
  resolution in pre-scan, page-settle wait, actionable-elements filter, legacy default search
  profile on empty terms. Compared via `PreScanDumpComparisonTest`
  (report: `<path_db>/page_diagnostics/elementDTO-PS-compare-report.txt`).

## Objective

Add a `PRE SCAN` flow before `TEST RUN` that scans the selected URL with Playwright, sends found `ElementDTO` records to a lightweight React dashboard, and lets the user select/apply elements later.

The first version must be read-only until the user clicks `Apply`. Scanning should not automatically create bot job instructions.

Migration rule: keep the existing AR Web Factory / `ARScannedElementPane` implementation intact while the React dashboard migration is in progress. New dashboard behavior must be additive and must preserve existing button visibility/availability rules for each client build. Remove old JavaFX scanner code only after the migrated flow is verified end to end.

## Current System

- `ARViewBotJobPane` owns the Bot Job Details toolbar and already has `TEST RUN`.
- `ARScannedElementPane` owns the heavy AR Web Factory scanner UI.
- `Page Scanner` currently scans through `PerformListElements.scanElements(...)`.
- When Playwright is enabled, `PerformListElements` delegates to `ARPlaywrightDriver.getPlaywrightDriver().scanElements(...)`.
- Scanner results are sent to React through WebSocket session `scannerGrid`.
- `GridItemScann` already supports selection, memory list, block dropdown, create block, and apply.

## Target Flow

1. User selects an environment URL in Bot Job Details.
2. User clicks `PRE SCAN`, located before `TEST RUN`.
3. Backend starts an isolated Playwright scan session.
4. Backend navigates to the selected URL and executes the same scanner logic used by Page Scanner.
5. Backend sends the result to React session `preScannerGrid`.
6. React opens/renders a lightweight scanner dashboard using `GridItemScann`.
7. User selects elements or uses block/header `+` actions into Memory List.
8. User chooses a target block or creates a new block.
9. User clicks `Apply`.
10. Only `Apply` persists selected elements into bot job instructions.

## Phase 1 - Frontend Session Support

Scope:
- Reuse `GridItemScann`.
- Add support for session id `preScannerGrid`.
- Add a prop such as `mode="preScan" | "scanner"`.
- Render a clearer dashboard title in `preScan` mode.
- Keep Memory List enabled.
- Keep block dropdown, create block, row `+`, block `+`, and apply behavior enabled.
- Do not require current JavaFX scanner pane state.

Implementation notes:
- In `src/index.tsx`, route `preScannerGrid` to `GridItemScann`.
- In `GridItemScann.tsx`, accept optional `mode`.
- Treat `preScannerGrid` WebSocket messages the same as `scannerGrid` for `searchTerms`.
- Do not fork `GridItemScann` unless the mode differences become large.

Acceptance:
- A page loaded with session `preScannerGrid` renders scanner elements.
- Existing `scannerGrid` behavior remains unchanged.
- Memory List and `Apply` still send the existing scanner apply contract.

## Phase 2 - Backend PreScan Service

Scope:
- Create a small backend service, for example `PreScanService`.
- Do not instantiate `ARScannedElementPane` for pre-scan.
- Do not depend on Selenium.
- Do not use the shared visible Playwright browser used by `TEST RUN`.

Implementation notes:
- Resolve URL from `homeURLChoiceBox` first, then current URL label, then bot job home URL fallback.
- Load `HomeBankingLoadDTO` options config.
- Start an isolated Playwright browser/context/page.
- Use an isolated Playwright browser for the scan engine. For BancaStato, use visible Chromium by default because the site blocks/serves incomplete content to headless sessions; headless can remain a future option for sites that allow it.
- Navigate with `DOMContentLoaded`, not full page `load`.
- Call the same scanner logic used by Page Scanner, ideally through a shared scanner method.
- Build `SplitDTO` with:
  - `type = "SEARCH_TOOL"`
  - `sessionId = "preScannerGrid"`
  - `operationId = "searchTerms"`
  - `elementDetails = scanned elements`
  - `blocks = current bot job block options`
- Send chunks to WebSocket session `preScannerGrid`.
- Keep and reuse the isolated visible browser during the Bot Job Details session so cookies, manual consent, and visual inspection remain available. It must still be separate from the TEST RUN browser.

Acceptance:
- `PRE SCAN` returns elements without opening AR Web Factory.
- It does not close or steal the current `TEST RUN` browser.
- It does not persist instructions until React sends Apply.

## Phase 3 - Toolbar Integration

Scope:
- Add `PRE SCAN` button before `TEST RUN` in `ARViewBotJobPane`.
- Add simple running state:
  - disabled while scanning
  - text such as `PRE SCAN ...`
  - errors reported with a small modal/log entry

Implementation notes:
- Place in the existing launch group:
  `Pre Launch | GEN FLOW | block dropdown | refresh | PRE SCAN | TEST RUN | STOP`
- Keep `PRE SCAN` independent from selected block. It scans the page URL, not a block.
- `TEST RUN` continues to require selected block.

Acceptance:
- Button appears before `TEST RUN`.
- Clicking it opens/updates the React pre-scan dashboard.
- `TEST RUN` still behaves as before.

## Phase 4 - Lightweight Dashboard Container

Scope:
- Open a React dashboard container independent from the heavy scanner pane.
- It can be a JavaFX WebView using the same bundled React app.
- It should not show the current browser or AR Web Factory controls.

Implementation notes:
- Use `buildWebView(...)` pattern with:
  - JSON payload: `[]`
  - session id: `preScannerGrid`
  - same socket port
  - current home banking id and bot job id/name
- The dashboard should receive real data via WebSocket after scan.
- If the dashboard is already open, reuse/refresh it instead of creating duplicates.

Acceptance:
- User can keep the dashboard open while scanning again.
- New scans clear previous elements first, then stream fresh chunks.
- React dashboard remains usable after backend scan completes.

## Phase 5 - Persistence Boundary

Scope:
- Keep all pre-scan results in memory until user applies.
- Reuse current `NEW_ELEMENT_DTO` / scanner memory apply backend path.

Rules:
- `PRE SCAN` may write diagnostics JSON/logs if needed.
- `PRE SCAN` must not create instructions.
- `PRE SCAN` must not reorder blocks.
- `Apply` is the only persistence action.

Acceptance:
- Running `PRE SCAN` multiple times does not modify bot job instructions.
- Applying selected elements inserts instructions into the chosen block.
- Refreshing Bot Job Details shows only applied elements.

## Phase 6 - Later Migration From ARScannedElementPane

Move buttons from `ARScannedElementPane` into React/PreScan dashboard one at a time:

1. Focus profile selector.
2. Search terms.
3. OCR configuration shortcut.
4. Clear grid.
5. Send HTML review.
6. Support request.
7. Scanner diagnostics.

Do not migrate clone/hover-pick behavior in this slice. Clone remains a separate power-user concept and should not block PRE SCAN.

## Technical Risks

- The current shared `ARPlaywrightDriver` is visible and singleton-backed in normal app flows.
- Using that same instance for pre-scan can interrupt `TEST RUN`.
- BancaStato returned 0 elements under headless Chromium due to 403/resource blocking, so the pre-scan browser should be isolated but visible for this client.
- Do not close the pre-scan browser immediately after each scan; users may need to inspect the page or preserve consent/session state.
- Some sites never complete `load`; use `DOMContentLoaded` and tolerate navigation timeout when DOM is usable.
- `GridItemScann` currently accepts several scanner sessions. Add `preScannerGrid` carefully without weakening message filtering too much.
- The backend scanner currently processes/persists scanned elements through existing repository logic. Confirm whether pre-scan should skip DB persistence or log scanned elements only.

## First Implementation Slice

Recommended first code change:

1. Frontend:
   - Add `preScannerGrid` routing to `GridItemScann`.
   - Add `mode="preScan"`.
   - Add a small dashboard title.

2. Backend:
   - Add `PRE SCAN` button.
   - Add a minimal `PreScanService` using isolated Playwright.
   - Send mocked/empty reset payload first, then real scan chunks to `preScannerGrid`.

3. Verification:
   - Click `PRE SCAN`.
   - Confirm React dashboard shows scanned elements.
   - Confirm Apply inserts into selected block.
   - Confirm no bot job instructions are created before Apply.
