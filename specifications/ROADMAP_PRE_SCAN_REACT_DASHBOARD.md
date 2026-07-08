# PRE SCAN React Dashboard Roadmap

## Objective

Add a `PRE SCAN` flow before `TEST RUN` that scans the selected URL with Playwright, sends found `ElementDTO` records to a lightweight React dashboard, and lets the user select/apply elements later.

The first version must be read-only until the user clicks `Apply`. Scanning should not automatically create bot job instructions.

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
