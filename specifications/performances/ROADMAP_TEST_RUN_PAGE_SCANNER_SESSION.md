# TEST RUN And Page Scanner Shared Session Roadmap

Date: 2026-07-11

## Objective

Allow the Page Scanner to scan the exact browser page produced by **TEST RUN**, including a page opened in a new browser tab by an instruction.

The intended workflow is:

```text
TEST RUN
   -> execute selected block
   -> detect navigation or a newly opened tab
   -> retain the final active page
   -> Scan Test Result
   -> scan that exact page and tab
```

Do not attempt to synchronize two independent browsers. TEST RUN should own its browser session, and Page Scanner should be able to consume the active page from that session.

## Current Behavior

TEST RUN and the lightweight Page Scanner currently use different Playwright sessions:

- TEST RUN starts through `ARViewBotJobPane.onTestRunClicked()` and delegates to `ARScannedElementPane.testRunBlockPlaywright(...)`.
- TEST RUN uses the shared `ARWebDriver` / `ARPlaywrightDriver` browser.
- The lightweight Page Scanner owns a separate `preScanDriver` in `ARViewBotJobPane`.
- `ARPlaywrightDriver` stores one `Page page` reference.
- A click may open a second tab in the same browser context, but the stored page reference is not automatically changed to that new tab.
- A later TEST RUN instruction can therefore continue looking at the original tab.
- Running the lightweight Page Scanner scans its isolated browser rather than the final TEST RUN page.

This creates two distinct problems:

1. TEST RUN and the new Page Scanner do not share browser state.
2. TEST RUN does not have an explicit active-tab policy when an instruction opens a new tab.

## Migration Rules

- Keep the isolated Page Scanner browser available for standalone scanning.
- Do not make the scanner guess which of two independent browsers represents the test result.
- Keep browser and page ownership in Java/Playwright, not in React.
- Use one explicit active-page source for every scan.
- Do not automatically close the TEST RUN browser after the selected block completes.
- The existing STOP action remains responsible for closing a TEST RUN browser.
- Preserve Playwright's single-thread access rule by routing all page/context operations through the owning driver executor.
- Do not reintroduce Selenium window-handle logic into the Playwright path.

## Active Page Policy

Apply this policy consistently to TEST RUN actions and scanner actions:

1. If an instruction navigates in the same tab, retain that tab as active.
2. If exactly one new tab opens, make the new tab active.
3. If multiple tabs open during one action, select the newest non-closed visible tab.
4. Wait for the adopted tab to reach at least `DOMContentLoaded` before the next instruction.
5. Bring the adopted tab to the front where supported.
6. If the active tab closes, fall back to the most recently active remaining tab.
7. If no usable tab remains, report that the TEST RUN browser has no scannable page.
8. Page Scanner always scans the driver's declared active page, never an unrelated cached `Page` reference.

## Proposed Browser Sources

The Page Scanner should expose two explicit sources:

| Source | Behavior |
|---|---|
| `Isolated Page Scanner` | Uses `preScanDriver`; opens or reuses the configured endpoint as today. |
| `TEST RUN - Active Tab` | Uses the retained TEST RUN driver and scans its declared active page without navigating it. |

The UI should show the selected source before scanning.

## Phase 1 - Make ARPlaywrightDriver Tab Aware

Extend `ARPlaywrightDriver` so it owns and exposes the active page deliberately rather than treating the first-created page as permanently current.

Responsibilities:

- track all non-closed pages in the current `BrowserContext`
- track the current active page
- listen for pages opened by the context
- adopt a new page after an action that creates one
- attach diagnostics to every newly created page
- expose safe operations for:
  - active page URL
  - active page title
  - open-page summaries
  - selecting a page
  - selecting the newest page
  - closing the active page
- ensure `click`, `fill`, `text`, `content`, `screenshot`, `evaluate`, and `scanElements` use the active page

Suggested conceptual API:

```java
Page activePage();
List<PageSummary> pages();
boolean selectPage(String pageId);
boolean selectNewestPage();
String activePageUrl();
```

The concrete API may differ, but callers must not manipulate Playwright `Page` objects from a different thread.

Acceptance:

- opening a normal same-tab link retains the existing active page
- opening one new tab adopts it automatically
- every new page receives console, page-error, and WebSocket diagnostics
- closing the active page selects a valid fallback
- all existing single-page behavior remains unchanged

## Phase 2 - Make Actions Detect Page Transitions

Wrap each TEST RUN action with page-transition detection.

For actions that can navigate or open a page, especially `CLICK` and `OTHER`:

1. capture the open pages and active page before the action
2. execute the action
3. wait for a bounded page/navigation transition window
4. compare pages after the action
5. adopt a newly opened page according to the active-page policy
6. wait for the selected page to reach `DOMContentLoaded`
7. continue the next instruction on that page

The wait must be bounded because many clicks neither navigate nor open a new page.

Acceptance:

- instruction two can locate an element in a tab opened by instruction one
- same-tab navigation does not incur an excessive delay
- delayed popups within the bounded transition window are detected
- popup detection failure is logged with the instruction name and current URL

## Phase 3 - Retain TEST RUN Result State

After a selected TEST RUN block finishes, preserve the browser and publish a result state.

Result state should include:

- running/completed/failed/stopped state
- Bot Job id
- selected block id/order
- active page URL
- active page title
- number of open tabs
- whether the active page is available for scanning

Lifecycle:

- starting a new TEST RUN replaces or explicitly closes the previous TEST RUN session
- normal completion retains the browser
- action failure may retain the browser for diagnosis
- STOP closes the browser and clears the result state
- application shutdown closes the browser

Acceptance:

- successful completion leaves the final page open
- failure leaves enough state to inspect or scan the failing page when safe
- STOP closes all pages/context/browser owned by TEST RUN
- a stale or manually closed browser is reported as unavailable

## Phase 4 - Add Scan Test Result Flow

Add a Page Scanner operation that consumes the retained TEST RUN active page.

Suggested UI labels:

- `Scan Test Result`
- or Page Scanner source: `TEST RUN - Active Tab`

Behavior:

- do not call `ensurePreScanBrowserOpen(...)`
- do not navigate the TEST RUN page back to the configured endpoint
- resolve the current TEST RUN driver
- validate that its context and active page are open
- wait for the current page to settle using the existing bounded settle logic
- scan the active page
- write scanner dumps using the same output conventions as the current Page Scanner
- send rows and status to `preScannerGrid`

Acceptance:

- the URL displayed by the scanner matches the TEST RUN active-tab URL
- scanning does not open another browser
- scanning does not change the active page URL
- the isolated Page Scanner remains independently usable
- a missing TEST RUN page produces a clear message instead of silently opening the endpoint

## Phase 5 - Make Browser Ownership Visible

Show enough state that the user knows which browser will be scanned.

Recommended status fields:

- source: `Isolated Page Scanner` or `TEST RUN - Active Tab`
- browser state: running/completed/closed
- active tab title
- active tab URL
- open tab count

Recommended controls:

- source selector
- `Scan Test Result`
- `Use Newest Tab`
- active-tab dropdown showing title and URL
- previous/next tab, if useful
- `Close Active Tab`, with confirmation when it is the last page

Do not rely only on the physical foreground browser tab. The UI must display which Playwright page the backend considers active.

Acceptance:

- the selected scan source is always visible
- the active URL is visible before scanning
- changing the active tab updates the displayed title and URL
- scanner status identifies the source it used

## Phase 6 - Concurrency And Lifecycle Safety

Define behavior for overlapping commands:

- disable TEST RUN while another TEST RUN is starting or running
- disable scanning while the active TEST RUN action is mutating the page, unless explicitly supported
- prevent STOP from racing an active scan
- serialize TEST RUN actions, page selection, and scanning through the Playwright driver executor
- reject commands against a closed or replaced session using a session/run identifier
- avoid holding raw `Page` references in JavaFX or React state

Acceptance:

- double-clicking TEST RUN does not create two owners of the same state
- STOP during a long operation terminates cleanly
- a late scan response from an older TEST RUN cannot overwrite the current result
- no Playwright object is accessed from the wrong thread

## Phase 7 - Diagnostics

Add structured log messages for:

- TEST RUN session id
- action/instruction name
- page count before and after the action
- previous active URL
- selected active URL
- reason for page adoption
- scanner source
- scanner active URL

Example diagnostic sequence:

```text
TEST RUN action CLICK "Open details": pages before=1 active=/start
TEST RUN new page detected: pages after=2 selected=/details
TEST RUN completed: active=/details tabs=2 scannable=true
PAGE SCANNER source=TEST_RUN active=/details
```

Acceptance:

- logs can prove which tab received each instruction
- logs can prove which page was scanned
- failures distinguish closed page, missing session, transition timeout, and scan failure

## Phase 8 - Test Plan

### Same-tab navigation

1. Run a block whose click navigates in the same tab.
2. Confirm the next instruction runs on the destination page.
3. Confirm Scan Test Result scans the destination URL.

### New-tab navigation

1. Start TEST RUN at the configured endpoint.
2. Execute instruction one in tab 1.
3. Let instruction two open tab 2.
4. Confirm tab 2 becomes active.
5. Confirm any later instruction runs in tab 2.
6. Complete TEST RUN without closing the browser.
7. Run Scan Test Result.
8. Confirm the scanned URL and DOM belong to tab 2.

### Multiple tabs

1. Open more than one additional tab.
2. Confirm the newest tab is selected by default.
3. Select another tab from the UI.
4. Confirm the scanner uses the selected tab.

### Closed pages

1. Close the active tab manually.
2. Confirm a remaining tab becomes active.
3. Close the entire browser.
4. Confirm Scan Test Result reports that no TEST RUN page is available.

### Isolated scanner regression

1. Do not run TEST RUN.
2. Select `Isolated Page Scanner`.
3. Run Page Scanner.
4. Confirm it opens/reuses `preScanDriver` and scans the configured endpoint as today.

### Failure inspection

1. Run a block with a failing instruction after navigation.
2. Confirm the browser remains available when safe.
3. Confirm Scan Test Result can scan the failing active page.

## Definition Of Done

This roadmap is complete when:

- TEST RUN reliably follows a newly opened tab
- subsequent TEST RUN instructions use the adopted tab
- the completed TEST RUN browser remains available until STOP or replacement
- Page Scanner can explicitly scan the TEST RUN active page
- Page Scanner does not open a second browser when `TEST RUN - Active Tab` is selected
- the isolated scanning workflow remains available
- the UI clearly identifies the scan source and active URL
- logs identify the page used for every action and scan
- new-tab, same-tab, closed-tab, multiple-tab, and isolated-scanner scenarios pass

## Recommended First Slice

Implement the smallest end-to-end slice in this order:

1. Add active-page/new-tab adoption to `ARPlaywrightDriver`.
2. Make TEST RUN retain its browser and expose the final active URL.
3. Add `Scan Test Result` that scans the TEST RUN active page.
4. Display scanner source and active URL.
5. Add manual multi-tab selection only after automatic newest-tab behavior is stable.

