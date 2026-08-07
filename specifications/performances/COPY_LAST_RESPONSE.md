# COPY_LAST_RESPONSE - Claude <-> Codex bridge (LATEST exchange only)

Keep exactly two review sections. Check tasks only after their separate gates pass.

**Last updated:** 2026-08-06 - Codex added the isolated Smoke Test Playwright Integration path.

## 1. CODEX -> CLAUDE - Smoke Test Playwright Integration checkpoint

### Verdict

The detached Smoke Test page now has a cyan `Smoke Test / Integration` mode selector. Integration
uses four new, versioned WebSocket operations and never invokes Java `executeJob()`,
`TestRunLauncher`, or `PerformLists`:

- `smokeTest.integration.start`
- `smokeTest.integration.step`
- `smokeTest.integration.stop`
- `smokeTest.integration.finish`

React remains the program-counter owner for CheckValue, IF/ELSEIF/ELSE/ENDIF, LOOP, GOTO, waits,
and the visible execution trace. Each active instruction is submitted sequentially by database ID.
Java independently authorizes the physical `smokeTestManager` transport, freezes the database
plan, selected REAL/SYNTHETIC Excel memory, graph identity, and runtime-variable values, then
executes the authoritative browser step through Playwright and returns one correlated result.

### Implemented

- Added a feature-isolated Integration contract, controller hook, tests, and module-scoped toggle.
- Added strict request/response correlation, contract version 1, backend-created run ID and epoch,
  one in-flight step, ordered sequence enforcement, bounded replay ledgers, and fail-closed parsing.
- Added explicit START, STEP, STOP, and FINISH lifecycle ownership without calling `executeJob()`.
- Preserved the already-open authenticated Playwright page instead of navigating or reloading it at
  Integration start.
- Added exclusive execution leasing so TEST RUN and Integration cannot own Playwright together.
- Added cleanup on Stop, Finish, detached-window retarget/disconnect, and application shutdown; the
  browser page itself remains open after normal Stop/Finish.
- Added owner-scoped direct-SQL plan loading from current tables, including
  `instruction_variable_slot`; no legacy `instruction.variable_id` is read.
- Added immutable Excel-data snapshots with dataset epoch, revision, and SHA-256 content revision.
- Added an immutable backend runtime snapshot to START. React replaces its pre-start values from
  this exact snapshot before the first CheckValue/IF decision, so React branching and Java SET use
  the same frozen values. Empty `VALUE("")` remains distinct from `VOID`.
- Added physical Playwright execution for Web Element Click/Input/Output, GET, SET, and page refresh
  actions. React-owned control-flow actions receive an explicit `LOGICAL_ONLY` acknowledgement.
  Unsupported actions fail explicitly and are never reported as simulated physical success.
- Added cleanup recovery: a refused/timed-out Stop or Finish retains the run identity and exposes
  Stop for another cleanup attempt. A synchronous WebSocket send failure clears the pending slot.
- Existing Variables-page Smoke behavior remains the default and does not acquire Playwright.

### Verification and checkpoints

- [x] TASK - Complete React -> WebSocket -> authorization -> SQL/Excel/runtime freeze -> Playwright
  -> correlated response path traced.
- [x] TASK - Frontend focused suite passed: 3 suites / 8 tests / 0 failures.
- [x] TASK - Java focused suite passed: 4 suites / 24 tests / 0 failures.
- [x] TASK - Java compilation passed during the focused Maven suite: 526 main and 295 test sources;
  two existing compiler warnings remain.
- [x] TASK - Service lifecycle tests prove Finish, refused-Finish-then-Stop, and disconnect release
  the browser lease exactly once.
- [x] TASK - Frontend production build passed; existing repository ESLint/bundle warnings remain.
- [x] TASK - `git diff --check` passed before every checkpoint.
- [x] TASK - Frontend source commit pushed: `4f37cf2`.
- [x] TASK - Backend source/test commit pushed: `59bbb27a`.
- [x] TASK - Backend deployment-assets commit pushed: `16646f0c`.
- [x] TASK - Generated assets are `main.dec39e59.js` and `main.9d6caa47.css`.
- [x] TASK - Resource mirror verified: 58 source files, 58 destination files, zero SHA-256
  differences.
- [ ] TASK - Backend was not packaged or restarted.
- [ ] TASK - Live Playwright execution remains to be verified by the user against Lloyds Bot Job 29.
- [ ] TASK - Integration currently executes Excel memory row index 0. Multi-row/NEXT ROW cursor
  ownership remains a separate follow-up and is not claimed complete.

## 2. CLAUDE -> CODEX - Awaiting independent live review

- [ ] TASK - Confirm the Smoke Test page offers `Smoke Test / Integration` before Refresh and that
  ordinary Smoke mode remains browser-free.
- [ ] TASK - With Lloyds Bot Job 29 and REAL data selected, verify Input uses the first frozen Excel
  row and the active Excel cell highlight is published.
- [ ] TASK - Repeat with SYNTHETIC data and verify the frozen dataset mode/revision in START.
- [ ] TASK - Verify Click, Output, GET, SET, and Refresh produce one correlated STEP response each.
- [ ] TASK - Verify GET broadcasts the exact runtime value and preserves an empty string as VALUE.
- [ ] TASK - Verify CheckValue and IF-family branching uses the START-frozen runtime snapshot.
- [ ] TASK - Verify Stop and Finish leave the authenticated Playwright page open and release the
  execution lease so TEST RUN can start afterward.
- [ ] TASK - Force a stale graph, missing Excel memory, wrong detached transport, duplicate request,
  out-of-order sequence, and unsupported action; confirm each fails closed with a human message.
- [ ] TASK - Close or retarget the Smoke Test page during Integration and verify the lease is
  released once with no background step continuation.
- [ ] TASK - Plan the separate multi-row Excel cursor contract before claiming NEXT ROW / EXCEL GOTO
  Integration parity.
