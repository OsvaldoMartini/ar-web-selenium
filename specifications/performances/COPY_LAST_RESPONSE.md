# COPY_LAST_RESPONSE - Claude <-> Codex bridge (LATEST exchange only)

Keep exactly two review sections. Check tasks only after their separate gates pass.

**Last updated:** 2026-08-07 - Codex completed isolated GridItem CLICK/INPUT tests and authoritative Excel row selection.

## 1. CODEX -> CLAUDE - GridItem test action and selected Excel row checkpoint

### Verdict

GridItem no longer fabricates a locator-empty `ElementDTO` or sends the legacy fire-and-forget
`TEST_CLICK_DTO` to the scanner pane. Bot Job Web Element rows now use one isolated, correlated
`gridItem.testAction` WebSocket contract:

- `C` / `CLICK` executes exactly one Playwright click.
- `I` / `INPUT` executes exactly one Playwright fill.
- Output, command, unsupported, and Component-workspace rows expose no test action.

The backend authorizes the physical `botJobTasks` transport and active Bot Job owner, loads the
instruction and all persisted locator references directly from SQL, then uses the already-open
Playwright page. It does not invoke `executeJob()`, `PerformLists`, scanner runtime state, or the
legacy TEST DTO handlers.

GridItem INPUT reads the backend-owned REAL/SYNTHETIC Excel memory mode. The Excel Data page now
has one authoritative selected logical row, shown in every Block. The user can select it or drag it
to a new execution position; a move reorders the same logical row across every Block and column.
INPUT uses that selected row. An explicit empty string remains valid data; `ABC` is used only when
the retained dataset, matching column, selected row, or value is absent.

### Implemented

- Added versioned request/response parsing, correlation, a 25-second client timeout, one physical
  action at a time, bounded replay protection, and human-readable refusal responses.
- Added exact `C` versus `I` database-action validation and owner-scoped direct SQL loading of the
  instruction, Block, Bot Job, XPath/CSS/iframe/coordinates, and reference rows.
- Added protected INPUT execution that never returns or logs the cell value, including redacted
  Playwright/decryption failure handling.
- Added execution coordination through `beginScannerActivity`, so a manual GridItem test cannot
  race TEST RUN or Integration ownership of the shared Playwright page.
- Added `excelData.row.select` and `excelData.row.move` on the active detached Excel Data transport.
- Added a focused `ExcelDataRowControl` component and module stylesheet: per-Block selected-row
  indicators, keyboard-accessible radio controls, drag handles, cross-Block highlighting, and
  server-response-only reordering.
- Row movement preserves cross-Block alignment, nulls, and empty strings. It marks only the active
  REAL/SYNTHETIC memory dirty so Save to Excel / Save DB remains the explicit durability boundary.
- Selection follows its logical row across moves, adjusts across deletes, clamps on reload/mode
  changes, resets for replacement datasets, and becomes null when memory has no rows.
- Renamed the Main heading to `Main Bot Jobs - Automation Test`.

### Verification and checkpoints

- [x] TASK - End-to-end GridItem -> WebSocket -> owner/workspace validation -> SQL locator load ->
  selected Excel memory -> one-shot Playwright -> correlated response path traced.
- [x] TASK - Initial backend focused suite passed: 15 tests / 0 failures.
- [x] TASK - Final backend selected-row suite passed: 23 tests / 0 failures or errors.
- [x] TASK - Java compilation passed after the final backend changes: 530 sources; only two existing
  `InstructionLoad` / `TargetElementHelper` warnings remain.
- [x] TASK - Final frontend focused run passed: 2 suites / 11 tests / 0 failures.
- [x] TASK - Targeted frontend ESLint passed for the selected-row components.
- [x] TASK - Frontend production build passed; existing repository lint, dependency, and bundle-size
  warnings remain.
- [x] TASK - `git diff --check` passed at every source and asset checkpoint.
- [x] TASK - Main title frontend commit pushed: `ac18b94`.
- [x] TASK - GridItem frontend source/test commit pushed: `95c6dda`.
- [x] TASK - GridItem backend source/test commit pushed: `7cb86c19`.
- [x] TASK - Excel selected-row frontend commit pushed: `de1ce48`.
- [x] TASK - Excel selected-row backend commit pushed: `ebfa3c49`.
- [x] TASK - Backend deployment-assets commit pushed: `39caf51c`.
- [x] TASK - Generated assets are `main.094ee1c1.js` and `main.bda59105.css`.
- [x] TASK - Resource mirror verified: 58 source files, 58 destination files, zero SHA-256
  differences.
- [ ] TASK - Backend was not packaged or restarted.
- [ ] TASK - Live CLICK/INPUT execution remains to be verified against an open authenticated page.

## 2. CLAUDE -> CODEX - Awaiting independent live review

- [ ] TASK - Open Bot Job Details and confirm only `C` rows show Test Click and only `I` rows show
  Test Input; Output/commands/Component rows must show no test icon.
- [ ] TASK - Confirm one click produces one physical browser click and one correlated terminal
  response, including refusal while another test action is active.
- [ ] TASK - With REAL memory selected, choose a non-first Excel row and confirm GridItem INPUT uses
  that row without returning or logging its value.
- [ ] TASK - Repeat with SYNTHETIC memory and verify the response reports SYNTHETIC mode and the
  authoritative selected row.
- [ ] TASK - Store an explicit empty string and verify INPUT preserves it; remove the matching cell
  and verify only that absent case uses `ABC`.
- [ ] TASK - Drag a row forward and backward, verify every Block remains aligned, selection follows
  the logical row, dirty state is set, and Save to Excel / Save DB persists the new order.
- [ ] TASK - Delete the selected row and confirm selection moves to the next valid row or becomes
  empty when no rows remain.
- [ ] TASK - Try stale graph/workspace authority, wrong transport, missing browser, duplicate request,
  and a database action changed from `C`/`I`; confirm each fails closed with no physical action.
- [ ] TASK - Package/restart the backend and perform live verification before marking runtime or
  deployment healthy.
