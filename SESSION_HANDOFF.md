# Session Handoff

## Current checkpoint - 2026-08-10

The dated 2026-07-15 scanner-removal notes below remain historical backlog. The authoritative Page
Mappings delivery checkpoint is:

- P0-P7 and all 12 original review-remediation findings are implemented and pushed. P5 is backend
  `c8e722cd` plus `823ab2dc` / frontend `14b7832`; P6 is backend `668a7acb`; P7 OCR Review is
  backend `89bbce24` / frontend `4dc51aa`.
- Legacy OCR Results production source remains retired in backend `07f3fd47` and frontend
  `b2d8a59`. OCR Config and Page Mappings OCR Review remain. The current catalog contains zero
  retired OCR Results entries.
- Snapshot ACL/retention foundations are backend `478a51b2` / frontend `dfd4836`; prior lifecycle
  hardening is backend `09fa2824` / frontend `cb64ab3` plus `6750c3b`.
- Item-7 verification added retention/snapshot coverage and fixed two defects found by it: STAGED
  recovery now uses the migration-compatible empty-string FAILED representation, and private ACL
  handling supports extended-length Windows paths. Frontend retention tests are `f8dd5aa`; backend
  tests and fixes are `380841af`.
- OCR Apply reconnect recovery is frontend `449f9ea`: read-only Review is retired on disconnect,
  while a mutating Apply preserves and resends its exact serialized request before bootstrap. Any
  timeout, malformed/stale response, invalidation, retarget, or backend ambiguity remains blocked
  until a correlated bootstrap and integrity-verified capture reload.
- Backend OCR recovery is `3e365b25`: requests are authorized before the bounded ledger, identical
  reconnects attach replacement transports, successful Applies alone are replay-cached, every
  terminal subscriber is reauthorized, and abrupt worker errors cannot wedge the lane. Alias
  commit/close ambiguity is typed and returns `reloadRequired=true`; settled responses survive the
  outer read-connection close path.
- The earlier SQLite frame `org.sqlite.core.DB.prepare(DB.java:264)` from automated verification
  came from an old test fixture missing `scanned_element.defined_name`. It is distinct from the
  later live BancaStato incident; the fixture now creates both `defined_name` and `client_named`.
- In the live BancaStato Page Scanner, the registry and both 93-entry JSON diagnostics were saved,
  then immutable snapshot persistence failed with `[SQLITE_ERROR] no such table:
  page_scan_snapshot`. The legacy scan remained available, but it produced no history/capture row.
- Frontend verification: the retention-focused run passed 15/15; the final JSDOM-only affected
  suite passed 44/44 across 8 suites. Backend verification: the snapshot/retention matrix passed
  71/71; the final non-browser OCR/session/retirement suite passed 106/106. No browser or native OCR
  process was launched.
- `npm run build` passed at frontend `a51e792` with existing repository warnings. The exact 58-file
  build mirror has zero path/hash differences and is pushed in backend `fb23c531`. Entrypoints are
  `main.eb4f02b1.js` (SHA-256
  `965E2A606FA0AA0A5744C0443BC1EF2FA97FDCE66EB83C4050BE0A5C82E83C56`) and
  `main.df7752f0.css` (SHA-256
  `912DEAE51E4B60DE97A1BEAEB74F6AAE6719EFBB54ED8D28388E77A1518AE70C`).
- Explicit `mvn compile` passed with 562 main Java sources on Java 17. Only the existing
  `InstructionLoad` Lombok and `TargetElementHelper` varargs warnings remained.
- `automation-tests.json` was regenerated and pushed in `d76362ff`, recording backend `3e365b25`
  and frontend `a51e792`: 2,341 catalog rows, 2,305 code cases, and 19,452 generated API requests.
  It contains 13 focused Page Mappings OCR cases and zero legacy OCR Results rows.
- An authorized maintenance window quiesced only the BancaStato ARWeb process tree and created the exact
  pre-migration backup
  `D:\Projects\ARWebBancaStato\ARWeb\Backup-CODEX-2026-08-10-page-scan-snapshot\database.db`
  (5,050,368 bytes; SHA-256
  `6256AEDB77C489060CC22F7F00E465349008265C3330ABE4E0D513F0375D8AD3`).
- Exactly `2026-08-07__page_scan_snapshot`,
  `2026-08-08__page_scan_snapshot_sqlserver_key_repair`, and
  `2026-08-08__page_scan_snapshot_view_fingerprint` were applied to
  `D:\Projects\ARWebBancaStato\ARWeb\database.db` in one SQLite transaction. The initial
  post-migration state had 24 migration rows, the expected 13-column table and indexes,
  `quick_check=ok`, zero foreign-key violations, zero snapshot rows, and no SQLite sidecar file.
- The targeted live scan then created the sole snapshot row:
  `16a2d848-6660-4f86-9786-5726d209d4e9`, owner Home Banking `13` / Bot Job `29` / Home URL `15`,
  captured `2026-08-10T12:12:10.059962200Z`, 93 elements, `READY`, final `pinned=0`, and manifest
  SHA-256 `e5e099c71f9d3099943121cd285627da991bd6d5a00f7c117bc90bd18c305bcd`.
- Its owner-scoped capture contains exactly `manifest.json`, `screenshot.png`, `elements.json`,
  `rects.json`, and `meta.json`. The manifest hash matches the database, each payload hash matches
  the manifest, and the capture chain plus all five files have protected ACLs limited to the
  process user, SYSTEM, and Administrators. No SQLite sidecar, deletion/retention journal, staging
  folder, or temporary snapshot artifact remains.
- Targeted acceptance exposed one backend ingress defect: both raw and decoded WebSocket guards
  used `contains("ping")`, so every `pageMappings.*` operation was silently swallowed because
  `Mappings` contains lowercase `ping`. Commit `70d5d08d` now ignores only exact `ping` or
  `ping-*` control frames and preserves the existing plain and encoded heartbeat producers.
- `mvn -DskipTests compile` passed with 562 Java sources and the two existing warnings. The targeted
  BancaStato IntelliJ-debug acceptance run used PID `2852`, started after the rebuilt class from
  `target/classes`, and listened on `127.0.0.1:65278` / `127.0.0.1:65279`. HTTP served the matching
  `main.eb4f02b1.js` and `main.df7752f0.css` assets. Live logs record
  `pageMappings.openResponse`, the `pageMappingsManager` connection, bootstrap, a 734,829-byte
  integrity-verified capture response, cache state, four explicit pin responses, and capture
  reload. No Page Mappings failure/error was recorded after the open request; one earlier Bot Job
  transport EOF reconnected before Page Mappings opened. PID `2852` is now stopped and both ports
  are closed, so no running-service health is claimed after the acceptance window.
- No backend package/image or other-database/SQL Server rollout was performed. The pre-existing
  orphan capture folder remains untouched; orphan inventory/reconciliation and broader live
  reconnect, takeover, retarget, deletion, same-ID reuse, Use Existing/Rescan, retention
  policy-save/purge, OCR/Memory, and multi-page acceptance remain open.
- Unrelated dirty Grid, Claude-settings, Marketing, patch, and screenshot files remain preserved
  and outside these commits. During final temporary-worktree cleanup, Git for Windows followed the
  worktree's `node_modules` junction into the original frontend checkout before failing on a path
  loop. The verified pushed tree was restored without overwriting the dirty `GridItemScann.tsx`
  (SHA-256 `7E8F12625D890E97F68EDC58482E0B1ACFB5D08A39156BF16F5D985136F04D45`), Git metadata was
  reconstructed at `a51e792`, and `npm rebuild --ignore-scripts` restored 168 dependency command
  shims without changing `package-lock.json`. The temporary worktree/junction was then removed.
  The two untracked generated `dev-server.*.log` files were deleted by the failed cleanup and were
  not recoverable from Git; final frontend status contains only the pre-existing Grid edit.

### Step 8 Bot Job retarget and cache-reuse correction - 2026-08-10

This checkpoint supersedes only the earlier current-runtime, Bot Job retarget, and Use Existing /
Rescan gate statements. Historical migration and acceptance evidence above remains unchanged.

- The apparent cross-owner history was not a history-query leak. Snapshot SQL was already scoped by
  Home Banking and Bot Job. Pre-fix Lloyds captures are genuinely stored as Bot Job 32 data because
  the process-global Playwright page retained Bot Job 29's page while the active owner changed.
  Those contaminated rows remain untouched and require explicit cleanup authorization.
- Backend `3721c049` strictly navigates the shared browser to the newly active Bot Job endpoint,
  closes it fail-closed if the owner switch cannot be confirmed, retargets the existing Page Scanner
  and Page Mappings generations, and revalidates detached transports against the active Registry.
  Backend mutation fencing also prevents Rescan, OCR Apply, pin, retention save, or purge from
  crossing a Bot Job generation or racing an authoritative bootstrap.
- Frontend `17748b8` makes WebSocket message-buffer generation explicit and atomic, resets both Page
  Mappings cursors before consuming a replacement buffer, clears stale owner-sensitive state, and
  keeps mutation controls disabled while an authoritative reload is required.
- Frontend `e23c6d6e` removes the duplicate shell Close control from Page Mappings. The page keeps
  one owner-aware Close button and retains its pending-mutation guards.
- Backend `c594ba5b` allows frame-hosted pages to reuse the top-document mapping only when captured
  locators remain top-document scoped. Live BancaStato Rescan then proved the remaining unsupported
  state was open Shadow DOM, not frames or the earlier long locator attribute.
- Backend `b9222d2f` adds a versioned, bounded open-Shadow-DOM structural fingerprint with explicit
  root and slot boundaries. Existing non-shadow fingerprint bytes remain unchanged. Because the
  current DTO/geometry contract cannot encode a ShadowRoot boundary safely, Playwright-pierced
  shadow descendants are deliberately omitted; iframe, nested-context, size, depth, and malformed
  scopes remain fail-closed.
- `mvn -DskipTests compile` passed with 562 Java sources and only the two existing warnings. A clean
  isolated frontend `npm run build` passed with existing repository warnings. Its exact 58-file
  resource mirror has zero path/hash differences and is pushed in backend `242095b2`.
- Current frontend entrypoints are `main.d31d8186.js` (2,055,381 bytes; SHA-256
  `81D457AF99A8CCEE16B5B6E323DE5FE0B2AEAC4698942E5E21CF1C3DC0E4A89E`) and
  `main.9afd0737.css` (489,796 bytes; SHA-256
  `4A1E4538BFF7E0FD0C6106BC2EAEAA6A6F4720D231E2153B617D309AA594B04B`).
- The final BancaStato runtime is PID `8032`, loaded from `target/classes` with the exact Config-4.2
  file and listening on `127.0.0.1:60711` / `127.0.0.1:60712`. Root, JS, and CSS return HTTP 200
  with the hashes above. Six `.10` logs contain zero error/exception/missing-table/snapshot-failure
  matches.
- A read-only post-restart database check reports `query_only=1`, `quick_check=ok`, 24 migrations,
  zero foreign-key violations, 13 READY snapshots, and no WAL/SHM/journal sidecar. No capture has a
  64-character fingerprint yet because no Rescan was run after `b9222d2f` became active.
- Before the final shadow-aware deployment, the user ran a normal Page Scanner scan and one real
  Page Mappings Rescan on the correct BancaStato URL. Both produced READY Job 32 captures without a
  backend error, but correctly retained blank fingerprints under the old Shadow DOM gate. Codex did
  not trigger either scan.
- No tests were created or run for Step 8, no package/image was built, and no other database or SQL
  Server installation was changed. Live completion still requires one user-driven Bot Job 29 -> 32
  switch with both detached pages open, one fresh Page Mappings Rescan after `b9222d2f`, and one Use
  Existing action proving no additional snapshot is created.

### Page Mappings controls, cleanup, and full-page rendering - 2026-08-10

This checkpoint supersedes the older Page Mappings UI, contaminated-capture cleanup, current-runtime,
and initial fixed-delay SCROLL PAGE status above. It does not change any other installation or SQL
Server database.

- Frontend `716a686` adds independent left-history and right-details vertical scrolling; `7264fa7`
  adds the captured-element search clear-X and established pulsing Pages badge treatment; `f5cb822`
  adds the focus-contained bottom-right help dialog and natural-width screenshot panning with aligned
  overlays; `7f04cbc` adds the correlated red/green `SCROLL PAGE` Rescan toggle; and `af6fffd`
  contains modal focus. The Page Mappings detached page still has one guarded Close action.
- `Use Existing` performs no scan and creates no snapshot. It integrity-loads the latest READY capture
  whose page fingerprint matches the current shared browser page, so selecting an older history row
  and then choosing `Use Existing` intentionally returns selection to that newest reusable row.
- Backend `260f2025` propagates the authenticated `scrollPage` flag through the owner/workspace-bound
  Rescan path. OFF preserves the existing live-DOM scan. ON performs bounded top-window traversal
  before fingerprinting/scanning and forces only that immutable capture to `full_page`; the original
  scroll position and exact page identity are restored/revalidated. Missing, blank, invalid, or
  out-of-range retention-days configuration now defaults to 30; an explicit configured `0` still
  disables age cleanup and max-unpinned fallback remains `0`.
- The exact two pre-fix Lloyds captures incorrectly stored under Home Banking 2 / Bot Job 32 were
  removed from the active SQLite history and their artifact folder was moved to the recoverable
  quarantine under
  `D:\Projects\ARWebBancaStato\ARWeb\Backup-CODEX-2026-08-10-job32-lloyds-cleanup`.
  Its database backup is 5,267,456 bytes with SHA-256
  `8B4BD1F19A535644824F953E2D7FDEA4291592020A7E082F1CBC1DF0D7F95716`. Current read-only evidence is
  `quick_check=ok`, 24 migrations, zero foreign-key violations, 17 READY Job 32 BancaStato captures,
  two pinned, zero Job 32 Lloyds rows, and no SQLite sidecar.
- The first live SCROLL PAGE implementation found the same 239 locators, but logs showed its fixed
  200 ms hops completed traversal in about 2.3 seconds while OCR availability rose across repeated
  scans from 10 to 25 to 51 words. The locator scan was stable, but the rendered pixels/text were
  still completing.
- Backend `20c8a4bc` replaces those fixed hops with a bounded adaptive render-ready gate: each
  viewport waits for paint frames, relevant DOM/class/style quiet, near-viewport image load/decode,
  fonts, and finite visible animations. It uses per-viewport and 45-second global deadlines, checks
  two stable bottom windows, applies the same readiness after restoring position, ignores infinite
  animations, and fails closed instead of persisting a known-incomplete capture.
- Frontend production build passed with existing repository warnings; its exact 58-file mirror is
  backend `6b2d1350`. Live entrypoints are `main.3f8cb24e.js` (2,062,835 bytes; SHA-256
  `089E7A4564C3345B3B4CE0DB2D8AAA9C734253D8A45FB38FA4252DAC2F131C58`) and
  `main.c51a1b29.css` (495,832 bytes; SHA-256
  `FC65A5462FF227DB8CAA8936C68DC2B6FCCFC611DB6BC38621C8BB59AF1918BA`); source and
  `target/classes` contain the same 58 paths and hashes.
- `mvn -DskipTests compile` passed with 563 main sources and the two existing warnings. No tests were
  created or run and no package/image was built. Backend `20c8a4bc` and frontend `af6fffd` are pushed
  to their upstream branches.
- PID `4428` now runs the adaptive class from `target/classes` with the exact BancaStato config on
  `127.0.0.1:54668` / `127.0.0.1:54669`. Root, JS, and CSS return HTTP 200 with the hashes above; the
  six new `.16` logs have zero relevant failure matches: error, exception, `SQLITE_ERROR`, snapshot
  failure, or render timeout. The old PID's forced-close EOF messages remain in `.15` and are not
  new-runtime failures.
- A fresh user-driven `SCROLL PAGE` Rescan is still required to compare the adaptive visual result.
  A bounded browser traversal cannot guarantee virtualized lists, nested scroll containers,
  canvas/video, CSS background resources, or unbounded infinite pages; these remain explicit limits.

### Per-Bot-Job SCROLL PAGES limit and redeployment - 2026-08-10

This checkpoint supersedes only the prior current-asset/runtime and fixed scroll-limit status. The
earlier implementation and incident history remain valid evidence.

- Frontend `d5f6dad` adds a styled `SCROLL PAGES` integer control with default `5` and range `1..40`.
  The acknowledged value is stored only in browser `localStorage` under
  `arweb.page-mappings.scroll-pages.<homeBankingId>.<botJobId>`, so Bot Jobs do not share the
  selection. The ON/OFF toggle remains transient and resets OFF. A dirty or invalid draft blocks
  SCROLL PAGE, Check page, Use Existing, and Rescan until the value is committed.
- Backend `805968ad` validates an explicitly supplied `scrollPages` only after exact detached-owner
  authorization, defaults a missing legacy field to `5`, and echoes the option through accepted and
  terminal Rescan frames. The budget counts only confirmed downward viewport movements; reaching
  stable bottom or the selected bounded count succeeds, while page-change, render, time, height, and
  stalled-scroll failures remain fail-closed.
- `npm run build` passed with the existing repository warnings. Deployment mirror `4e5813c0` contains
  exactly 58 frontend files and 19 image assets. Source resources and `target/classes` are byte-identical;
  24 obsolete target bundles were removed. The prior `main.3f8cb24e.js` / `main.c51a1b29.css` are absent.
  Current entrypoints are `main.15510fe8.js` (2,065,999 bytes; SHA-256
  `50F04B0F4BB47EF58F2A393A49415479D5D6F4C7704DA28BA939B0D5CE048902`) and
  `main.974b35cd.css` (498,096 bytes; SHA-256
  `7BCBDD73DD3F192571D806928F9E170E77E8AE7F06FD4F9DFCC666B4EC674E63`).
- `mvn -DskipTests compile` passed with 563 main sources and only the two existing warnings. No tests
  were created or run per user direction; no migration, backend package, or container image was made.
- PID `21796` now runs the rebuilt `target/classes` with the exact BancaStato config on
  `127.0.0.1:53734` / `127.0.0.1:53735`. HTTP root, JS, and CSS return 200 and the served asset hashes
  match `target/classes`; the six new `.17` logs contain zero relevant error matches.
- One user-driven Page Mappings Rescan with `SCROLL PAGE` ON and a selected non-default limit remains
  the live visual/behavior gate. Codex did not trigger a scan.

### Page Mappings header and element-total refresh - 2026-08-10

This checkpoint supersedes only the immediately preceding current frontend assets and PID.

- Frontend `cf16efe` adds `Total Web Elements: <count>` to the right of the captured-element search
  label. It uses the selected immutable capture's authoritative `elementCount`, so filtering and the
  200-row result-display cap never change or undercount it.
- The same isolated commit replaces the Page Mappings inset gradient shell/header with the established
  Main Dashboard pattern (`#eef3f8` page, zero outer padding, `#0b5394` top bar, 8px/12px spacing,
  compact title/subtitle). The existing two-column grid, responsive stacking, and independent left/right
  scroll owners are unchanged. The header is now the real floating-workspace drag handle and its actions
  remain excluded from dragging.
- `npm run build` passed with existing repository warnings. No tests, Maven command, Java compilation,
  migration, backend package, or container image was run or created.
- Deployment asset commit `98ae848b` mirrors exactly 58 files and 19 image assets into Java resources
  and `target/classes`; the prior bundles are absent. Current entrypoints are `main.92c3e040.js`
  (2,067,083 bytes; SHA-256 `2E288579487DE1E0FE8C4C9D85E9AC70B9782749D091D485AE3EA8C69BFA329F`)
  and `main.1ac3c57f.css` (498,284 bytes; SHA-256
  `00197A6964A29C506217792B5F37E47EA4BB5BF9E351883F5DA731D6A968B4AE`).
- PID `12944` runs the unchanged Java classes with the exact BancaStato config on
  `127.0.0.1:55720` / `127.0.0.1:55721`. Root/JS/CSS return 200 with matching hashes; the six new
  `.18` logs contain zero error or strict operational-failure matches.
- Read-only SQLite evidence: Home Banking 2 has 881 active scanned-element rows (Job 5: 549 across
  three page keys; Job 32: 332 across two page keys). The latest Job 32 READY capture
  `454c45cd-cd5b-4a9c-8e70-6b15ab437713` contains 239 elements; Job 32 has 18 READY captures.

### Smoke Test Playwright page refresh - 2026-08-10

This checkpoint supersedes only the immediately preceding current frontend assets and runtime PID.

- Frontend `7d5a157` adds a compact two-line `Refresh` / `Web Page` control immediately before
  `Stop` on the Smoke Test toolbar. Its isolated style follows the established Real/Synthetic Data
  toggle dimensions and visual language. It is enabled only for the authoritative Integration
  workspace while no integration run or execution owns the browser.
- Backend source `aab60fca` adds the exact `smokeTest.integration.refresh` contract. It authorizes the
  current Smoke Test transport, binding, Bot Job workspace, owner, and graph revision before
  reserving the shared Playwright browser; then it calls the current page's Playwright reload and
  bounded settle path while holding the Bot Job mutation generation. It does not reload the React
  Smoke Test page.
- `npm run build` passed with existing repository warnings. `mvn -DskipTests compile` passed with
  564 main Java sources and only the existing Lombok/varargs warnings. No tests ran per user direction.
- Deployment commit `cd9bf34a` mirrors exactly 58 frontend files and 19 images into Java resources
  and `target/classes`. Current entrypoints are `main.9a55ef9b.js` (2,077,464 bytes; SHA-256
  `379BB80F481BFE97BB563BFAA98071125BEDDA06A97F85DD4F8DE53940F965F9`) and
  `main.069de826.css` (506,255 bytes; SHA-256
  `08535CB786F8B8A3D27FCA4BFF7953F48B1B20B27667A279337DBCD98101C16F`).
- PID `17864` ran the rebuilt `target/classes` with the exact BancaStato config on
  `127.0.0.1:62590` / `127.0.0.1:62591`. HTTP root, JS, and CSS returned 200 with the exact hashes
  above. A user action reached the new endpoint and was safely refused because no Bot Job Playwright
  page was open. The Main window was then closed; logs record normal application shutdown and PID
  `17864` is stopped. One unrelated browser-console 404 is present, with no JVM/SQLite/snapshot error.
- The remaining live gate is to restart ARWeb, open Lloyds Bot Job 29 and its Playwright page first,
  then open Smoke Test Integration and click Refresh Web Page while idle. Codex did not click the
  button or run a test.
- A subsequent `Step 0` failure was not a Lloyds Bot Job 29 plan. Read-only SQLite/log evidence shows
  the workspace was Bot Job 32 and selected inactive Block `204` (`Registra eBill`, order 10,
  `active=0`). The React flow intentionally displays inactive blocks as bypassed, but the start
  request incorrectly included their IDs while the authoritative backend accepts active IDs only.
- Frontend `124ecb6` now derives the Integration scope from selected active plan blocks. Inactive
  blocks remain visible and locally reported as bypassed; they are not sent for database/Playwright
  execution. An inactive-only selection returns `Select at least one active Block...` and the hook
  safely returns to IDLE instead of remaining in STARTING.
- No tests or Maven command ran for this correction. `npm run build` passed with existing warnings;
  deployment `1883a1bc` mirrors 58 exact files. PID `11496` runs the unchanged Java classes on
  `127.0.0.1:61402` / `127.0.0.1:61403` and serves `main.da89dda3.js` (2,077,618 bytes; SHA-256
  `A9CFBE95199F991D5CF0D1CD2D42200F24E5DE56D08ED413E69064D9E35FEC61`) plus
  `main.634ba30e.css` (506,166 bytes; SHA-256
  `BFFC0DF3E252F977CED9A36791D481D00509474249B9D3052CA7EAF397178B64`). Six `.23` logs contain
  zero strict/error matches at the deployment checkpoint.

### Smoke Test live instruction controls and unified Playwright healing - 2026-08-11

This checkpoint supersedes only the current Smoke Test controls, frontend assets, and runtime PID.
The Page Mappings checkpoints and the still-open user-driven Lloyds execution gate remain unchanged.

- Backend `4c11186e` routes both full Smoke Test Integration and manual row `TEST INPUT` / `TEST
  CLICK` through the same server-owned `RuntimeElementHealingService` and Playwright runtime
  executor. Selector resolution remains owner/current-page scoped and ordered: authored locators,
  current `scanned_element` mappings/stable attributes, then unique canonical/client alias matches;
  coordinates remain last. An ambiguous early selector no longer hides a later unique higher-quality
  locator, and no physical action runs until one compatible actionable target is pinned.
- The existing `gridItem.testAction` contract now accepts the exact active `smokeTestManager`
  transport as well as Bot Job Details. It reauthorizes the current Smoke Test binding, owner,
  workspace epoch, and graph revision before execution and before terminal delivery. Manual INPUT
  keeps the existing selected Excel-row/display-key behavior and its explicit `ABC` fallback when no
  selected dataset is available; full Integration continues to use its frozen execution data.
- The existing `variablesWorkspace.commands.status` mutation now accepts the same exact active Smoke
  Test transport. Active/inactive state still has one database source of truth and is published to
  every synchronized Bot Job/Smoke Test consumer after the authoritative mutation.
- Frontend `f7f9aae` adds isolated right-side row controls to the Smoke Test execution-flow cards:
  green/red active state, blue `TEST INPUT`, and orange `TEST CLICK`, with SVG icons, pending states,
  narrow responsive layout, and the same shared hooks/contracts used by GridItem. Controls are
  disabled while disconnected, stale, unbound, Integration is running, or another row action is
  pending.
- Focused Java verification passed twice after the final source correction: 47 tests across
  `GridItemTestActionServiceTest`, `GridItemTestActionExecutorTest`,
  `SmokeTestIntegrationStepExecutorTest`, and `VariablesWorkspaceServiceTest`, with zero failures,
  errors, or skips. `mvn -DskipTests compile` passed with 564 main sources and only the existing
  Lombok/varargs warnings. No frontend test suite or real Integration execution was run.
- Automation catalog `2b11e657` was regenerated after the final source/deployment commits without
  executing tests. It records backend `3fcee24c`, frontend `f7f9aae`, 2,341 catalog rows, 2,305 code
  cases, and 19,452 generated API requests.
- `npm run build` passed with existing repository warnings. Deployment `3fcee24c` mirrors exactly 58
  files into Java resources and `target/classes`; old `main.6b8f4c4a.js` /
  `main.1336e287.css` are absent. Current entrypoints are `main.0d1c19c7.js` (2,089,651 bytes;
  SHA-256 `D7485EE02FF812470E5467FE164EDBA190645E91D744BF95D515231EE0402F22`) and
  `main.11a8513b.css` (508,962 bytes; SHA-256
  `89C813BE043B8A39BD450667E9FAF35D39344B703E893DAEB0642A039A688406`).
- PID `31360` runs the rebuilt `target/classes` with the exact BancaStato config on
  `127.0.0.1:62094` / `127.0.0.1:62095`. HTTP root/JS/CSS return 200 with exact target hashes. The
  desktop shell opened, `smokeTestManager` connected, and live logs record Smoke bootstrap plus
  `variablesWorkspace.commands.statusResponse`. At the restart checkpoint the six post-start logs
  contained zero strict matches; subsequent user browsing added only two page-console resource
  responses (HTTP 404 and 400), with no JVM exception, SQLite, missing-table, snapshot, or WebSocket
  operation failure.
- The in-app browser-control surface was unavailable, so no visual button approval, manual row
  click/input, Refresh Web Page action, or Lloyds Integration run was performed by Codex. No database
  migration, backend package, or container image was created. Those live user actions remain separate
  acceptance gates and must not be inferred from compile/build/deployment evidence.

### Smoke Test execution-type controls and exact binding - 2026-08-11

This checkpoint supersedes only the immediately preceding Smoke Test row-control assets and runtime
PID. The remaining Lloyds Integration and Page Mappings live gates are unchanged.

- Backend `a1d6bd3e` extends the existing authoritative `gridItem.webElementType.update` contract to
  the exact active `smokeTestManager` transport. It revalidates the Smoke binding, owner, workspace
  epoch, and graph revision before the existing exact-one database mutation, then publishes the
  synchronized workspace snapshot without broadening Bot Job transport authority.
- Frontend `3fde7be` reuses the established `WebElementTypeToggle` in every applicable Smoke Test flow
  row, preserving the `INPUT -> OUTPUT -> CLICK` cycle and database-backed semantics used by GridItem.
  The Active/Inactive control is now the requested icon-only power button, and the power, type, Test
  Input, and Test Click controls remain on one horizontally scrollable row. The same change includes
  the missing Smoke `bindingEpoch` on manual Test Input/Click requests, which had caused earlier
  requests to be refused before reaching the Playwright executor.
- Focused Java verification for the execution-type service passed 13/13 with zero failures, errors,
  or skips. Final `mvn -DskipTests compile` passed with 564 main sources and only the two existing
  Lombok/varargs warnings. `npm run build` passed with existing repository warnings; no frontend
  test suite or live Integration execution was run.
- Deployment `74168d27` mirrors the 58-file frontend production build; catalog `3609803d` records
  2,341 rows, 2,305 code cases, and 19,452 generated API requests without executing tests. Source
  resources and `target/classes` match exactly, including 19 image assets, and five stale target-only
  bundles were removed.
- Current entrypoints are `main.45672047.js` (2,091,300 bytes; SHA-256
  `C793B11EC3E6D7496B83C721A2AA8B085D7C756CC8AB12067815F3C9424A1157`) and
  `main.aacbfa82.css` (509,093 bytes; SHA-256
  `0EAD57019FEDDE86C53558714F1A3F3F9B3C6378B2573EA9D2CB90DA335C908B`). PID `20668` runs the
  rebuilt `target/classes` with the exact BancaStato config on `127.0.0.1:64433` / `64434`; HTTP
  root and both assets return 200 with matching hashes. The six new `.3` logs contain zero strict
  Java/SQLite/snapshot/WebSocket failure matches at the deployment checkpoint.
- Live acceptance remains user-driven: visually confirm the single-row layout, change one safe
  instruction type and verify Bot Job synchronization, then execute one safe Test Input/Click with
  Lloyds Bot Job 29 and its Playwright page open. No database migration, package, or container image
  was created.

### Smoke Integration selected-page startup and Stop recovery - 2026-08-11

This checkpoint supersedes only the current Smoke Integration startup/Stop behavior, frontend
assets, and runtime PID. The locator-refusal diagnostics and Page Mappings live gates remain
separate.

- Live evidence confirmed that Integration START could acquire a newly adopted Playwright tab while
  it was still `about:blank`, because the old startup contract intentionally preserved any open
  page. Backend `9fe40dbf` now strictly opens/navigates to the selected Bot Job plan URL and waits
  for the Playwright page-settled gate before returning STARTED. The navigation and settle are held
  inside the exact Bot Job workspace generation, so a concurrent Bot Job switch refuses startup.
- The observed Stop lockout was a frontend terminal race: Stop canceled the pending row action, the
  rejected action restored READY, and another Stop path could cancel/replace the first terminal
  acknowledgement and leave `CLEANUP_REQUIRED`. Frontend `882af61` makes Stop/Finish single-flight,
  prevents a canceled step from overwriting terminal phase, and consumes replacement WebSocket
  message buffers using their explicit generation.
- Focused verification passed: `SmokeTestIntegrationServiceTest` 3/3 and the two focused frontend
  Smoke Integration suites 8/8. Java compilation ran as part of the focused Maven check with 564
  main sources; only the existing Lombok-builder and inexact-varargs warnings remained. The
  frontend checks retained only existing React `act`/open-handle dependency warnings.
- `npm run build` passed with existing repository warnings. Deployment `4dade5a0` mirrors exactly
  58 files into Java resources and `target/classes`; the stale `main.45672047.js` set is absent.
  Current entrypoints are `main.6a91a10f.js` (2,091,547 bytes; SHA-256
  `36DA34C21B87826BFC1939E7BA8AACE1833F1BECA9876B2F7F3AE01C08CDEE36`) and
  `main.aacbfa82.css` (509,093 bytes; SHA-256
  `0EAD57019FEDDE86C53558714F1A3F3F9B3C6378B2573EA9D2CB90DA335C908B`).
- Catalog `ce41e3f7` records backend deployment `4dade5a0`, frontend `882af61`, 2,342 rows,
  2,306 code cases, and 19,452 generated API requests. Generation executed no tests.
- PID `29912` runs the rebuilt `target/classes` with the exact BancaStato config on
  `127.0.0.1:59091` / `127.0.0.1:59092`. HTTP root, JS, and CSS return 200 with exact target
  hashes. The six new `.5/.4` logs contain zero strict Java/SQLite/snapshot/Smoke-start failure
  matches.
- Live user acceptance remains: open Lloyds Bot Job 29 and start Integration without manually
  preparing the browser; verify it opens/settles the Lloyds URL, then press Stop during or after one
  safe action and confirm the row controls recover without switching Bot Jobs. Codex did not run a
  bank action after deployment. The reported `COORDINATE_TARGET_INVALID` and `AMBIGUOUS_TARGET`
  refusals remain correct fail-closed locator diagnostics and were not weakened by this fix.
- No migration, distributable backend package, or container image was created. Unrelated dirty
  Claude/settings, Marketing, patch, and screenshot files remain preserved.

### Smoke runtime locator-strength correction - 2026-08-11

This checkpoint supersedes only the current runtime PID and the prior statement that the Lloyds
`AMBIGUOUS_TARGET` outcome required no source correction.

- Live evidence for instruction `1749` (`personal_2`) showed one exact scanned registry row but 73
  prepared registry candidates because its shared CSS `span.btn-text` was treated as equal to its
  exact XPath. Two live spans survived, so the runtime correctly performed zero physical actions.
- Backend `453710d2` preserves locator strength: exact XPath matches outrank stable identity
  attributes, which outrank CSS-only matches. If the one authoritative registry candidate still
  has a broad selector, its persisted `scanned-text` may narrow the DOM candidates, but execution
  still requires exactly one visible, boundary-compatible, action-compatible element. Multiple
  semantic matches remain `AMBIGUOUS_TARGET` with no physical action.
- Focused `RuntimeElementHealingServiceTest` verification passed 2/2 and compiled all 564 main Java
  sources; only the existing Lombok-builder and inexact-varargs warnings remained. No browser or
  Integration action was executed by Codex.
- Catalog `6d88c97c` was regenerated without executing tests and now records 2,344 rows, 2,308 code
  cases, and 19,452 generated API requests.
- PID `27756` runs the rebuilt `target/classes` with the exact BancaStato config on
  `127.0.0.1:59032` / `127.0.0.1:59033`; HTTP root returns 200 and the fresh `.5/.6` log set has no
  strict runtime-healing, SQLite, snapshot, or JVM failure match. Frontend assets were unchanged.
- Live user acceptance remains: retry instruction `1749` on the Lloyds page and confirm one
  physical action or, if the exact scanned text is genuinely duplicated, a fail-closed ambiguity
  with the reduced authoritative candidate count.

### License Request status and validation UI - 2026-08-11

This checkpoint supersedes only the current frontend assets and runtime PID. It does not close the
Lloyds Smoke Integration or Page Mappings live-acceptance gates above.

- Frontend `1e8d528` renames the detached Info workspace to `About this Software` and the License
  workspace/header to `License Request`. The License header now has the established compact
  green/yellow/red status treatment for Ready, progress, connection/backend errors, malformed
  responses, and client-side mandatory-field errors.
- License Request validates Organization, Owner, email presence/format, response/license file, and
  agreement acceptance before sending. Backend license state remains authoritative; the existing
  detailed Active/Required banner is preserved separately from transient operation feedback.
- Focused frontend verification passed 2 suites / 8 tests. `npm run build` passed with existing
  repository warnings. No Java source changed and no Java test ran.
- Deployment `c320f5a6` mirrors exactly 58 frontend files and 19 image assets. Current entrypoints
  are `main.7a606860.js` (2,093,729 bytes; SHA-256
  `3E0C2D347E8861C68D04208ED7F352146DE1FC17B184869A4E6464448277DD48`) and
  `main.834b1a93.css` (510,094 bytes; SHA-256
  `B2EACC407DF28A4CCA57F3B1AAE8770BC5C19164735052743D5AA8B9E04E87C3`). Source resources and
  `target/classes/build` have zero path/hash differences.
- The backend `target` directory and prior ARWeb process disappeared externally during this
  checkpoint. A no-test `mvn -DskipTests compile` recreated 1,442 class files and copied the exact
  frontend resources; no Java source was modified. PID `28552` now runs the rebuilt
  `target/classes` with the exact BancaStato config on `127.0.0.1:53768` / `53769`.
- HTTP root, JS, and CSS return 200 with exact target hashes, and the deployed JS contains the new
  titles and required-field messages. Catalog `2f0db93e` records 2,344 rows, 2,308 code cases, and
  19,452 generated API requests; catalog generation executed no tests.
- No controllable in-app browser was attached, so visual License/About acceptance is not claimed.
  No migration, package, or container image was created. Unrelated dirty settings/specification/
  screenshot files remain preserved.

### Page Mappings Scan Flow and remaining-verification audit - 2026-08-11

- Backend `dd38963f`, frontend `08957d6`, and deployment `98d59860` already deliver the read-only,
  owner-scoped `Scan Flow` tree. The modal groups the current `scanned_element` registry by
  organization, Bot Job, and page. Its local filter applies to the visible tree and recomputes the
  Web Elements, Bot Jobs, and Pages cards; it is intentionally a compact tree rather than the Smoke
  Test execution graph.
- The latest production bundle still contains this feature, but it has no dedicated frontend modal
  test, bootstrap/parser/retarget test, or backend `PageMappingsScanInventoryService` test. Live
  visual filtering and large-inventory behavior are also unverified.
- Read-only live SQLite evidence is currently 881 Home Banking 2 registry rows, two Bot Jobs, and
  five page keys: Job 5 has 549 rows across three pages; Job 32 has 332 rows across two pages. Job
  32 has 20 READY immutable captures, two pinned; the latest is
  `6fcf159d-2585-4cac-a6e9-6297ffd4a3cd`, 239 elements, READY, with a 64-character fingerprint.
  Database `quick_check=ok` and foreign-key violations are zero.
- The 881 owner total is not a clean BancaStato-page total: 93 cumulative Job 32 registry rows still
  identify `https://www.lloydsbank.com/`. The earlier guarded cleanup removed the two wrong
  `page_scan_snapshot` rows/artifacts only. These 93 registry rows require a separate reference/
  instruction audit and explicit cleanup authorization; they were not modified in this checkpoint.
- Highest-priority missing tests are: inventory owner isolation/counts/redaction; modal filter/card/
  tree/clear/focus behavior; malformed inventory and retarget clearing; Use Existing zero-write;
  non-default scroll option/adaptive failure paths; retention Save/Purge; OCR Apply; Memory staging/
  Apply; and disconnect/takeover/same-ID/multi-page lifecycle behavior. Live acceptance, legacy
  orphan inventory, other-database/SQL Server rollout, and package/image delivery remain separate.

### Page Mappings OCR Review guidance and alias rollback - 2026-08-11

This checkpoint supersedes only the current Page Mappings OCR help, OCR proposal presentation, and
frontend asset/runtime status. OCR authorization, transaction, reconnect, and persistence contracts
remain unchanged.

- Frontend `4ff3a99` expands the `Immutable capture workspace / Page Mappings rules` dialog with the
  selected-screenshot OCR workflow, quality meanings, safe one-row test, exact Apply scope and
  non-effects, Memory List behavior, limits, rollback, and unknown-outcome recovery.
- Read-only live SQLite evidence proved the user's Apply had succeeded: `scanned_element.id=672`,
  Bot Job 32, canonical `bancastato`, has `client_named=Banca Stato`. The apparent failure was
  frontend state: changing Proposed name did not select Use, and a successful Apply replaced the
  row object, causing the panel to initialize its draft again from the old OCR text and select it as
  another pending change.
- Frontend `3f67e5a` makes a valid edited proposal select its Use checkbox, preserves the acknowledged
  draft for the same OCR Review request, and clears selection when that draft becomes authoritative.
  A compact restore icon appears only for rows with a client alias and immediately submits one exact
  `clientNamed=null` change through the existing all-or-nothing OCR Apply path. No Rescan or new
  backend operation is involved.
- A successful Apply still refreshes the loaded immutable-capture projection and any matching staged
  Memory List label/revision. Adding or dragging afterward carries the saved alias; creating or
  changing an instruction remains the separate Memory List Apply action.
- Both frontend production builds passed with existing repository warnings; no test suite, Maven
  command, or Java compilation ran. Final deployment `64f499e1` mirrors exactly 58 files and 19
  images; catalog `91bab2a3` records 2,344 rows / 2,308 code cases without executing tests.
- Current entrypoints are `main.b8284312.js` (2,097,331 bytes; SHA-256
  `81FC483A4E71999A1E2723FC37F8C69665C7137DCBD4EE1F8CAFFCE9DCDCB045`) and
  `main.680e6c4a.css` (510,746 bytes; SHA-256
  `045304EA50C5F8B9CEAB94F5478DF6DE9B13767A4759AF0D760209006356F65B`). PID `1556` serves the
  exact bytes on `127.0.0.1:57395` / `57396`; root/JS/CSS return HTTP 200. Six startup logs contain
  no Java/SQLite/snapshot failure; one old-client WebSocket EOF followed restart.
- The in-app browser surface was unavailable. User live acceptance remains: reopen Page Mappings,
  confirm the saved `Banca Stato` row no longer jumps back to the OCR draft, use the restore icon only
  if the alias should be cleared, and verify Add/drag plus the separate Memory List Apply workflow.

### Page Mappings clickable Memory List card - 2026-08-11

This checkpoint supersedes only the current Page Mappings Memory-card source/build assets. It does
not supersede the OCR, Memory Apply, runtime, or live-acceptance gates above.

- Root cause: the `Memory List / N selected` area was a passive section. Only Add/drop set the
  `memoryList.open` request, so clicking the visible summary could not open or focus Memory List.
- Frontend `0cd8bed` makes the complete card mouse- and keyboard-actionable. It reuses the existing
  exact-owner `memoryList.open` contract, including for an empty selection, and does not modify,
  apply, or delete staged items. Rapid clicks are refused while the correlated Memory request is
  pending.
- `Drop captured elements here, or use Add.` is now a cyan glowing badge. The whole card has clear
  hover/focus treatment and the animation is disabled for reduced-motion users.
- The reported `received the focus request, but Windows did not confirm foreground activation`
  response is a successful browser-focus delivery with an unconfirmed Windows foreground result.
  The backend already uses the exact title-token HWND, restores minimized windows, attaches input,
  requests foreground/focus, and pulses topmost. Windows may still refuse foreground ownership; no
  shared native-focus behavior or truthful diagnostic was weakened in this frontend checkpoint.
- `npm run build` passed with existing repository warnings; no test suite, Maven command, or Java
  compilation ran. Resource deployment `bfe4cf87` mirrors exactly 58 files and catalog `729f0850`
  records 2,344 rows / 2,308 code cases without executing tests.
- New source-resource entrypoints are `main.f99c04f5.js` (2,098,249 bytes; SHA-256
  `BB37ED7E30ED6999FDEF998D461FADB9B1F9B288F300540DA24E9410F9B7DAE2`) and
  `main.ff26b7fd.css` (511,970 bytes; SHA-256
  `4D4A19FB43ABC7163F988D905E39FA04E9A33CD3A6F72353CD5568EC6A46141F`). The previous PID `1556`
  ended externally. No `target/classes` copy, service restart, or live visual click-through is
  claimed because the user was using the VPN/production browser session.

### Page Mappings client-name priority and instruction guidance - 2026-08-11

This checkpoint supersedes only the current OCR proposal/help assets and deployment-copy status. It
does not change the backend OCR transaction, locator identity, or cross-page execution authority.

- Root cause confirmed: SQLite row `672` already preserved `client_named=Banca Stato`, and
  `ScannedElementRepository.upsert` already carries the existing alias through Rescan. The remaining
  frontend proposal function placed OCR text before the saved alias, so Run again could display and
  preselect `€ BancaStato` as a replacement even though the database had not rolled back.
- Frontend `57c3118` makes a nonblank saved `client_named` the Proposed-name default and compares
  normalized aliases consistently. Run again leaves Use unselected for an existing alias; OCR text
  remains visible evidence. Only an explicit edit/select/Apply can replace it, and Restore remains
  the explicit null-alias rollback.
- The same checkpoint splits `Immutable capture workspace / Page Mappings rules` into accessible
  `Workspace Rules` and `Client Names & Instructions` tabs. The new tab documents canonical versus
  client names, Rescan/OCR preservation, migration-reference intent, Add/drag staging, the separate
  Memory List Apply transaction, conflict safety, and the rule that aliases never silently join
  unrelated elements or bypass exact locator/page identity.
- Focused frontend verification passed 2 suites / 3 tests. The production build passed with only
  existing repository warnings. No Java source changed, so no Maven command or Java compilation ran.
- Resource deployment `e3469fb7` and catalog `3db5da92` are pushed. The catalog records 2,347 rows /
  2,311 code cases and was regenerated without executing tests.
- The exact 58-file/19-image build is mirrored into both source resources and `target/classes` with
  zero hash differences; 26 stale target files were removed. Entrypoints are `main.0b70d82f.js`
  (2,102,401 bytes; SHA-256
  `1F1B5A29BB1917E18C035715FD4EC4FA526B46034A058768AB400C4392513C89`) and
  `main.8822f0dc.css` (512,809 bytes; SHA-256
  `8290860100F7E9284FD30031BDBE45B3A022C0118F690D6999DE65F84B6BBC9B`).
- No ARControlPanel process was running during the target copy. User live gate: start the app,
  reopen Page Mappings, confirm the new tabs, run OCR Review again, and verify saved `Banca Stato`
  remains Current and Proposed with Use unchecked. Do not use Restore unless clearing the alias is
  intended.

Read `specifications/performances/COPY_LAST_RESPONSE.md` and
`specifications/performances/Page Mappins PLAN 2026-08-07.md` before continuing.

Date: 2026-07-15

## Current Goal

Continue the Scanner / AR Web Factory migration removal.

Direction from the user:

- Keep Java as the minimal backend/service side.
- Move scanner frontend logic into the React/TypeScript container.
- Create/extract new TypeScript methods/functions and typed WebSocket contracts.
- Test with Playwright/browser validation as much as possible when the app can launch cleanly.
- For each medium migration modification, commit and push.
- Do not change frontend design unless required for the migration.

## Repositories

Backend:

- Path: `D:\Projects\ar-web-selenium`
- Branch: `refactor/perform-actions-decomposition`
- Status when written: clean except this handoff update
- Recent commits:
  - `ebdee6c0 test: reuse scanner contract constants`
  - `3dcb9733 refactor: reuse scanner operation id in pre scan host`
  - `34640069 refactor: reuse scanner constants in browser services`
  - `d3923855 refactor: centralize scanner search terms operation`
  - `ef626b03 refactor: reuse scanner session ids in pre scan workspace`

Frontend:

- Path: `D:\Projects\ar-react-ts-grid`
- Branch: `VERSION-4.6`
- Status when written: `src/components/GridItemScannMobile.tsx` is modified and needs the next pass
- Recent commits:
  - `ff476a6 refactor: reuse scanner sessions in grid messages`
  - `f27ff57 refactor: reuse scanner session ids in app routing`
  - `e48232c refactor: centralize scanner compatibility ids`
  - `209f8cc refactor: centralize scanner session ids`
  - `55dc621 refactor: centralize scanner baseline statuses`

## Files To Read First

Read these first before continuing:

1. Frontend active dirty slice:
   - `D:\Projects\ar-react-ts-grid\src\components\GridItemScannMobile.tsx`
   - `D:\Projects\ar-react-ts-grid\src\components\scanner\Scanner.operations.ts`
   - `D:\Projects\ar-react-ts-grid\src\components\scanner\Scanner.sessions.ts`
   - `D:\Projects\ar-react-ts-grid\src\components\scanner\Scanner.controllerStatus.ts`
   - `D:\Projects\ar-react-ts-grid\src\components\scanner\Scanner.controllerTiming.ts`

2. Frontend scanner comparison files:
   - `D:\Projects\ar-react-ts-grid\src\components\GridItemScann.tsx`
   - `D:\Projects\ar-react-ts-grid\src\components\GridItem.tsx`
   - `D:\Projects\ar-react-ts-grid\src\components\GridItemComp.tsx`
   - `D:\Projects\ar-react-ts-grid\src\index.tsx`
   - `D:\Projects\ar-react-ts-grid\src\components\scanner\*.test.ts`

3. Backend scanner contract/constants:
   - `src/main/java/com/allinweb/ch/scanner/ScannerWorkspaceOperations.java`
   - `src/main/java/com/allinweb/ch/scanner/ScannerWorkspaceSessions.java`
   - `src/main/java/com/allinweb/ch/scanner/ScannerWorkspacePayloads.java`
   - `src/test/java/com/allinweb/ch/scanner/ScannerWorkspaceOperationsTest.java`
   - `src/test/java/com/allinweb/ch/scanner/ScannerWorkspaceSessionsTest.java`

4. Backend biggest remaining runtime cleanup:
   - `src/main/java/com/allinweb/ch/websocket/SimpleWebSocketServer.java`

5. JavaFX legacy scanner files:
   - `src/main/java/com/allinweb/ch/component/pane/ARScannedElementPane.java`
   - `src/main/java/com/allinweb/ch/component/scene/ARScannedElementScene.java`

6. Smaller backend cleanup targets:
   - `src/main/java/com/allinweb/ch/service/GenFlowService.java`
   - `src/main/java/com/allinweb/ch/component/pane/PerformLists.java`
   - `src/main/java/com/allinweb/ch/component/pane/PerformListElements.java`
   - `src/main/java/com/allinweb/ch/component/pane/PerformPreLoad.java`
   - `src/main/java/com/allinweb/ch/component/pane/PerformCloseBrowser.java`
   - `src/main/java/com/allinweb/ch/plugin/PluginContext.java`
   - `src/main/java/com/allinweb/ch/component/pane/BotJobDetailsWorkspaceHost.java`

## What Is Missing To Finish

### 1. Finish The Current Frontend Mobile Scanner Slice

Current active file:

- `D:\Projects\ar-react-ts-grid\src\components\GridItemScannMobile.tsx`

Next actions:

- Review the current diff in `GridItemScannMobile.tsx`.
- Replace remaining scanner operation/session literals where safe.
- Prefer existing constants from:
  - `Scanner.operations.ts`
  - `Scanner.sessions.ts`
- Likely remaining safe replacement:
  - replace runtime `searchTerms` operation checks with `SCANNER_SEARCH_TERMS_OPERATION`
- Keep payload field names such as JSON property `searchTerms` only where they are actual wire payload keys.
- Run frontend scanner tests and build.
- Commit and push this slice.

Suggested commands:

```powershell
git -C D:\Projects\ar-react-ts-grid diff -- src/components/GridItemScannMobile.tsx
rg -n -C 3 "searchTerms|scannerTool|scannerGrid|preScannerGrid|scanner-element-pane" D:\Projects\ar-react-ts-grid\src\components\GridItemScannMobile.tsx
npm test -- --watchAll=false src/components/scanner
npm run build
git -C D:\Projects\ar-react-ts-grid diff --check
git -C D:\Projects\ar-react-ts-grid add src/components/GridItemScannMobile.tsx
git -C D:\Projects\ar-react-ts-grid commit -m "refactor: reuse scanner operation id in mobile grid"
git -C D:\Projects\ar-react-ts-grid push origin VERSION-4.6
```

### 2. Backend WebSocket Server Cleanup

Biggest remaining backend runtime file:

- `src/main/java/com/allinweb/ch/websocket/SimpleWebSocketServer.java`

Known runtime literals still to clean carefully:

- `scannerGrid`
- `preScannerGrid`
- `scannerTool`
- `scanner-element-pane`

Likely work:

- Reuse `ScannerWorkspaceSessions.SCANNER_GRID`.
- Reuse `ScannerWorkspaceSessions.PRE_SCANNER_GRID`.
- Reuse `ScannerWorkspaceSessions.SCANNER_TOOL`.
- Add `ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE = "scanner-element-pane"` if it does not exist yet.
- Update `ScannerWorkspaceSessionsTest`.
- Replace exact string comparisons and sends first.
- Be careful with regex checks such as `matches(".*scannerTool.*")`; replace with helper methods or constant-based contains logic only when behavior is identical.
- Keep compatibility destinations until React owns the replacement route.

Suggested focused tests after this slice:

```powershell
& 'D:\Installed\apache-maven-3.9.16\bin\mvn.cmd' '-Dtest=ScannerWorkspaceSessionsTest,ScannerWorkspaceOperationsTest,ScannerWorkspaceServiceTest,ScannerWorkspaceRequestLedgerTest,ScannerWorkspaceRequestTest,ScannerWorkspaceResponseTest' test
git diff --check
```

### 3. JavaFX Legacy Scanner Pane Cleanup

The big Java-side migration target remains:

- `src/main/java/com/allinweb/ch/component/pane/ARScannedElementPane.java`

This file is large and behavior-sensitive. Split it into small safe commits.

Known cleanup areas:

- Old scanner WebSocket sends.
- Hardcoded scanner session destinations.
- Direct JavaFX control state mixed with scanner business logic.
- Browser/tab/DOM operations.
- TEST RUN / STOP and terminal-result behavior.
- Block creation and persistence glue.
- Appium/Web XML parsing and scan DTO construction.
- OCR, filesystem/CSV, support, and plugin management.

Do not start by deleting or rewriting the large execution engine. First extract small service/context boundaries and preserve behavior.

### 4. Small Backend Cleanup

After `SimpleWebSocketServer.java`, continue with smaller files that still contain scanner/session/searchTerms literals or old Java-side contract knowledge:

- `ARScannedElementScene.java`
- `GenFlowService.java`
- `PerformLists.java`
- `PerformListElements.java`
- `PerformPreLoad.java`
- `PerformCloseBrowser.java`
- `PluginContext.java`
- `ScannerWorkspacePayloads.java`
- `BotJobDetailsWorkspaceHost.java`

Keep these changes small and commit each medium slice.

### 5. Tests Cleanup

Some tests still hardcode scanner contract values:

- `scannerGrid`
- `preScannerGrid`
- `scannerTool`
- `scanner-element-pane`
- `searchTerms`

This is lower risk than runtime code. Update tests after runtime constants are stable.

Prioritize tests that validate new constants:

- `ScannerWorkspaceOperationsTest`
- `ScannerWorkspaceSessionsTest`
- frontend scanner contract/helper tests under `src/components/scanner`

### 6. End-To-End Validation

Required final validation path:

- Full frontend build.
- Focused frontend scanner tests.
- Focused backend Maven scanner tests.
- Backend compile/package when practical.
- Playwright/browser validation if the app can be launched cleanly.

Do not claim scanner pane/scene retirement until:

- Runtime routes are migrated or compatibility-routed.
- React owns the reachable scanner controls.
- Java side is reduced to services/backends.
- Zero-reference audit confirms `ARScannedElementPane` and `ARScannedElementScene` can be deleted.
- Desktop runtime parity is recorded.

## Already Completed In This Migration Pass

Frontend extraction/cleanup already pushed:

- scanner response matching helpers
- scanner action status helpers
- bootstrap/action response handling helpers
- transport message builder
- controller reset state helper
- bootstrap/action request eligibility helpers
- message cursor helper
- request id formatting helper
- controller failure statuses
- controller timing
- baseline statuses
- scanner operation constants
- scanner session constants
- scanner compatibility ids
- app routing and grid message reuse of scanner session constants

Backend extraction/cleanup already pushed:

- scanner request body parser
- scanner workspace operation ids
- scanner workspace session ids
- pre scan workspace session reuse
- scanner search terms operation constant
- browser service scanner constant reuse
- pre scan host scanner operation id reuse
- scanner contract constant test reuse

## Recent Verification

Frontend recent verification:

- `npm test -- --watchAll=false src/components/scanner`
  - previously passed: 19 suites / 70 tests
  - known warnings: React `act` deprecation, CRA Babel preset warning, worker force-exited warning
- `npm run build`
  - previously passed
  - known existing ESLint warnings in unrelated files

Backend recent verification:

- Focused Maven scanner/service tests previously passed.
- Known Maven warnings:
  - duplicate `javafx-maven-plugin`
  - deprecation/unchecked compile warnings

## Current Important Caution

The frontend file `GridItemScannMobile.tsx` is already modified. Read and preserve that diff before editing.

Do not change frontend CSS/design while doing the scanner contract cleanup unless a functional migration requires it.

## Bot Job instruction-row selection and safe deletion - 2026-08-11

- [x] Frontend `4319195` adds an isolated checkbox immediately after each Bot Job instruction Active toggle. The first row offers `First row only` or `All rows`; every other row remains independently adjustable.
- [x] A glowing red trash/count appears immediately after the owning block collapse control only while that block has selected rows. It offers exact selected-row deletion or structural parent/conditional/loop expansion, then disappears after the authoritative grid removes the rows.
- [x] Existing block-selection, Page Scanner, and Memory List checkboxes were not changed. Component-workspace rows do not expose the new Bot Job control.
- [x] Backend `4ffd41d3` preserves normalized variable definitions during instruction and block deletion, clears only deleted `producer_instruction_id` ownership, and clears both stale `parent_id` and `parent_block_id` on surviving repaired rows. Blocks remain independent from row deletion.
- [x] Focused verification passed: frontend instruction-delete planner 8/8; backend instruction/block deletion 4/4; `mvn -DskipTests compile` passed; production frontend build passed with existing repository warnings.
- [x] Deployment assets `af56bd24` are pushed. Frontend build, backend resources, and `target/classes` match across 58 files; entrypoints are `main.7984b953.js` (SHA-256 `467B345F77C4E1CE02527BE571E12A2293517637542291C86C8C0391173D04F4`) and `main.af1d2d62.css` (SHA-256 `45518EC2707BAD707F6C2FA620EB908953E11D10F468C6F019EC82FF93DA63CB`).
- [x] Automation catalog `7a9e1be5` records the new coverage; catalog generation executed no tests.
- [ ] No ARControlPanel JVM was running during deployment. Start ARWeb and visually verify row positioning, first-row selection scope, exact selected deletion, connected deletion, counter disappearance, and variable preservation before treating live acceptance as complete.
- [ ] The reported Clone Job `instruction.variable_id` schema defect and the requested per-instruction ExcelWrite redesign remain separate follow-up work and were not mixed into this checkpoint.

## Normalized Clone Job variable graph - 2026-08-11

- [x] Live read-only schema inspection confirmed `instruction.variable_id` is absent. Durable definitions are in `bot_job_variable_definition`; instruction bindings are in `instruction_variable_slot`.
- [x] Backend `354256c8` removes the retired column from Clone Job instruction insert/update, preserves and remaps both parent fields, clones durable definitions, remaps slots and typed command configuration, and remaps references.
- [x] Missing instruction/block/variable mappings now fail closed instead of silently omitting part of a clone. Existing Clone Job cleanup remains the failure boundary.
- [x] `mvn -DskipTests compile` passed with 564 main sources. Focused `CloneJobServiceTest` plus the normalized-schema clone regression passed 4/4.
- [x] Catalog `e5794caa` records 2,351 rows / 2,315 code cases; catalog generation ran no tests.
- [ ] No migration or frontend build was required. ARWeb was not running; restart it and perform one real Lloyds Bot Job 29 clone, then verify instruction/block/variable/slot/config/reference counts before and after.
- [ ] The per-instruction ExcelWrite redesign remains the next separate item.

## Execution V2 live deployment - 2026-08-11

- Source/runtime routing is already pushed through backend `420e9f31`, frontend `4e955d2`, resource
  deployment `d3d20877`, and documentation `5c2b74d8`.
- `playwright-runtime-ts` production TypeScript compilation passed. No tests were run in this
  operational checkpoint. No Java source changed and no Maven compilation was run.
- The committed 58-file frontend build was copied exactly into `target/classes/build`; live assets
  are `main.95512dab.js` (`7905DF8ED9B966AC55623EF53EF384F4EED272341A9D482774C241D14EC48F5A`)
  and `main.e1122a50.css` (`650C09C59BFD2E6D96154FAD92D821CC6CD1AEEBFDC056A11E18E341FAE86DE4`).
- One random 256-bit signing secret exists only in the environments of the current process pair; it
  was never printed or persisted. Node PID `13220` is READY on loopback port `60110`. BancaStato
  ARWeb PID `3892` runs `target/classes` on `63291/63292`; HTTP assets match and startup logs contain
  zero strict Java/SQLite/snapshot/V2 failures.
- No banking action was triggered. Next: user selects Lloyds Bot Job 29, chooses `V2 / Isolated`,
  and runs a safe C/I/O/GET/SET/REFRESH Integration acceptance followed by Stop/Finish. ExcelWrite
  remains intentionally unsupported in V2 until the React-memory manager/artifact boundary lands.

## Smoke runtime-selector placement - 2026-08-11

- Frontend `9723982` places the existing Java V1/V2 selector between the active Integration toggle
  and Refresh, removes its old lower-card rendering, and adds a compact selected-runtime badge above
  the Smoke workspace status. No execution state or WebSocket contract changed.
- `npm run build` passed with existing warnings; no tests ran. Deployment `5f608d7d` is exact across
  58 resource/target files. Entrypoints are `main.086bf45c.js`
  (`EA46677A5D413D9C80563FD8804EE61C473CF98FFEB8574603377B97357FCD68`) and
  `main.8678873d.css` (`FE2A31FF421D702CFE8D1DE6C9B6A4E45419B6D82793B6F71496CA9559AA361C`).
- ARWeb selected the new JS entrypoint, then exited externally before full live asset verification.
  It was not restarted because the requested scope was frontend build/deployment only. Node PID
  `13220` remains READY with zero reservations; visual approval and Lloyds V2 execution remain open.
- The user then explicitly requested restart. The orphaned Node process was replaced with a fresh
  matched-secret pair: Node PID `29768` is READY on `60110`; ARWeb PID `11200` is responsive on
  `65031/65032`. Live `main.086bf45c.js` / `main.8678873d.css` hashes match exactly and six new logs
  contain zero strict failures. No Smoke action was triggered by Codex.

## Inline Smoke runtime status - 2026-08-11

- Frontend `d5fc82c` places the runtime badge immediately before the workspace message in one
  responsive horizontal wrapper. Build passed without tests; deployment `b1659f3b` is exact across
  58 files. Live CSS is `main.d4eaaa1c.css` with SHA-256
  `CE6211A963688478DB8732E602E381501FA31AB4B229414105582BE62E868AF5`.
- Node PID `14772` is READY on `60110`; ARWeb PID `16304` is responsive on `56775/56776`; live CSS
  matches and nine current logs have zero strict failures. No Smoke action was triggered.

### Instruction-owned ExcelWrite redesign - 2026-08-11

- [x] Root cause confirmed: the typed Command Editor already persisted per-instruction ExcelWrite metadata, but execution still read the Block `export_file`, used an element parent as the destination column, and rebuilt columns per Block. The React Bot Job grid also still exposed the obsolete Block-level Excel panel.
- [x] Frontend `74a345d` / `319a2cd` moves file selection to each ExcelWrite instruction on the full Command Editor page. The isolated modal supports directory selection, `.xlsx`/`.csv`, delimiter, output key, destination column, and owner-scoped reuse/search of files already configured in the Bot Job. The Block-header Excel control and its GridItem socket state/rendering were removed. Component Editor modal behavior remains backward-compatible.
- [x] Backend `d593db48` adds exact Command Editor transport/binding/owner authorization for `excelWrite.bootstrap`, `excelWrite.chooseDirectory`, and `excelWrite.validateTarget`. These operations do not persist; the existing atomic Command Editor UPDATE remains the only typed-configuration write boundary.
- [x] Runtime now builds one immutable instruction-owned ExcelWrite plan at execution start, reads values only from each instruction's `READ` variable slot, writes the configured destination column, groups shared files safely, and serializes concurrent writers per canonical file. ExcelWrite no longer requires or produces a Web Element parent/variable. An instruction with no complete file target is explicitly bypassed.
- [x] CSV payload contents are no longer written to operational logs. Invalid absolute target/file contracts fail before typed persistence. Existing typed blank/cleared files remain authoritative and are not repopulated from a legacy Block setting.
- [x] Migration `2026-08-11__excelwrite_instruction_targets` is registered and tested. It backfills only ExcelWrite instructions lacking typed configuration, captures the legacy Block file/parent-column first, clears obsolete ExcelWrite parent fields, preserves all variable definitions and `instruction_variable_slot` bindings, and leaves Block `export_file` intact for rollback compatibility.
- [x] Focused JVM verification passed 17/17 after one compile error in the new checked-refusal helper was found and corrected. The successful run compiled 568 main and 333 test sources. Automation catalog `2f89c731` now records 2,353 rows / 2,317 code cases.
- [x] Frontend production build passed with existing repository warnings. Deployment mirror `03ce2179` is exact across 58 files / 19 images in both `src/main/resources/build` and `target/classes/build`: `main.86864372.js` (2,113,042 bytes, SHA-256 `155C9D6A7D42E61094416AC39EB615378D14B558B56FF9D1C153B8F2E422E585`) and `main.cd1e36ee.css` (518,299 bytes, SHA-256 `3D035EA10F65E28B30DE140870F7273EBD13E6B6D1EABB0BE6922D5C86667A`).
- [x] BancaStato-only activation completed while ARWeb was stopped. Exact pre-migration backup: `D:\Projects\ARWebBancaStato\ARWeb\Backup-CODEX-2026-08-11-excelwrite-instruction-targets\database.db`, 5,373,952 bytes, SHA-256 `5756E73B80ED5467E4107D3018EF3FC22C533155C848F632EE90B0D5D20B9147`. Exactly `2026-08-11__excelwrite_instruction_targets` ran in one transaction and migration history advanced 24 -> 25.
- [x] Post-state: `quick_check=ok`, `integrity_check=ok`, zero FK violations/sidecars, 25/25 ExcelWrite instructions have typed configuration, obsolete E parents are 0, and instruction/Block-export/variable-definition/variable-slot identity counts and sums are unchanged. The pre-existing typed placeholder for instruction 1729 remained unchanged as required.
- [ ] No application restart or live ExcelWrite file write was performed in this checkpoint. BancaStato Jobs 5 and 32 have READ bindings for their ExcelWrite rows; database-wide, 11 historical E rows lack a READ slot and 5 are active in other Bot Jobs, so those rows must be explicitly rebound or disabled before relying on their output. Live acceptance must configure two ExcelWrite instructions in different Blocks to the same file, verify both columns/file contents, verify an unconfigured instruction bypasses, and confirm variables survive instruction/Block deletion.

## Smoke Integration Continue Page policy - 2026-08-11

- [x] Root cause confirmed: React always sent `PRESERVE_ACTIVE`, but Java V1 ignored it and always navigated to the selected Bot Job URL before every Integration run.
- [x] Frontend `9723c6e` adds the green/red `ON/OFF Continue Page` control in the Integration toolbar. ON continues the active Bot Job's already-open Java V1 Playwright page; OFF reloads the selected Bot Job URL. V2 forces OFF because its isolated runtime cannot adopt the shared Java browser.
- [x] Backend `b2d13bde` makes `PRESERVE_ACTIVE` and `RELOAD_SELECTED` authoritative and echoes the accepted policy. Follow-up `b08e35d5` removes an incorrect origin comparison: a bank login flow may legitimately redirect to another authentication origin. Bot Job workspace-generation validation and strict browser retarget/close on Bot Job change remain the ownership boundary.
- [x] Focused verification passed 16/16 across the contract, Integration service, and Playwright browser lifecycle suites. The lifecycle suite explicitly verifies that an open redirected page is reused without navigation or reload. The production frontend build passed. No broad test suite or package build ran.
- [x] Deployment `50ee3404` is pushed. Frontend build, backend resources, and `target/classes` match across 58 files. Entrypoints are `main.df172fe3.js` (SHA-256 `8A2EC92FB6DA750EE63D199ED2A42944F4181F5F0CF5B9CE6F37ABE2D1363FD8`) and `main.621bd10a.css` (SHA-256 `998AF6673C6D5C54FE15EEDB4DF0220AA4CF16210ACFB05F886B11766FA74207`).
- [x] Corrected matched runtime pair restarted: Node PID `20252` is READY on `60110`; ARWeb PID `16636` is responsive on `65289/65290`. Live JS is `main.df172fe3.js`, and six new logs contain zero strict Java/SQLite/snapshot errors.
- [ ] Live user gate: open Lloyds Job 29, reach the user/password page, finish or stop the run while keeping ARWeb and its browser open, then run again with Continue Page ON. The redirected authentication page must remain unchanged. Repeat once with OFF and confirm it reloads the selected Lloyds URL.

## Unsaved Excel memory in test execution - 2026-08-11

- [x] Root cause confirmed: `ExcelDataWorkspaceService` rejected dirty REAL memory in both Bot Job TEST RUN and Smoke Integration, although manual row tests already consumed edited memory and Save was an explicit separate action.
- [x] Backend `252c8ab0` makes Smoke, Integration, and TEST RUN consume the current REAL or SYNTHETIC in-memory dataset. Integration freezes a deep copy, including unsaved edits, so later edits or Save cannot change an active run.
- [x] Only Batch `Launch` now checks dirty REAL memory and returns `Save to Excel before Launch`. Synthetic memory never has that disk-save prerequisite. The existing Save action remains available after a successful test to persist proven REAL edits.
- [x] Focused verification passed 15/15 across Excel policy and Smoke Integration contract/service suites. Java compiled 577 sources. No frontend source changed, so no npm build or asset deployment was required.
- [x] Catalog `60574009` records 2,372 rows / 2,336 code cases; generation executed no tests. ARWeb PID `24584` runs on `54622/54623`; Node PID `5708` is READY on `60110`; six fresh logs have zero strict failures.
- [ ] Live user gate: edit a REAL memory cell without saving, run Integration and verify the edited value is used, then Save to Excel afterward. Repeat with SYNTHETIC. Confirm Batch Launch alone refuses unsaved REAL memory.

## Pages Open hidden-window focus recovery - 2026-08-11

- [x] Live evidence confirmed the Bot Job Details control WebSocket remained authoritative while its Chromium top-level window was hidden (`Visible=False`). Pages Open delivered the focus request, but native lookup filtered out all hidden HWNDs, so the existing restore routine could never run.
- [x] Backend `a94ab42f` keeps the one-use WebSocket-issued title token as exact identity, includes hidden top-level windows in token lookup, and then reuses the existing `ShowWindow`/restore/foreground sequence. Session, close, launch, and owner authorization were not broadened.
- [x] Java compiled 577 main / 339 test sources; focused `DesktopWindowFocusServiceTest` passed 1/1. Catalog `aa82166f` records 2,373 rows / 2,337 code cases; generation ran no tests.
- [x] ARWeb PID `16000` runs on `61964/61965`; Node PID `26056` is READY on `60110`; six startup logs have zero strict failures.
- [ ] Live user gate: open/retarget Job 32, then click Pages Open -> Bot Job Details. The hidden exact window must become visible and foreground. Repeat after switching back to Job 29.

## Java V1 Integration command completion - 2026-08-12

- [x] `JAVA_V1 / SHARED` remains the new Java Playwright Integration path. It does not call the
  legacy Selenium `executeJob()` engine. `PRESERVE_ACTIVE` continues the currently owned shared
  Playwright page; `RELOAD_SELECTED` navigates to the selected Bot Job endpoint first.
- [x] Frontend `2231211` makes PAUSE block the React-owned run until explicit Continue or Stop.
  Frontend `b9a097b` executes EXCEL GOTO against the frozen Integration dataset row count, and
  `9b21244` terminates orchestration after an authored Q/QUIT closes the browser.
- [x] Backend `29300e16` exposes the frozen dataset row count. Backend `1b7304b2` adds physical
  Java Playwright execution for BACK, NEXT_ENTER, bounded SWIPE_UP/SWIPE_DOWN, viewport PNG
  screenshot P, and Q/QUIT. Existing C/I/O, GET/SET, REFRESH variants, locator healing, owner
  fencing, and exact run correlation remain authoritative.
- [x] CK, CSV CHECK, PDF CHECK, IF/ELSEIF/ELSE/ENDIF, LOOP, GOTO, EXCEL GOTO, H/WAIT, and PAUSE
  remain React-owned logical commands. They are not silently re-executed by Java.
- [x] Focused Java verification passed 15/15 after correcting the existing StartResponse fixture.
  Java compiled 577 main and 339 test sources. The frontend production build passed with existing
  repository warnings after two incomplete Excel Goto test-fixture shapes were detected and fixed.
- [x] Deployment `d63d29e8` mirrors 58 exact files into resources and `target/classes`, with zero
  hash mismatches. Entrypoints are `main.c166cabf.js`
  (`B2F81EB01C49ABDCA58C1A6D4F772A9E1AAE6FC5C5AB7CE8BCCA49F1315BE3C0`) and
  `main.2f02cebb.css` (`863E0A55F811307B9AE6F729BFCC3916137646B1087BBA2F67BCD97DB80A7803`).
  Catalog `5a8c5c7a` records 2,375 rows / 2,339 code cases.
- [ ] ExcelWrite E remains intentionally fail-closed in Smoke Integration until the separate
  React-memory ExcelWriter Manager and narrow backend artifact-flush DTO are implemented. The P
  command validates a PNG in memory; durable screenshot naming/storage is also a separate artifact
  contract. No app restart or live banking action was performed in this checkpoint.

## ExcelWriter Manager Integration checkpoint - 2026-08-12

- React now owns ExcelWrite arrival, first-arrival file/column/row ordering, editable run memory,
  formula-safe deterministic CSV construction, lazy-loaded XLSX construction, SHA-256, and the
  End-of-Block / End-of-Execution flush policy. The policy is frozen during an active run. Source
  commits are frontend `9e4e7e4` plus architecture correction `72026cb`.
- ExcelWrite is logical-only in Java V1 Smoke Integration; it never invokes legacy per-row Java disk
  I/O. The correlated backend reauthorizes the exact run/workspace, revalidates frozen instruction
  IDs plus current typed READ/file/column configuration, verifies Base64 length and SHA-256, and
  atomically writes only React-finalized bytes (`e3ce0417`, corrected boundary `f6a76d62`).
- XLSX targets write the CSV companion first. End-of-Block/final flush failures stop and clean the
  run instead of leaving it stuck. React retains FAILED/dirty state and does not claim full success.
- Focused frontend verification passed 11/11 initially and the final artifact matrix passed 10/10.
  Focused Java verification passed 16/16 and compiled 578 main / 340 test sources. The production
  frontend build passed with existing repository-wide warnings. No Playwright action ran.
- Deployment `9b57892c` mirrors 61 exact files into resources and `target/classes`. Entrypoints are
  `main.1f0e624c.js` (2,136,884 bytes; SHA-256
  `CF9383BF7F68265D6A1DA7DA87F765CB531BCB406D47C3727C9518A456B85117`) and
  `main.5b308b0c.css` (530,113 bytes; SHA-256
  `8B79F93F682289C77DF7B97425D5E9EC65351C4EBEC5F2CE848E89FADD52DE48`). ExcelJS is lazy-loaded.
  Catalog `195eab6a` records 2,382 rows / 2,346 code cases.
- No migration, package/image, application restart, or live file write was performed. Remaining:
  live two-Block/shared-target acceptance, post-stop Save Partial/Discard, and a truly detached
  floating Manager page rather than the current isolated in-page dialog.

### ExcelWriter PAUSE / Stop / Close Browser save boundaries - 2026-08-12

- Frontend `32cfc6b` supersedes the earlier post-stop Save Partial expectation. An authored PAUSE
  now flushes all dirty ExcelWriter artifacts before showing Continue/Stop; explicit Stop flushes
  before terminal cleanup; Q/QUIT flushes before physically closing Playwright. These rules affect
  ExcelWriter only and do not read, save, or change Excel Data memory.
- All flush requests share one serialized queue. Stop always attempts terminal cleanup even when a
  write fails and reports both outcomes. A PAUSE/close-boundary failure stops fail-closed instead of
  continuing with unsaved output. A second Stop from PAUSE retries only files made dirty there.
- VOID READ variables create no file/tab. Produced VALUE states create output even when the exact
  value is empty or contains only spaces; those distinctions are regression-tested.
- Focused frontend verification passed 9/9. Production build passed with established warnings. Exact
  61-file deployment is `865abb79`; current JS is `main.4399de1e.js` (2,137,441 bytes; SHA-256
  `97A0846526979D6A2F2F5B90CE22C320898CAC3B1FF5FA63E21C3D51F1AA509C`) and CSS remains
  `main.5b308b0c.css`. Catalog `6e790afd` records 2,386 rows / 2,350 code cases.
- No Java source changed, so Maven was not rerun. No app restart or live file/browser action ran.

## Runtime readiness and detached ExcelWriter Manager - 2026-08-12

- Backend `b871b71a` and frontend `bc6ebc7` fix the Smoke Integration startup race. Java now
  opens/focuses Runtime Variables and waits for an exact owner/binding React-ready acknowledgement
  before it acquires or navigates Playwright. Excel Data remains separate: its visibility request
  is still non-blocking, while REAL/SYNTHETIC execution data continues to be frozen authoritatively
  by Integration and was not corrupted by the Runtime Variables race.
- Frontend `30942d6` promotes ExcelWriter Manager from the Smoke Test DOM into the independent
  `excelWriterManager` detached page. Smoke Test remains the sole owner of ExcelWriter run memory;
  an owner-scoped BroadcastChannel projects state and returns edit/policy/save commands. The cyan
  dirty-file glow, tabs, grid, and manual Save remain. Java performs only exact-owner launch/focus
  authorization in backend `a3fb24da`; it does not own cells or workbook construction.
- Focused frontend checks passed 2/2. `VariablesWorkspaceServiceTest` passed 27/27; Maven compiled
  578 main and 340 test sources with only the two established warnings. The production frontend
  build passed with established repository warnings.
- Deployment `2ba49070` mirrors 61 exact files into resources and `target/classes`, with zero
  missing, extra, or hash-mismatched files. Entrypoints are `main.e060044e.js` (SHA-256
  `A3AF24B79D8DD437FC9D12188F9B40B60638F177E62625609637058D7AFAF806`) and
  `main.b8d60cfe.css` (SHA-256
  `D0B552AA8B44D702503174DF0D025C0276C5270E9B541D4B4B202FF0DA8382AC`). Catalog `2c3dd644`
  records 2,390 rows / 2,354 code cases.
- No migration, package/image, application restart, browser action, or live workbook write was
  performed. Remaining live gate: restart from the rebuilt classes, run one Integration containing
  ExcelWrite, verify Runtime Variables is visible before Playwright, and verify the independent
  Manager updates/edit/saves without blocking Smoke Test or changing Excel Data.

## Three-page Smoke execution readiness - 2026-08-12

- [x] The prerequisite applies to both local Smoke simulation and Smoke Integration. Before either
  execution plan advances, React sends one correlated owner/generation-bound prepare request and
  awaits Runtime Variables, Excel Data, and ExcelWriter Manager.
- [x] Backend `9edfdfd3` opens all three pages and waits for exact rendered-owner acknowledgements;
  `847095f9` runs that wait on a bounded worker so the Smoke WebSocket remains free to relay
  ExcelWriter state and exact terminal delivery stays on the original authoritative transport.
  Integration repeats the same authoritative gate before acquiring or navigating Playwright.
  Wrong owner, disconnect, load failure, or timeout fails closed before a browser action.
- [x] Frontend `73a45f4` makes the local Run handler await that gate, adds generation-safe message
  consumption, and replaces the ExcelWriter BroadcastChannel with its independent WebSocket relay.
  Smoke Test remains the sole owner of ExcelWriter cells/workbook logic; Java only validates and
  relays opaque owner-bound state/commands. ExcelWriter acknowledges readiness only after its exact
  state has arrived and rendered. Excel Data acknowledges its exact dataset generation.
- [x] The three pages retain their normal top-right states. Excel Data and ExcelWriter add explicit
  red failure treatment; the Smoke Test top status reports opening, ready, failure, and timeout.
- [x] Verification passed: frontend exact-owner bridge 1/1; backend Variables/Integration suites
  32/32; catalog suite 2/2; `mvn -DskipTests compile` compiled 578 sources; production frontend build
  passed with established repository warnings.
- [x] Deployment `0c898756` mirrors 61 exact files and 19 images into resources and
  `target/classes`. Entrypoints are `main.19002a92.js` (2,147,575 bytes, SHA-256
  `057F77B79DAAC40920652759078466EA957690B04540DCEFE3B261ED931C5C17`) and
  `main.87de7edc.css` (532,294 bytes, SHA-256
  `6BAA0FCFC311EEC57A8930B8AD6353AD876D28B9A39A5E347B45667B1D9F1EEE`). Catalog `780e746a`
  records 2,390 rows / 2,354 code cases.
- [ ] No application restart or live Smoke/Integration/Playwright action was performed. From
  IntelliJ, run one Smoke simulation and one Java V1 Integration and verify the three pages are
  visible/ready before the first step and ExcelWriter updates in real time without blocking the
  Smoke page or altering Excel Data.

## Integration runtime-write scheduler stabilization - 2026-08-13

- [x] Root cause confirmed from the live Bot Job 5 sequence: GET published a Runtime Variables
  snapshot, which recreated the snapshot-dependent `updateValue` callback while the current
  Integration step was settling. The React scheduler effect restarted the same instruction, the
  duplicate was refused as already active, and the later ExcelWrite and PAUSE rows were skipped.
- [x] Frontend `ed214dc` keeps the runtime update callback stable across ordinary memory revisions
  while reading the newest owner binding, runtime revision, and entry revision through a ref.
  Connection/session changes remain callback dependencies and still retire stale execution state.
- [x] Focused verification passed 5/5 across runtime-memory transport and Integration GET result
  projection. The regression proves callback identity remains stable after a snapshot revision and
  the subsequent write carries the newest base and entry revisions.
- [x] A clean production build passed with established repository warnings. Deployment `c00a74d8`
  mirrors 61 exact files into resources and `target/classes`; stale `main.8ef4bf5c.js` artifacts are
  absent. Current assets are `main.a26bedce.js` (SHA-256
  `443A3563D4BC15D63B15FA51B55FC6F5CC16639BA88CDCBD8500E7DB9289DBAE`) and
  `main.87de7edc.css` (SHA-256
  `6BAA0FCFC311EEC57A8930B8AD6353AD876D28B9A39A5E347B45667B1D9F1EEE`).
- [x] ARWeb PID `9520` runs the rebuilt `target/classes` with the exact BancaStato config on
  `127.0.0.1:57269/57270`. Root/JS/CSS return HTTP 200 and live hashes match the deployed files;
  seven fresh runtime files contain zero strict Java, SQLite, snapshot, or duplicate-step matches.
- [ ] Live user gate: rerun Bot Job 5 Block 1 and verify GET advances once into the configured
  ExcelWrite row, the ExcelWriter Manager receives the file/column/value, and PAUSE opens exactly
  once. Instruction 1775 remains independently unconfigured and is expected to report its own
  ExcelWrite configuration failure until configured or disabled.

## Smoke Stop to Page Scanner ownership handoff - 2026-08-13

- [x] Live evidence showed Smoke Integration Stop and its final step settling together while Page
  Scanner could still observe the shared execution lease. The old coordinator rejected Scanner
  immediately with a TEST RUN-specific message even when Smoke Integration was the owner.
- [x] Backend `abb1b194` gives stopping Java V1 Integration an explicit release state. Page Scanner
  waits on its own worker for up to 15 seconds for that exact lease handoff; it never runs
  concurrently with the finishing Playwright action and still fails closed if cleanup does not
  finish. Stop, disconnect, binding replacement, and shutdown all signal release.
- [x] Backend `4773e1b6` makes separately correlated duplicate Stop requests join the serialized
  cleanup and receive `STOPPED`; it no longer reports "termination is already in progress", while
  the browser lease still closes exactly once.
- [x] Bot Job close/retarget now treats active, starting, refreshing, or terminating Smoke
  Integration as busy. After cleanup, the existing single shared Playwright browser is strictly
  navigated to the newly selected Bot Job; no parallel browser thread is introduced.
- [x] Focused verification passed 16/16 across the ownership coordinator, Smoke Integration
  lifecycle, and workspace close gate. Maven compiled 578 main and 341 test sources.
- [x] ARWeb restarted from the final `target/classes` as PID `8284`, listening on
  `127.0.0.1:56221/56222`;
  HTTP returned 200, SQLite connected, and Main Dashboard loaded Bot Jobs.
- [ ] Live gate: run Java V1 Integration, press Stop while a step is settling, immediately request
  Page Scanner, and then switch Bot Jobs. Scanner must acquire after cleanup, and owner switching
  must remain blocked until cleanup before retargeting the same browser.

## Concurrent isolated Smoke runtime and five-browser proof - 2026-08-13

- [x] Backend `d4862439` replaces the process-global single Smoke run with an exact-run registry.
  Java V1 remains exclusive because it owns the shared Java Playwright page; V2 admits at most five
  isolated runs and retains exact owner, run ID, runtime authority, response transport, pending-step,
  and terminal state per run.
- [x] Backend `618ec9ec` serializes Step, ExcelWrite, Stop, and Finish within each run while allowing
  different V2 runs to execute concurrently. Replayed requests retain their accepted Home Banking
  identity instead of inferring an owner from another run sharing the same transport.
- [x] Node checkpoint `ae847e43` adds a bounded local-only five-browser acceptance runner using the
  production `PlaywrightWorkerPool` and `PlaywrightBrowserFactory`. It does not call a bank endpoint
  or execute a banking instruction.
- [x] `npm run build` passed for the TypeScript Playwright runtime. `mvn -DskipTests compile` passed
  with 578 main sources and only the two established warnings. No automated test suite ran, as
  requested.
- [x] Live local acceptance created five simultaneous headed Chrome instances. All five reached
  `READY` with distinct run/browser/context/page IDs, then the runner released every exact session
  and exited with code 0 after its bounded display interval.
- [x] Source commits `d4862439`, `ae847e43`, and `618ec9ec` are pushed to
  `refactor/perform-actions-decomposition`.
- [ ] This does not complete Main Dashboard multi-Bot-Job execution. `Start Selected` remains
  intentionally disabled because the manager does not yet own one frozen React execution program,
  REAL/SYNTHETIC dataset, Runtime Variables state, and ExcelWriter state machine per selected Bot
  Job. The next phase is that React multi-run control plane; it must use these isolated V2 runs and
  must not degrade into a browser-only launcher.
- [ ] **CRITICAL COMPLETION BLOCKER — Stop during locator resolution:** the user pressed Stop while
  Integration was still trying to find a Web Element on the current page after locator resolution
  had failed/continued waiting. The run did not stop and release Playwright completely. After the
  multi-Playwright `Run` controller is implemented, reproduce this exact interleaving and fix it at
  the authoritative cancellation boundary. Acceptance requires the active locator wait/action to
  observe cancellation, one terminal Stop result, exact browser/run release, no lingering ownership
  or worker activity, usable Smoke controls, and immediate availability for Page Scanner or a new
  run. Do not mark multi-run complete before this gate passes.

## Multi-run owner-scoped preflight - 2026-08-13

- [x] Backend `f39baa6c` adds a correlated Main Dashboard preflight for one immutable selection of
  one to five Bot Jobs. The exact registered Main Dashboard transport is required before database
  work; each Home Banking/Bot Job pair is revalidated by loading its complete owner-scoped frozen
  plan. No browser is started.
- [x] Preflight reports plan revision, Block/instruction counts, endpoint validity, V2 configuration,
  and unsupported active command families per row. ExcelWrite is classified as React-owned
  logical work; the Node runtime never performs workbook construction.
- [x] Frontend `bb28f3a` adds isolated preflight state/correlation, per-row REAL/SYNTHETIC selection,
  readiness diagnostics, request timeout, message-generation recovery, and responsive manager
  presentation. Changing a data mode invalidates prior readiness.
- [x] Java compilation passed with 578 main sources after one missing-import failure was corrected.
  The frontend production build passed after one TypeScript inference failure was corrected; only
  established repository warnings remain. No automated tests ran.
- [x] Deployment `bd1cb821` mirrors 61 exact files into resources and `target/classes`. Current
  entrypoints are `main.ee2cef00.js` (2,155,575 bytes, SHA-256
  `CC77341C93AC7AE757A16E34528D63F249BD8587118ACFBB635B8FB917DA7C43`) and
  `main.3940a42a.css` (533,092 bytes, SHA-256
  `86BBF9BCEE171FBA762C31D2F375825181BB92F0F08CC2CC0F771E1603E90C66`).
- [ ] `Start Selected` remains intentionally disabled. Preflight proves plan/runtime command
  readiness only; the next checkpoint must create one independent React Run controller, frozen
  data snapshot, Runtime Variables store, ExcelWriter reducer, and terminal state per selected row.
  No application restart or live UI preflight was performed.

## Main Dashboard selected Bot Job React controllers - 2026-08-13

- Backend `b9a88131` freezes owner-scoped plans, REAL/SYNTHETIC datasets, relationship graphs, and
  Runtime Variables without depending on the singleton Smoke/Excel Data bindings. Backend
  `b50dbd20` routes prepared Dashboard starts through the existing exact-run V2 registry.
- Backend `06ed6479` adds plan-revision revalidation at batch issuance and bounded dataset-transfer
  limits. `mvn -DskipTests compile` passed with 581 sources and the two established warnings.
- Frontend `75351fb` enables `Start Selected` only after correlated complete preflight. Each row owns
  one React program, data mode, runtime map, ExcelWriter reducer/flush lifecycle, V2 run ID, logs,
  progress, Stop, and terminal status. `Stop Selected` fans out to active rows; close/Escape are
  blocked until every child settles.
- The production frontend build passed with existing warnings. Deployment `a18cf8cd` mirrors 61
  exact files into resources and `target/classes`; current entrypoints are `main.0652482b.js`
  (`01D7EFFD0B4DC7B14FEEF0A2DB4ABC50DD92547E00AA5D2E4096C7B0E6900A96`) and
  `main.25f852bf.css` (`827E3E142EE6C823A4B2E343FA0AEEDE49A4A0C7B04AD8ADE867334213571912`).
- Backend/Node `1777fb4d` fixes the Stop-during-locator lock boundary. Java now requests an immediate
  exact-run Node interruption before waiting for the current step monitor; Node closes only that
  browser, returns `ACTION_CANCELLED`, suppresses the late action result, and settles STOPPED.
  Focused Java tests passed 11/11 and the focused Node cancellation/unknown-outcome tests passed 2/2.
- Node `2672b85f` adds one bounded 10-second render-resolution deadline per V2 physical instruction.
  The complete XPath/CSS/registry/name priority chain is retried every 150 ms; a visible, uniquely
  validated target proceeds immediately, ambiguity remains fail-closed, and expiry returns the
  existing `TARGET_NOT_FOUND`. The Playwright-page wait is interrupted by exact-run Stop. All 35
  Node runtime tests passed.
- No service restart, database mutation, migration, package, image, or live Bot Job execution
  occurred. Live acceptance must still reproduce Stop during a real unresolved locator and prove
  exact release plus immediate Page Scanner/new-run availability.

## V2 runtime locator recovery review - implementation checkpoint

- This supersedes the earlier post-scan/background-reconciliation interpretation. Recovery is
  triggered only when one active Smoke Test Integration V2 instruction exhausts its bounded render
  wait and locator priority chain, or remains ambiguous. Page Scanner completion is unrelated.
- Node performs no physical action, returns at most 25 read-only candidates, and keeps physical
  attempts at zero. Exact owner/Bot Job is authoritative; exact-page registry rows remain the normal
  automatic path, while same-Bot-Job/same-name rows from another page are review-only evidence.
- The affected React run waits in an ExcelWriter-colored comparison modal. Other isolated Bot Job
  runs continue. Columns include saved canonical/client/OCR names, old/new XPath/custom XPath/CSS,
  stable attributes, page identities, tag/type/role/action, confidence/reasons/warnings, and explicit
  green-check/red-X/gray-dash comparisons for XPath/CSS/attributes/frame/shadow.
- Explicit actions are Use Once, Use and Save Locator, Cancel Recovery, and Stop Execution. React
  sends only the opaque candidate ID. Java revalidates the exact run/instruction and Node performs a
  new page-checked physical action. Save updates only the exact existing owner/Bot Job/page/element
  registry row after action success; it never rewrites an immutable capture or instruction graph.
- Node tests, focused Java tests, frontend contract tests, Java compilation, and frontend production
  build pass. Live V2 execution/visual acceptance and deployed asset mirroring remain separate gates.

## Stop preserves the V2 browser - 2026-08-14

- [x] Backend/Node commit `37b3910c` supersedes the earlier V2 Stop behavior that closed the exact
  browser. Smoke Test Stop now interrupts the pending locator/render wait, settles the run, and
  parks the still-open browser under the exact organization/Home Banking/Bot Job owner.
- [x] A later run for that exact owner adopts the same browser/context/page instance. Browsers are
  never shared across owners. Parked-browser admission remains bounded by the configured active-run
  capacity; application/runtime shutdown remains the final process-cleanup boundary.
- [x] The authored `Q` / Close Browser instruction is now supported by V2 and calls a dedicated
  token-authorized `/close-browser` route. Ordinary Stop and Finish use the preserving `/stop`
  lifecycle and report that the page remains open.
- [x] The frontend contained no browser-close call in its Stop path; it displayed the terminal text
  returned by Java. Therefore no React asset change or frontend deployment was required.
- [x] All 39 Node runtime tests passed. Focused Java lifecycle verification passed 22/22, and the
  final explicit-close HTTP/executor subset passed 8/8. Maven compiled 581 main and 341 test sources.
- [x] Commit `37b3910c` is pushed to `refactor/perform-actions-decomposition`.
- [ ] Live acceptance remains: start V2, stop during an unresolved locator, confirm the browser
  stays open, start the same Bot Job again and confirm the browser instance is reused, then execute
  an authored Close Browser command and confirm only that command closes it.

## Synchronized locator-recovery verification control - 2026-08-14

- [x] Frontend commits `6899293` / `8fdeca0` add one transient green/red power control shared by
  the Bot Job execution-flow header and the open Locator Recovery modal. It defaults ON. Turning it
  OFF while a recovery is open sends the existing correlated BYPASS decision; later unresolved V2
  instructions bypass recovery without displaying the modal.
- [x] Backend `2524ed7e` makes `recoveryVerificationEnabled` part of the exact Step request,
  response, and sequence replay identity. Missing legacy request fields default ON. OFF cancels the
  exact Node recovery hold and records the unresolved instruction as `SKIPPED` with
  `RECOVERY_BYPASSED`; it does not perform a physical action.
- [x] Verification passed: focused Java 17/17, focused React 16/16, Java compile 581 main / 341 test
  sources, frontend production build, and `git diff --check`. Existing React `act` deprecation and
  repository build/lint warnings remain visible but are unrelated.
- [x] Frontend source is pushed to `VERSION-4.6`; backend source is pushed in `2524ed7e`.
  Deployment asset commit `084fb900` mirrors 61 exact files into resources and `target/classes`.
  Entrypoints are `main.a4addf99.js` (2,174,408 bytes; SHA-256
  `C0B26AD611CE4D5AA081D8C42D9167942AAE410325AE6BE8987B074AC42857A8`) and
  `main.368be5c5.css` (540,087 bytes; SHA-256
  `AF02DB5872770BA727F2232420E0DA472D62439EC9EBAA3B9A7E909EE39E3D7F`).
- [ ] No application/runtime restart or live UI/V2 recovery was performed. Live acceptance must
  prove ON pauses with both nonempty and zero-candidate recovery, OFF bypasses and advances, the
  two power controls remain synchronized, and Stop preserves/reuses the browser until an authored
  Close Browser instruction closes it.

## Java V1 Locator Recovery modal support - 2026-08-14

- [x] Backend `a20fcf2c` extends the existing Locator Recovery WebSocket/modal contract to Java V1
  Shared. V1 `TARGET_NOT_FOUND` and `AMBIGUOUS_TARGET` outcomes now retain an exact run,
  instruction, owner, and page-bound recovery set instead of returning only a terminal diagnostic.
- [x] The existing React modal is reused unchanged. It supports zero-candidate Bypass, Use Once,
  Use and Save Locator, Cancel Recovery, Stop Execution, and the synchronized recovery-verification
  power control for both V1 and V2.
- [x] V1 candidate discovery is bounded to 100 live main-document elements, 100 unique registry
  rows, and 25 displayed comparisons. React receives opaque candidate IDs and cannot submit a
  locator. Frame and Shadow DOM recovery remains fail-closed with a zero-candidate modal because
  Java V1 cannot safely encode those boundaries in this recovery action.
- [x] A selected action revalidates the exact current page and reruns only the selected generated
  XPath/CSS through the serialized Java Playwright executor. `Use and Save` updates only the exact
  owner/Bot Job/page/scanned-element row and only after the physical action succeeds. GET recovery
  also restores the run-local/durable Runtime Variable result.
- [x] Backend admission now refuses a later Step with `RECOVERY_PENDING` until the user resolves or
  bypasses the paused instruction. Pending V1 state is cleared on Bypass, Cancel, Stop, Finish,
  disconnect, binding retirement, or service shutdown.
- [x] Operational logs record preparation, candidate counts, decisions, page changes, action
  diagnostics, save outcomes, and cleanup using run/instruction/registry IDs only. They do not log
  page URLs, locator strings, banking text, or input/output values.
- [x] `mvn -DskipTests compile` passed with 582 main sources and the two established warnings.
  `git diff --check` passed. No test was created or run by explicit user instruction. No frontend
  source/build asset, database migration, package/image, service restart, or live action occurred.
- [ ] Live acceptance remains: trigger Java V1 with one missing/ambiguous main-document element,
  prove nonempty and zero-candidate modal behavior, exercise Bypass/Use Once/Use and Save, verify
  the saved locator row only after success, and inspect the new safe operational log entries.

## Smoke Integration trace and supervised V2 runtime - 2026-08-14

- [x] Backend `16df617c` adds a dedicated rolling `ar_web_smoke_execution.log` under configured
  `path_log`. It correlates V1/V2 start, plan/data freeze, steps, recovery, ExcelWrite, Stop/Finish,
  cleanup, Java-to-Node HTTP, and Node request completion without credentials, grants, values,
  URLs, banking text, or locator strings. Optional Node JSONL is `ar_web_execution_v2.log`.
- [x] Backend `f86c1688` adds a Java-owned local Node supervisor. When IntelliJ provides no grant
  secret, Java generates one process-private 32-byte secret and passes it only to the child Node
  process. Start waits for loopback `/health/ready`; Stop refuses active/pending V2 runs and cannot
  terminate an externally managed runtime.
- [x] Frontend `a927e93` adds the requested red/green `V2 Runtime` Start/Stop control between
  Continue Page and Refresh. Requests and responses are correlated to the exact Smoke transport,
  binding/workspace generation, Home Banking, Bot Job, and graph revision.
- [x] Java compilation passed with 583 sources. Node TypeScript and production React builds passed;
  no tests were created or run per user direction. Deployment `c9be76f0` mirrors 61 exact files.
  Entrypoints are `main.4c1147fd.js` (2,178,325 bytes; SHA-256
  `B5A84C13372032DA4C03AA35E76C03D3C41A133861275A94258B3210DBEA9D1A`) and
  `main.dce607b4.css` (540,972 bytes; SHA-256
  `5273A8FE0F06201126A942089ED34B6ED12A4259615DD9246416FD777230ABDD`).
- [ ] No Java/Node service restart or live Start/Stop/V2 run was performed. Restart IntelliJ ARWeb,
  open Smoke Test, start V2 from the new control, confirm READY, run one V2 Integration, then prove
  runtime Stop is refused during the run and succeeds after terminal cleanup.
- [x] Post-checkpoint verification exposed and fixed a static initialization-order defect in the
  supervisor logger (`df6de97d`). The first focused Java run failed 9 service cases at class startup;
  after the fix, the same complete focused matrix passed 37/37.
- [x] The full Node V2 runtime suite passed 39/39. The headed five-browser acceptance reached READY
  for five distinct run/browser/context/page identities and exited cleanly with no matching orphan
  demo process. These were isolated synthetic/mock runs, not banking-site actions.

## Isolated V2 browser launch parity - 2026-08-14

- [x] Backend/Node commit `32cd748b` makes every V2 Chromium launch request
  `--start-maximized` and creates its isolated context with `viewport: null`. V2 remains one
  Browser/Context/Page per run and never attaches to the Java V1 shared browser.
- [x] Database-owned Bot Job browser options using the established `argument:` / `arg:` syntax now
  cross the Java-to-Node start contract. Both sides enforce the same bounded contract: at most 32
  arguments, 3-512 characters each, a required `--` prefix, and no control characters. Client code
  cannot author this list.
- [x] V2 contexts now allow normal service-worker behavior. Isolation is still provided by the
  nonpersistent per-run context, so workers and storage are not shared across runs.
- [x] Privacy-safe Node diagnostics record the actual viewport/screen/DPR and hashed page identity
  after navigation, then actual current page identity plus context/page IDs and registry/live
  candidate counts after each settled physical action. Retained same-owner browsers rebind logging
  to the replacement run ID.
- [x] Complete Node runtime verification passed 40/40. Focused Java HTTP/coordinator verification
  passed 11/11 and compiled 583 main plus 341 test sources. `git diff --check` passed.
- [x] Commit `32cd748b` is pushed to `refactor/perform-actions-decomposition`.
- [ ] No frontend source/build, deployment copy, database change, service restart, or live banking
  action was performed. Restart ARWeb/its supervised V2 runtime before live acceptance, then compare
  V1/V2 viewport, page identity, candidate counts, and locator behavior for the same Bot Job.

## V2 parity trace expansion - 2026-08-14

- [x] Commit `630e1a0f` adds privacy-safe browser lifecycle events for launch request/process,
  context/page creation, navigation/readiness, refresh, current page identity, physical action
  start/result/failure, interruption, retained-run rebinding, explicit close, cleanup failure, and
  unexpected closure.
- [x] Action results record run/browser/context/page IDs, hashed current page identity, instruction
  and sequence, action/stage/code, registry/live/recovery candidate counts, physical-attempt count,
  frame/shadow/tag/action validation flags, and duration. No URL, locator, browser argument, value,
  grant, token, banking text, or credential is logged.
- [x] Java's dedicated Smoke trace now brackets reserve/start/readiness, action dispatch/result,
  recovery decisions and locator-save outcome, refresh, interrupt, preserving Stop/release, and
  authored browser close. Reservation failures are inside the correlated start-failure boundary.
- [x] Log-sink failures are explicitly best-effort and cannot change runtime behavior. Node passed
  42/42 tests; focused Java passed 11/11 and compiled 583 main plus 341 test sources; diff checks
  passed. `630e1a0f` is pushed to `refactor/perform-actions-decomposition`.
- [ ] The running application was not restarted. Live evidence from a new V2 run remains required
  in `ar_web_smoke_execution.log` and `ar_web_execution_v2.log` after restart.

## Owner-bound Smoke Integration emergency STOP - 2026-08-15

- [x] Frontend `19b8bb8` keeps STOP enabled before, during, and after Integration. It sends an
  owner/binding/workspace/graph-correlated `smokeTest.integration.forceStop` even when START has
  not returned a run ID. A late START response cannot restore a cancelled run.
- [x] Backend `d99708c6` tracks pending START attempts separately from registered runs. Emergency
  STOP cancels an exact current-owner pending V1/V2 startup, interrupts an exact active run, clears
  admission counters idempotently, and does not terminate the ARWeb JVM or another Bot Job owner.
  The emergency authorization path deliberately avoids the Bot Job registry lock so a V1 browser
  navigation holding that lock cannot prevent cancellation.
- [x] Focused backend verification passed 14/14, including V1 cancellation before run ID and exact
  V2 interruption. Focused React verification passed 5/5. The production React build completed
  with only established repository lint warnings. `git diff --check` passed.
- [x] Deployment `4e5638fb` mirrors 61 exact files into backend resources and `target/classes`.
  Live entrypoints are `main.d6be4c21.js` (SHA-256
  `400863337A69ED124CEC1A033D0A2322337BD6E42A0E318E0BD23B4D60828C73`) and
  `main.3fd23b90.css` (SHA-256
  `F368682586257172C6940E406D81E1400309F72A578CCC0F15ECF1AFDBD87E74`).
- [x] Fresh BancaStato ARWeb PID 3944 started at 09:11:54 from `target/classes`, remained
  responsive on `127.0.0.1:50176/50177`, and served both new entrypoints over HTTP 200.
- [x] Live Bot Job 5 V1 evidence: START entered `V1_BROWSER_RESERVING` with `pendingStarts=1` and
  no run ID; STOP logged `FORCE_STOP_SETTLED` with `pendingStartsCancelled=1`,
  `activeRunsInterrupted=0`, and `forcedV1=true`. A second START was admitted and registered as
  run `62e111b5-261e-4a71-b4db-38236280f193`, proving ownership release. Its normal correlated STOP
  logged `STOP_RECEIVED`, `STOP_ADMITTED`, `RUN_INTERRUPT_REQUESTED`, `RUN_TERMINATING`, and
  `RUN_TERMINATED`; the immediately repeated emergency STOP safely settled as idle. PID 3944 stayed
  healthy throughout.
- [ ] V2 live emergency-stop acceptance remains. The exact V2 service path is covered by the
  focused test, but no live isolated V2 browser was started or stopped in this checkpoint.
- [x] The first live V2 retry exposed a separate legacy Bot Job 5 options defect before browser
  admission: its database value concatenates active `arg:` markers without delimiters and uses
  one leading hyphen. SERVER reached READY, but each START correctly failed with
  `Execution V2 browser argument is invalid`; Runtime Instances remained empty.
- [x] Backend `639bb634` recovers only active concatenated `arg:`/`argument:` markers, ignores
  `#arg:` and proxy metadata, normalizes a safe single leading hyphen to `--`, and retains the
  existing 32-entry/512-byte/control-character validation. Privacy-safe logs now record parsed or
  rejected counts/lengths without option values. Focused V2 coordinator/client verification passed
  13/13 and Java compilation passed with 583 main sources.
- [x] Frontend `77c027b` blocks V2 Run with the short message `Start SERVER before running V2.`
  when the server is not READY and gives the SERVER control an orange glow while V2 is selected
  and unavailable. The production build passed with established warnings.
- [x] The latest exact 61-file deployment uses `main.ad474808.js` (SHA-256
  `814ECCE17F559A814FAE9C7FBA3C9D7B06961ED3F98412167F32F2369F150DE9`) and
  `main.83e6fa5a.css` (SHA-256
  `AD722AC9CECB2992EF7D772A8E3BA8CB5EC98D709605330AD2069EF4DD3EE4E6`).
- [ ] The user intentionally closed the test application. Restart one fresh ARWeb instance, start
  SERVER, then rerun V2 and Emergency STOP to complete live V2 acceptance.

## Smoke owner-switch retirement - 2026-08-15

- [x] Frontend `38664ba` keys only the run-local `VariablesSmokeTestPanel` by the verified
  `{homeBankingId, botJobId}` owner. Same-owner renders preserve state; changing Bot Jobs remounts
  that execution surface, closes any stale Locator Recovery modal, and clears the superseded
  plan/cursors/run-local UI without resetting unrelated detached workspaces.
- [x] Backend `bf2de79d` immediately interrupts the superseded V1/V2 operation when the authoritative
  Smoke binding changes, then uses the existing idempotent terminal cleanup. Privacy-safe trace
  events now include `BINDING_CHANGE_RUN_INVALIDATED` and `RUN_INTERRUPT_REQUESTED` with run ID,
  mode, owner IDs, instruction/request IDs, and reason, but no URL, locator, banking text, or value.
- [x] Focused verification passed: React 1/1 and Java 15/15. Java compiled 583 main and 341 test
  sources; the production React build completed with established repository warnings only.
  `git diff --check` passed in both repositories.
- [x] Deployment `8ddf2688` mirrors 61 exact files into resources and `target/classes`; both mirrors
  have zero relative-path/hash differences. Live entrypoints are `main.99b35f77.js` and
  `main.83e6fa5a.css`.
- [x] The old BancaStato ARWeb PID 3780 and its app-owned Node/Playwright roots were stopped. Fresh
  PID 6744 is responsive on `127.0.0.1:55188/55189`; HTTP returns 200 for the root and both current
  entrypoints. IntelliJ and unrelated Java processes were not stopped.
- [ ] Exact live owner-switch acceptance remains: start Job 5, pause in Locator Recovery, switch to
  Job 29, verify the modal closes, Runtime Instances contains no Job 5 run, and the new `.4` Smoke
  trace contains owner invalidation, interruption, and terminal cleanup in order.

## Smoke STOP preserves browser; KILL closes exact browser - 2026-08-15

- [x] Frontend `a715a2a` synchronizes Runtime Instances STOP/KILL with the active React execution
  controller and instruction loop. An exact matching externally controlled run is retired locally,
  pending work receives a typed cancellation, and stale/nonmatching run IDs are ignored.
- [x] Backend `dfd95e32` separates execution termination from browser disposition for both runtimes.
  Main Smoke STOP and Runtime Instances STOP interrupt/terminate the exact run and preserve its
  browser; Runtime Instances KILL interrupts/terminates the exact run and closes V1's shared browser
  or only the selected V2 isolated browser. Executor-shutdown fallback performs the same cleanup
  synchronously instead of stranding a STOPPING run.
- [x] Privacy-safe disk events cover control received/refused/admitted, exact mode and owner IDs,
  browser-close request/settlement/failure, executor fallback, run interruption, termination, final
  browser disposition, and failure type. They never record URLs, locators, values, or banking text.
- [x] Focused Java verification passed 31/31 and compiled 583 main plus 341 test sources. Focused
  React verification passed 9/9. The production frontend build succeeded with established lint
  warnings; this repository has no standalone `typecheck` script. Both diff checks passed.
- [x] Deployment `7c877bd7` mirrors 61 exact files into resources and `target/classes`. Fresh
  BancaStato PID 16044 is responsive on `127.0.0.1:58494/58495`; HTTP 200 serves
  `main.948ca07e.js` (SHA-256 `C9FAFDADA666BF04357F8DA4BF8248AF9F152C1C1CEF01BE4431B51C0DC46D64`)
  and `main.83e6fa5a.css` (SHA-256 `AD722AC9CECB2992EF7D772A8E3BA8CB5EC98D709605330AD2069EF4DD3EE4E6`),
  exactly matching the frontend build. Seven post-start logs have zero strict error matches.
- [ ] Live behavior remains user-driven: for V1 and V2 prove main STOP and Runtime Instances STOP
  leave the browser/page reusable for CONTINUE PAGE or Page Scanner, then prove KILL closes only the
  intended browser and does not affect a sibling isolated V2 run.

## Page Scanner follows the selected V1/V2 runtime - 2026-08-16

- [x] Frontend `138e3d7` persists the selected Smoke runtime per exact Home Banking/Bot Job and sends
  that mode with every browser-scoped Page Scanner action. Missing, invalid, or unavailable browser
  storage defaults safely to V1; changing owners never borrows another Bot Job's selection.
- [x] Backend `de97964b` reauthorizes the exact Page Scanner workspace before parsing the requested
  runtime. V1 preserves the established shared-browser path. V2 obtains a temporary capability over
  only the exact owner's retained isolated browser, runs the established Java scan/OCR/fingerprint/
  snapshot pipeline through bounded Node RPC, and returns that browser to the retained pool.
- [x] A V2 scanner lease blocks a same-owner execution from taking the browser concurrently, rejects
  cross-owner/wrong-token calls, serializes scanner RPC, parks after an idle bound, and never attaches
  to V1 or a sibling V2 browser. Privacy-safe logs record owner IDs, runtime, scanner operation,
  lifecycle, duration, and failure type without URLs, locators, values, grants, or capability tokens.
- [x] Verification passed: Node 44/44; Java focused 75/75 plus `mvn -DskipTests compile` with 585 main
  sources; focused frontend runs 12/12, 9/9, and 14/14; production React build passed with established
  warnings. Both repository diff checks passed.
- [x] Deployment `2b849e98` mirrors 61 exact files into resources and `target/classes`; catalog
  `04f6c33a` records 2,438 rows / 2,402 code cases. Current assets are `main.644f22d0.js` (2,189,216
  bytes; SHA-256 `4727A9E8343B64AA67F4FE4689B11A8CFCFEC12951C2C17CF04D787FB9AD74A4`) and
  `main.83e6fa5a.css` (545,824 bytes; SHA-256
  `AD722AC9CECB2992EF7D772A8E3BA8CB5EC98D709605330AD2069EF4DD3EE4E6`).
- [x] Fresh BancaStato PID 19644 runs from current `target/classes` with the exact Config-4.2 config,
  listens on `127.0.0.1:65306/65307`, and serves root/JS/CSS over HTTP 200 with exact build hashes.
  Seven current log files were created and contain no strict error match; they remain empty until an
  application action emits a record.
- [ ] Live routing acceptance remains user-driven. For one owner: select V1 and prove Page Scanner
  uses the retained V1 page; select V2, run then STOP to retain its isolated browser, and prove Page
  Scanner scan/refresh/Test Input/Test Click/rename use that exact V2 page. Confirm the new runtime/
  scanner lifecycle records appear and that another owner's retained V2 browser is unaffected.

### V2 Page Scanner evaluation/readiness correction - 2026-08-16

- [x] The first live V2 test proved routing and exact-owner leasing, but exposed two Node adapter
  defects: Playwright did not invoke Java function source supplied as text, so scan/fingerprint
  returned `undefined`; after Refresh, a continuously changing banking DOM failed the V2-only
  whole-body stability rule with `PAGE_READINESS_TIMEOUT`.
- [x] `060ea054` explicitly invokes the bounded trusted scanner function with its JSON-safe argument.
  Scanner-only readiness now matches V1: network idle is best effort, element-count stability is the
  decision, and bounded timeout/evaluation interruption does not strand or fail the scanner lease.
  V2 execution startup readiness is unchanged.
- [x] Privacy-safe readiness logs add outcome, sample count, stable confirmations, ready state, node
  count, and duration. Node build plus the expanded suite passed 51/51. Catalog `9330cfa6` is pushed.
- [x] Fresh PID 11280 is responsive on `127.0.0.1:49719/49720` and serves the unchanged exact frontend
  entrypoints over HTTP 200. No Java/frontend source changed in this correction, so Maven and React
  rebuilds were correctly not run.
- [ ] Repeat V2 Test 2 without closing Page Scanner. Expected: nonzero scanned elements, a valid
  fingerprint/snapshot where the page is cacheable, a `browser.scanner.readiness` record, and no
  `PAGE_READINESS_TIMEOUT`.

## Locator Recovery Page Scanner parity - 2026-08-17

- [x] Frontend `a866d2e` adds a cyan `Page Scanner` action inside the Locator Recovery modal. The
  action opens/focuses the detached scanner for the current Bot Job without resolving, bypassing,
  stopping, or closing the pending recovery; the modal remains paused for the user's decision.
- [x] Backend/Node `0cd5cd25` gives that scanner an exact pending-recovery authority. V1 inspects
  the reserved shared browser only for the matching owner/workspace generation. V2 sends scanner
  RPC to the exact active isolated run and never attaches to V1, a retained browser, or a sibling
  V2 run. Scanner work is serialized with the V2 recovery operation.
- [x] Recovery authority is retired on decision start, cancel, bypass, owner/run termination, or
  successful recovery. A failed recovery reauthorizes inspection so the user can review and try
  again. Privacy-safe trace phases cover authorization, retirement, V2 scanner open/RPC/close, and
  Node recovery-scanner RPC lifecycle.
- [x] The earlier Runtime Instances STOP/KILL timeout was independently fixed in frontend
  `e93966f`: an explicit control preempts only the periodic LIST request occupying the shared
  request slot; another explicit control remains serialized. Deployment was `37c45774`.
- [x] Focused verification passed: React 7/7, Node 51/51, and Java 38/38. The Java run compiled 586
  main and 342 test sources; only the established Lombok and varargs warnings remained. The React
  production build passed with established lint warnings.
- [x] Deployment `5e97ddb4` mirrors 61 exact files and 19 images into resources and
  `target/classes`, with zero path/hash differences. Entrypoints are `main.6a639822.js` (2,192,181
  bytes; SHA-256 `993A31340CC297862024C1FEE9CBDD6A9DD5557A5BE476A43C6928C48A55347D`)
  and `main.121bd605.css` (546,116 bytes; SHA-256
  `88F6F12A29121F68DB2B6BDA9ACF80D4922DDBF207F645CD8C0E6851D1096AB5`).
- [ ] No application instance was started because the user closed all instances for this update.
  Live acceptance remains: create a pending Locator Recovery in V1 and V2, click Page Scanner,
  run one scan against the same paused browser, then return to the still-open modal and complete or
  bypass the recovery. Confirm a sibling V2 browser and another Bot Job remain unaffected.

### Locator Recovery Page Scanner audit and expanded coverage - 2026-08-17

- [x] Complete post-implementation review found two concrete lifecycle races. A thrown V1/V2
  recovery operation retired scanner authority without restoring it although recovery remained
  pending; and the modal allowed a recovery decision while its Page Scanner launch was unresolved.
- [x] Backend `cd7717ac` restores exact recovery scanner authority after an exceptional operation
  outcome unless the run is already cancelled. Frontend `5d2f01c` disables every recovery decision
  and the verification toggle only while Page Scanner opening is pending; normal launch failure
  leaves the modal open and retryable.
- [x] New coverage verifies registry validation, exact mode/owner/epoch isolation, ambiguity refusal,
  V1/V2 exceptional recovery restoration, wrong-owner/unknown/missing/settled V2 recovery scanner
  rejection, exact active-run HTTP routing, wrong-token and malformed Node RPC refusal, modal
  serialization, and visible non-terminal launch failure.
- [x] Verification passed React 9/9, Node 51/51, Java affected/neighboring 52/52, and catalog 2/2.
  Java compiled 586 main and 343 test sources with only the two established warnings. Production
  React build passed with established lint warnings.
- [x] Deployment `f8c55364` mirrors 61 exact files with zero source/resource/target differences.
  Current entrypoints are `main.ac1201a0.js` (2,192,199 bytes; SHA-256
  `6301CDCB0A7ACC6E7C50EFCD6F65376B292CFE4C804E11FB292CA9C9DEEB8B26`) and
  unchanged `main.121bd605.css` (546,116 bytes; SHA-256
  `88F6F12A29121F68DB2B6BDA9ACF80D4922DDBF207F645CD8C0E6851D1096AB5`).
- [x] Catalog `c0fadd47` records 2,451 rows, 2,415 code cases, and 19,452 generated API requests.
- [ ] No live application was started. The same V1/V2 manual acceptance matrix remains open.

### In-modal recovery scan, action override, and candidate probes - 2026-08-17

- [x] Frontend `4a57ca6` replaces the Locator Recovery modal's detached-window launch with one
  correlated in-modal Page Scanner operation. The modal keeps the instruction paused, refreshes its
  rows from the response, and adds Action (CLICK / INPUT / OUTPUT), Test Input, and Test Click
  immediately after OCR / mapped evidence.
- [x] Backend `2410ec62` freezes the historical owner/page registry before invoking the established
  Page Scanner pipeline against the exact paused V1 or V2 browser. The normal settle, element scan,
  OCR naming, `scanned_element` persistence, diagnostics JSON, fingerprint, screenshot, and immutable
  snapshot attempt remain authoritative; a bounded matcher then compares the fresh DTOs with the
  frozen registry and replaces only the still-pending recovery candidates.
- [x] Existing clients that omit the action remain backward-compatible and execute the authored
  action. New requests may explicitly select CLICK, INPUT, or OUTPUT; Test Input/Click are physical
  probes and never settle or save the pending recovery.
- [x] Privacy-safe trace phases cover request receipt, pending-state refusal, owner/runtime admission,
  historical freeze, scan completion, candidate comparison/installation, probe start/result/failure,
  resolved action source, and duration. URLs, locators, input values, credentials, and banking text
  are excluded from logs.
- [x] Focused verification passed Java 45/45 and React 24/24. Java compiled 587 main and 344 test
  sources with only the established Lombok and varargs warnings. The normal React production build
  passed with the established repository lint warnings; CI warning-as-error mode remains blocked by
  the pre-existing lint backlog.
- [x] Deployment `efa4b4b8` mirrors 61 exact files into resources and `target/classes` with zero hash
  differences. Entrypoints are `main.5ce146d0.js` (2,195,388 bytes; SHA-256
  `6975FF992D8F462355BBD780064AD9F11F3ADAC0EA7C8AFBCA885EA99FD2C19B`) and
  `main.9629381e.css` (547,452 bytes; SHA-256
  `887F453578D55AECBC30F7A559AAC5DD99E8E1FCEB51EE9187792067E54535F1`).
- [ ] The existing JVM was not restarted, so it still has the prior Java classes loaded. Restart one
  instance before live acceptance, then prove V1 and V2 scan refresh, zero/multiple candidates,
  Test Input/Click, action override, Use Once, Use and Save, bypass, and owner isolation while
  confirming the new trace phases.

### V1 Stop-safe locator discovery - 2026-08-17

- [x] Live run `b9926f22-80f6-4b26-802f-165ff6d2e6de` exposed a coverage gap: Stop retired the
  Integration controller while Playwright Java remained blocked in
  `PlaywrightRuntimeHealingExecutor.probeLiveName` at `locator.elementHandle()`. The serialized
  Playwright worker stayed occupied and the next V1 run stopped at `V1_BROWSER_RESERVING`.
- [x] Backend `6ce63ddb` replaces the two runtime-healing `nth(...).elementHandle()` loops with
  immediate `elementHandles()` DOM snapshots, checks cancellation before and after Playwright
  access and while validating candidates, and never converts cancellation into an ordinary
  unavailable-candidate result. Stop cancellation now logs whether an active operation existed and
  accepted cancellation.
- [x] Focused verification passed 23/23 across the new cancellation/snapshot regression suite and
  the existing Smoke Test Integration lifecycle suite. Java compiled 587 main and 345 test sources
  with only the two established warnings; `git diff --check` passed.
- [x] The fix is committed and pushed. The updated BancaStato runtime is PID `22172`, listening on
  `127.0.0.1:51320` / `127.0.0.1:51321`; HTTP root returns 200 and the new `.3` startup logs contain
  zero strict backend/database/snapshot error matches.
- [ ] Live acceptance still requires stopping V1 during missing-locator discovery and immediately
  starting another V1 run, proving the second run reaches `V1_BROWSER_READY` instead of remaining
  at `V1_BROWSER_RESERVING`.

### V1 render wait and target-first Locator Recovery - 2026-08-17

- [x] The Stop-safe immediate DOM snapshot exposed a timing regression: V1 could exhaust locator
  resolution while a valid late-rendered control was still loading. Backend `44d63205` now retries
  the complete V1 priority resolver under one interruptible 10-second deadline with 150 ms probes.
  Stop remains responsive because no Playwright implicit wait or `Thread.sleep` was reintroduced.
- [x] A located control that remains disabled, or an input that remains read-only, is returned as a
  typed unavailable result after the bounded wait. Smoke Integration records that instruction as
  `SKIPPED` and continues without opening Locator Recovery or attempting the physical action.
- [x] Backend `c69234a7` adds the authoritative unresolved instruction to every V1 recovery payload
  and preserves it across the in-modal Page Scanner refresh. It carries locator/name/page/action
  metadata but is not a selectable database candidate.
- [x] Frontend `043121e` always renders that unresolved instruction as the first table row, followed
  by owner/page-scoped database or Page Scanner candidates. `XPath Match` is immediately after
  `Test Click`; only actual candidates expose selection and physical probe controls.
- [x] Test-first evidence was captured before production changes: the React test failed because the
  unresolved `avanti` row was absent and the XPath column was misplaced; Java test compilation
  failed because the bounded-wait seam and unresolved-target contract did not exist. After the fix,
  focused Java verification passed 17/17, broader Smoke Integration verification passed 24/24,
  and focused React verification passed 17/17.
- [x] The React production build passed with established repository warnings. Deployment
  `6bde7e31` mirrors the exact 61-file build into Java resources using `main.d11c83aa.js` and
  `main.6c3546aa.css`; source/resource path and hashes match.
- [x] The first catalog audit correctly failed because generated evidence was stale (1,502 recorded
  versus 1,512 current backend JUnit declarations). It was regenerated to 2,464 rows / 2,428 code
  cases / 19,452 generated API requests, and the exact catalog verification then passed 2/2.
- [ ] No ARWeb restart or live browser action was performed. Live acceptance must prove: a
  late-rendered target resolves before recovery; a persistently disabled/read-only control skips
  and advances; and a true missing target opens the modal with the unresolved row first and
  database/Page Scanner candidates below it.

### Locator Recovery origin and probe correction - 2026-08-18

- Frontend `1063553` / backend `94bd17e9` are pushed. Locator Recovery now renders the authored
  unresolved instruction as `BOT JOB`, retained owner/page database candidates as `PREVIOUS`, and
  the latest in-modal scanner candidates as `CURRENT`; scanner refresh retains PREVIOUS evidence.
- V2 Test Input/Test Click now probe XPath with explicit Playwright `xpath=` syntax before CSS and
  return `SCANNER_TEST_SELECTOR_INVALID` without exposing raw selectors when syntax is invalid.
- The new blue rules helper beside `Use and Save Locator` documents every origin, probe, decision,
  Page Scanner behavior, verification power, and fail-closed authorization; keyboard focus is
  contained and restored.
- Verification passed Node 52/52, Java 38/38, catalog 2/2, React 25/25, and the React production
  build. Catalog totals are 2,467 rows / 2,431 code cases / 19,452 generated API requests.
- Deployment commit `8696ed25` is pushed. The frontend build, `src/main/resources/build`, and
  `target/classes/build` contain the same 61 paths and SHA-256 values. Entrypoints are
  `main.cb263057.js` (`B261E5734F307A19895786B5A0D30659D25E3EB1E82CD790355D2EFD05146503`) and
  `main.724a0df7.css` (`29CA28FA0933D4CA755135344B75088E910B1ECCE410294ADD13D9F49D2DD8A4`).
- No process restart or live banking action occurred. Restart/live V1+V2 acceptance is still open.

## Test ID first locator contract - 2026-08-18

- [x] Backend `8912aa4d` establishes one V1/V2 locator order: Test ID first, then authored/custom
  XPath, XPath, CSS, registry identity, and name recovery. Standard Test IDs are `data-testid`,
  `data-test-id`, `test-id`, `data-cy`, and `data-qa`.
- [x] Page Scanner now records `automation.test-id.attribute` for an explicitly configured client
  `attr:<name>`. Only that declared client attribute is promoted to Test ID priority; ordinary DOM
  attributes are not. Existing standard IDs remain backward compatible; existing custom IDs gain
  the marker on their next scan.
- [x] V1, V2, recovery Test Input/Test Click, Use Once, and registry candidate execution carry the
  same Test ID-first selector order. The Locator Recovery table shows `Test ID` immediately after
  Select as `attribute=value`, including client-defined attributes.
- [x] Frontend `a02bb1c`, backend `8912aa4d`, deployment `c45e75bd`, and catalog `f7e307f9` are
  pushed. Focused Java verification passed 33/33 plus catalog 2/2; React passed 11/11; Java compile,
  Node build, and React production build passed. The catalog has 2,473 rows / 2,437 code cases.
- [x] The 61-file frontend build is mirrored exactly into resources and `target/classes`.
  Entrypoints are `main.dbaeb5e6.js` (SHA-256
  `7401BA21C51905212CA2A1988A5C3425248569E8906B8C801393D51688939D7D`) and
  `main.724a0df7.css` (SHA-256
  `29CA28FA0933D4CA755135344B75088E910B1ECCE410294ADD13D9F49D2DD8A4`).
- [ ] No ARWeb process was restarted and no live banking action was performed. Live acceptance is
  one V1 and one V2 instruction with a standard Test ID, then a newly scanned client-configured
  Test ID, proving Test ID is attempted before a deliberately stale XPath.

## Test ID and Locator Recovery coverage hardening - 2026-08-18

- [x] Review found one changed-surface failure path: a rejected Locator Recovery decision released
  the busy state but became an unhandled React promise rejection. Frontend `719a538` now keeps the
  modal open, renders the bounded failure message, and remains retryable without changing backend
  authorization or decision semantics.
- [x] Focused coverage is exactly 100% instructions/branches/lines/methods for Java
  `TestIdLocatorContract`, and 100% statements/branches/functions/lines for both Locator Recovery
  React components. Java contract tests are 7/7; Locator Recovery React tests are 19/19.
- [x] Node/Playwright V2 passed 52/52 plus TypeScript build. The full Java run reached 1,516 passes
  with only two local-page navigation timeouts; both timed-out browser tests passed 2/2 in an
  isolated rerun. Catalog verification passed after regeneration to 2,485 rows / 2,449 code cases.
- [x] The full React repository baseline is still 891 passes / 54 failures across 18 unrelated
  legacy suites. None of those failures references the changed Locator Recovery files; therefore
  repository-wide 100% is not claimed.
- [x] Backend `e65b1d29`, frontend `719a538`, and deployment `e234f95a` are pushed. The successful
  production build is mirrored exactly across 61 source/resource/target paths. Entrypoints are
  `main.a82a4b50.js` (SHA-256 `9A03293909391FC16CEFB993B81EF7A1F1DCD2BB66732C6D1EC3498A29D3EB20`)
  and `main.724a0df7.css` (SHA-256 `29CA28FA0933D4CA755135344B75088E910B1ECCE410294ADD13D9F49D2DD8A4`).
- [ ] No application restart or live banking action was performed. Live V1/V2 Test ID precedence
  and rejected-decision retry behavior remain manual acceptance gates.

## Test ID adjacent-contract regression expansion - 2026-08-18

- [x] A second end-to-end audit covered the consumers around the already fully covered Test ID
  normalizer: Java V1 selector construction, persisted scanner attribute parsing, unresolved and
  selected Locator Recovery evidence, recovery matching, V2 action DTO construction, and Node V2
  selector execution order. No new production defect or production-source change was required.
- [x] Seven focused regression cases prove standard-ID ordering and deduplication, custom-ID
  mismatch fallback, persisted configured-ID authorization, scanner option/metadata propagation
  without a browser, BOT JOB and CURRENT recovery evidence, V1 selected-row reference
  reconstruction, V2 recovery payload ordering, and Node's first-selector short circuit.
- [x] Verification passed 29/29 focused Java tests with JaCoCo, 79/79 broader affected Java tests,
  53/53 complete Node V2 tests plus TypeScript build, and catalog validation 2/2. The core
  `TestIdLocatorContract` remains 100% line/branch/method covered. Large legacy executor classes are
  not represented as repository-wide 100% coverage.
- [x] Commit `c652e639` is pushed. The regenerated catalog records 2,492 rows / 2,456 code cases /
  19,452 generated API requests.
- [ ] No frontend production source, build asset, Java production source, database, process, or live
  browser was changed. Runtime restart/live V1 and V2 standard/custom Test ID acceptance remains open.
