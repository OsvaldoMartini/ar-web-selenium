# Session Handoff

Date: 2026-07-10

## Repositories

Backend:
- Path: `D:\Projects\AllinWeb\ar-web-selenium`
- Branch: `refactor/perform-actions-decomposition`
- Latest observed commit: `452da0c3 feat: Avaloq card support - test-id classification + effective click target`

Frontend:
- Path: `D:\Projects\AllinWeb\abr-react-ts-grid`
- Branch: `VERSION-4.6`
- Latest observed commit: `e0ed423 docs: Avaloq investigation - implementation status`

## Current Local State

Backend has local uncommitted files:
- `.claude/settings.local.json`
- `Config-4.2/TESTS.config`

Frontend was clean at the time this handoff was written.

## Migration Rule

Keep the current AR Web Factory / `ARScannedElementPane` implementation intact while migration is in progress.

The new React dashboard and pre-scan behavior must be additive. Preserve the same button visibility/availability rules for each client build. Remove old JavaFX scanner code only after the migrated flow is verified end to end.

## Pre-Scan Dashboard State

The migration added a lightweight React scanner dashboard:

- Java button: `PRE SCAN` in Bot Job Details before `TEST RUN`.
- React session: `preScannerGrid`.
- React component: `GridItemScann` with `mode="preScan"`.
- The dashboard reuses existing scanner grid behavior:
  - element grouping
  - row `+`
  - block header `+`
  - Memory List
  - create block
  - Apply to target block

The `PRE SCAN` button toggles the main Bot Job content area:
- `PRE SCAN` opens the dashboard.
- Button text changes to `BOT JOB`.
- Clicking `BOT JOB` returns to the normal Bot Job grid.

## Browser Behavior

Pre-scan uses an isolated visible Playwright browser.

Reason: BancaStato returned `403` / incomplete DOM under headless Chromium, causing the scanner to return 0 elements.

Important behavior:
- It does not reuse the `TEST RUN` browser.
- It does not close or steal the current AR Web Factory browser.
- It keeps the isolated browser open and reuses it so cookies, consent, opened dropdowns, and manual navigation survive.
- `Page Scanner` scans the current isolated browser page when already open.
- `Refresh Web Page` refreshes/reopens the isolated browser but does not scan or clear the grid.

## Scanner Defaults

The dashboard default focus is:

`All page scanner controls`

This sends blank search terms to the backend. The backend then uses `PlaywrightElementScanner.DEFAULT_SELECTOR`, matching the broader AR Web Factory Page Scanner behavior and restoring output/text candidates.

If result counts differ from AR Web Factory, inspect:
- `PlaywrightElementScanner.DEFAULT_SELECTOR`
- classification logic in `PlaywrightElementScanner`
- whether the browser page state differs between scans

## Status Messages

Backend sends `preScanStatus` to `preScannerGrid`.

Statuses:
- `running`
- `done`
- `empty`
- `failed`

Frontend displays this status in the dashboard header and disables scan buttons while running.

## Buttons / Visibility

The dashboard must mirror current AR Web Factory visibility for this client.

Currently hidden in React because the JavaFX AR Web Factory equivalents are hidden:
- `Send Pure HTML Review`
- `Request Support`
- `Search Hidden Fields`

Keep hidden controls present in code but not visible, because different clients may enable them later.

## Next Recommended Work

1. Retest dashboard default scan with blank `Search by`.
   - Expected: `Output` group appears.
   - Link/Button/Input counts should be closer to AR Web Factory screenshots.

2. Add `Close Pre-Scan Browser`.
   - Backend command: `PRE_SCAN_CLOSE_BROWSER`.
   - Closes only the isolated pre-scan browser.
   - Must not affect `TEST RUN` or AR Web Factory.
   - Sends status: `Pre-scan browser closed.`

3. Verify `OCR Config` from dashboard.
   - It currently opens the existing Java OCR config using selected bot job `homeBankingId/homeUrlId`.

4. Add Apply status feedback.
   - Show applying X elements to target block.
   - Show backend accepted/refreshed.

5. Only after dashboard flow is stable, migrate remaining visible factory controls one at a time.

## Commands To Inspect On New Machine

Backend:

```powershell
cd D:\Projects\AllinWeb\ar-web-selenium
git status --short
git log -5 --oneline
```

Frontend:

```powershell
cd D:\Projects\AllinWeb\abr-react-ts-grid
git status --short
git log -5 --oneline
```

## Build/Test Note

During this migration work, Maven/npm builds were intentionally not run by Codex unless explicitly requested. The user prefers to build/copy frontend artifacts manually.

