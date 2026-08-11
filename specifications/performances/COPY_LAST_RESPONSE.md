# COPY_LAST_RESPONSE - Claude <-> Codex bridge (LATEST exchange only)

Keep exactly two review sections. Check tasks only after their separate gates pass.

**Last updated:** 2026-08-11 - Frontend `57c3118` prioritizes persisted client names over rerun OCR proposals and adds the separated Client Names & Instructions rules tab. Focused checks passed 2 suites / 3 tests; production build and exact 58-file source/target mirror passed. Deployment `e3469fb7` and catalog `3db5da92` are pushed. No ARControlPanel process was running during the target copy, so user launch/live acceptance remains open; no Java source, Maven command, migration, package, or image is claimed.

## 1. CODEX -> CLAUDE - Page Mappings and Memory lifecycle review handoff

### Outcome

The 12 findings in `Page Mappings REVIEW FIXES 2026-08-07.md` are fixed in source and pushed.
The remediation establishes server-owned owner/session authority, authenticated detached-window
reconnects, generation-safe retarget/delete behavior, scan-owned immutable artifacts, verified
capture geometry, authoritative Page Mapping Apply, bounded SQL Server schema repair, artifact
lifecycle cleanup, URL redaction, and correlated Memory List workspace generations.

The latest isolated corrections fix failed Memory OPEN/SYNC responses and detached Memory command
retarget races without changing the existing WebSocket operation names or persistence actions:

```text
memoryList.open / memoryList.sync
  -> exact typed pending request
  -> Java failure-only correlation envelope
  -> FAILURE settles by request ID plus any supplied owner-generation assertions
  -> SUCCESS still requires exact owner/workspace generation

memoryList.command
  -> pending action captures homeBankingId + botJobId + workspaceEpoch + ownerEpoch
  -> an authoritative owner retarget synchronously retires the old timer/dialog/status
  -> late old-owner responses and drags are ignored
  -> Java replies only to the captured requester while it still owns the transport
```

The Page Mappings roadmap is now source-complete through P7:

- P5 cache-first scanning: backend `c8e722cd` plus correction `823ab2dc`; frontend `14b7832`.
- P6 safe runtime healing: backend `668a7acb`.
- P7 selected-capture OCR Review and atomic alias Apply: backend `89bbce24`; frontend `4dc51aa`.
- P7 legacy OCR Results retirement: backend `07f3fd47`; frontend `b2d8a59`.
- Snapshot private ACL and retention/pin/purge baseline: backend `478a51b2`; frontend `dfd4836`.
- Snapshot lifecycle and WebSocket hardening: backend `09fa2824`; frontend `cb64ab3` plus
  `6750c3b`. The policy is explicitly system-wide; capture counts and mutations remain bound to the
  authoritative organization/Bot Job.
- Clean frontend deployment mirror and regenerated catalog: backend `c3a86e6f`, sourced from
  frontend `6750c3b`. The unrelated dirty Grid change was excluded.
- Item-7 retention/snapshot coverage and fixes: frontend `f8dd5aa`, backend `380841af`.
- OCR Apply reconnect recovery: frontend `449f9ea`; the exact request survives reconnect and
  unknown outcomes remain blocked until correlated bootstrap plus verified capture reload.
- OCR backend authority/ledger/transaction recovery: `3e365b25`. Requests are authorized before
  admission, identical replacement transports attach, successful Applies replay safely, abrupt
  errors release the lane, and commit ambiguity returns `reloadRequired=true`.
- OCR Config's stale desktop-shell drag-handle expectation was corrected in frontend `a51e792`;
  production presentation was not changed.
- Current deployment mirror is backend `fb23c531`, sourced from frontend `a51e792`; current catalog
  is backend `d76362ff`, recording backend source `3e365b25` and frontend `a51e792`.
- The retirement commit changed only OCR Results concerns in `GridItemScann`; Claude's unrelated
  rollback-name Grid work remains unstaged and uncommitted.
- In an authorized BancaStato-only maintenance window, the exact live SQLite database was backed up
  and exactly the three registered snapshot migrations were applied in one transaction. Other
  installations and SQL Server remain unchanged and migration initialization remains user-controlled.
- A targeted BancaStato scan created READY capture `16a2d848-6660-4f86-9786-5726d209d4e9` for
  Home Banking 13 / Bot Job 29 / Home URL 15 with 93 elements. Its manifest/payload hashes and
  private Windows ACLs were verified end to end.
- Both WebSocket ingress guards previously used `contains("ping")`, which swallowed every
  `pageMappings.*` operation because `Mappings` contains lowercase `ping`. Pushed backend commit
  `70d5d08d` now recognizes only exact `ping` / `ping-*` control frames.
- BancaStato acceptance PID `2852` ran the rebuilt class from `target/classes`, served the current
  frontend hashes, and completed Page Mappings open, bootstrap, integrity-verified capture, cache,
  explicit pin, and capture-reload round trips. It is now stopped; no backend package/image was
  built.

### Step 8 owner-switch correction

- The history query was already owner-filtered. Lloyds rows visible under Bot Job 32 are genuinely
  stored as owner 2 / Job 32 because the process-global Playwright page retained Job 29's live page
  during a Bot Job switch. Existing contaminated rows were preserved pending explicit authorization.
- Backend `3721c049` now strictly retargets the shared browser to the active Bot Job endpoint,
  retargets Page Scanner/Page Mappings generations, validates detached transports against the
  Registry, and fences every Page Mappings mutation and bootstrap across owner changes.
- Frontend `17748b8` adds an atomic WebSocket message-buffer generation, resets Page Mappings cursors
  before replacement-buffer consumption, and clears stale owner-sensitive state while preserving a
  fail-closed reload requirement.
- Frontend `e23c6d6e` disables the detached shell's duplicate Close control, leaving one guarded
  Page Mappings Close action.
- Backend `c594ba5b` safely permits top-document reuse when frames merely exist. Live evidence then
  isolated open Shadow DOM as the remaining cacheability gate.
- Backend `b9222d2f` adds a versioned, bounded open-Shadow-DOM fingerprint with explicit root and
  slot boundaries while preserving every non-shadow fingerprint byte. The scanner excludes
  Playwright-pierced shadow descendants because their DTO/geometry boundary is not yet representable;
  those controls and closed roots remain intentionally unsupported rather than misidentified.
- Clean frontend `npm run build` passed with existing warnings. The exact 58-file mirror is backend
  `242095b2`; entrypoints are `main.d31d8186.js` (SHA-256
  `81D457AF99A8CCEE16B5B6E323DE5FE0B2AEAC4698942E5E21CF1C3DC0E4A89E`) and
  `main.9afd0737.css` (SHA-256
  `4A1E4538BFF7E0FD0C6106BC2EAEAA6A6F4720D231E2153B617D309AA594B04B`).
- Final Java compilation passed with 562 sources and only the two existing warnings. No Step 8 test
  was created or run, and no package/image was built.
- PID `8032` runs the final classes with the exact BancaStato config on ports 60711/60712. Root and
  both assets return HTTP 200 with matching bytes; six `.10` logs contain zero relevant errors.
  Read-only SQLite health is `quick_check=ok`, 24 migrations, zero FK violations, 13 READY rows,
  zero fingerprint-bearing rows, and no sidecar.
- Two user-triggered pre-`b9222d2f` scans on the correct BancaStato URL completed READY but retained
  blank fingerprints under the old Shadow DOM gate. One fresh post-deployment Rescan is required;
  Codex did not trigger a scan.

### Page Mappings UI, cleanup, and adaptive full-page Rescan

- Frontend `716a686`, `7264fa7`, `f5cb822`, `7f04cbc`, and `af6fffd` respectively deliver independent
  history/details scrolling, captured-element search clear plus the pulsing Pages badge, the
  focus-contained bottom-right rules guide plus horizontal natural-width screenshot pan, and the
  correlated red/green `SCROLL PAGE` toggle. `Use Existing` remains a read-only integrity load of the
  newest READY capture matching the live page fingerprint; it intentionally selects that reusable row.
- Backend `260f2025` authorizes and propagates `scrollPage`, traverses the top-level document before
  fingerprint/scan, restores and revalidates exact page identity, and forces only that snapshot to
  `full_page`. OFF and ordinary Page Scanner behavior remain unchanged. Retention-days fallback is
  now 30 for missing/blank/invalid configuration, while explicit `0` still disables age cleanup.
- The two exact Lloyds snapshots incorrectly stored under Home Banking 2 / Bot Job 32 were removed
  from the active DB and their artifact folder was quarantined under the recoverable backup
  `D:\Projects\ARWebBancaStato\ARWeb\Backup-CODEX-2026-08-10-job32-lloyds-cleanup`. Database backup
  SHA-256 is `8B4BD1F19A535644824F953E2D7FDEA4291592020A7E082F1CBC1DF0D7F95716`.
- Live evidence showed the first 200 ms scroll hops completed in about 2.3 seconds: every repeat found
  239 locators, while OCR availability rose 10 -> 25 -> 51. Backend `20c8a4bc` replaces the fixed
  delay with bounded readiness for paint frames, relevant DOM/class/style quiet, near-viewport image
  decode, fonts, and finite visible animations, including stable-bottom and restored-position gates.
- Frontend build passed with existing warnings. Backend `6b2d1350` mirrors exactly 58 files;
  `main.3f8cb24e.js` SHA-256 is
  `089E7A4564C3345B3B4CE0DB2D8AAA9C734253D8A45FB38FA4252DAC2F131C58` and
  `main.c51a1b29.css` SHA-256 is
  `FC65A5462FF227DB8CAA8936C68DC2B6FCCFC611DB6BC38621C8BB59AF1918BA`.
- Java compile passed with 563 sources and the two existing warnings. No tests were created/run and no
  package/image was built. PID `4428` runs the adaptive class with the exact BancaStato config on
  54668/54669; HTTP assets match, the six new `.16` logs have zero relevant errors, and read-only DB
  state is `quick_check=ok`, 24 migrations, zero FK violations, 17 READY Job 32 BancaStato rows, two
  pinned, zero Job 32 Lloyds rows, and no SQLite sidecar.

### Per-Bot-Job SCROLL PAGES limit and redeployment

- Frontend `d5f6dad` provides a styled integer limit, default `5`, range `1..40`, persisted in browser
  storage under the exact Home Banking/Bot Job key. The toggle remains transient OFF; dirty/invalid
  drafts disable cache and Rescan actions until committed.
- Backend `805968ad` parses explicit values only after detached-owner authorization, defaults a missing
  legacy field to `5`, correlates the exact value on accepted/status frames, and counts confirmed
  downward movements. Stable bottom or the selected cap is a bounded success; technical/render
  failures remain fail-closed.
- Clean frontend build and deployment mirror `4e5813c0` contain exactly 58 matching files and 19
  matching image assets. The 24 stale `target/classes` bundles were removed; old
  `main.3f8cb24e.js` / `main.c51a1b29.css` are absent. Current entrypoints are
  `main.15510fe8.js` (SHA-256 `50F04B0F4BB47EF58F2A393A49415479D5D6F4C7704DA28BA939B0D5CE048902`)
  and `main.974b35cd.css` (SHA-256 `7BCBDD73DD3F192571D806928F9E170E77E8AE7F06FD4F9DFCC666B4EC674E63`).
- `mvn -DskipTests compile` passed with 563 sources. PID `21796` runs the exact BancaStato config from
  `target/classes` on 53734/53735; root/JS/CSS return 200 with matching bytes and six `.17` logs have
  zero relevant errors. No tests, migration, backend package, or container image were produced.

### Page Mappings header and element-total refresh

- Frontend `cf16efe` renders the selected capture's authoritative `elementCount` to the right of
  `Search captured elements`; filtering and the 200-row display cap do not affect this total.
- Page Mappings now uses the Main Dashboard page/header palette and spacing. Its two-column grid,
  responsive stacking, and independent history/details scroll owners are unchanged.
- Frontend build passed with existing warnings. Deployment `98ae848b` mirrors 58 files and 19 image
  assets exactly; stale prior bundles are absent. PID `12944` serves `main.92c3e040.js` and
  `main.1ac3c57f.css` with matching hashes on 55720/55721; six `.18` logs contain zero errors.
- No tests or Maven command ran. Read-only DB evidence is 881 active BancaStato scanned-element rows;
  the latest Job 32 READY capture contains 239 elements.

### Smoke Test Playwright page refresh

- Frontend `7d5a157` adds the isolated two-line `Refresh` / `Web Page` button immediately before
  `Stop`, using the established Real/Synthetic Data toggle sizing and interaction pattern.
- Backend `aab60fca` owner-authorizes `smokeTest.integration.refresh` against the exact Smoke Test
  transport, binding, workspace, Bot Job owner, and graph revision before reserving the shared
  Playwright browser and calling its reload/settle path. Bot Job mutation fencing prevents a switch
  during refresh; the React Smoke Test page is never reloaded.
- The button is available only for an authoritative, idle Integration workspace and is disabled
  while execution or another browser owner is active. Response handling is request/binding/owner/
  graph correlated and duplicate successes are replay-safe.
- Frontend build passed with existing warnings; Java compile passed with 564 main sources and the
  two existing warnings. No tests ran. Deployment `cd9bf34a` contains 58 matching frontend files
  and 19 images.
- PID `17864` served `main.9a55ef9b.js` (SHA-256
  `379BB80F481BFE97BB563BFAA98071125BEDDA06A97F85DD4F8DE53940F965F9`) and
  `main.069de826.css` (SHA-256
  `08535CB786F8B8A3D27FCA4BFF7953F48B1B20B27667A279337DBCD98101C16F`) from
  `target/classes` on 62590/62591; root and both assets returned HTTP 200. It stopped normally after
  the Main window closed. The new endpoint safely refused one action because no Bot Job Playwright
  page was open; no JVM/SQLite/snapshot failure followed.
- Live acceptance remains: restart ARWeb, open Lloyds Bot Job 29 and its Playwright page, then click
  Refresh Web Page from the idle Smoke Test Integration workspace. Codex did not trigger the refresh
  or run a test.
- The later Step 0 plan failure was Bot Job 32 requesting inactive Block 204 (`Registra eBill`), not
  Lloyds Bot Job 29. Frontend `124ecb6` preserves inactive blocks in the visible/bypassed flow but
  filters them from the executable Integration scope; an inactive-only selection fails locally with
  a precise message and resets the start phase.
- `npm run build` passed with existing warnings; no tests or Maven command ran. Deployment `1883a1bc`
  mirrors 58 exact files. PID `11496` serves `main.da89dda3.js` (SHA-256
  `A9CFBE95199F991D5CF0D1CD2D42200F24E5DE56D08ED413E69064D9E35FEC61`) and
  `main.634ba30e.css` (SHA-256
  `BFFC0DF3E252F977CED9A36791D481D00509474249B9D3052CA7EAF397178B64`) on 61402/61403; six
  `.23` logs have zero error/strict matches at the checkpoint.

### Smoke Test live row actions and unified locator healing

- Backend `4c11186e` makes manual `TEST INPUT` / `TEST CLICK` and full Integration use the same
  `RuntimeElementHealingService` plus one physical Playwright operation. Resolution preserves the
  existing priority order: authored locators, current-page owner-scoped `scanned_element` mappings,
  then unique canonical/client alias matching, with coordinates last and ambiguity fail-closed.
- The manual-action and instruction-active contracts now accept only the exact authoritative
  `smokeTestManager` binding in addition to their existing Bot Job Details transport. Owner,
  workspace epoch, graph revision, license, reconnect, and terminal-current-requester checks remain
  enforced. Active state still writes one database source and publishes to both views.
- Frontend `f7f9aae` adds isolated green/red Active, blue `TEST INPUT`, and orange `TEST CLICK`
  controls on the right of each applicable Smoke Test flow row. They reuse the existing GridItem
  hooks/contracts and are disabled during stale/unbound/disconnected state, Integration execution,
  or any pending row action.
- Focused Java verification passed 47/47 with no failures/errors/skips; `mvn -DskipTests compile`
  passed with 564 main sources. `npm run build` passed with existing warnings. No frontend suite or
  real Playwright row/Integration action ran.
- Catalog `2b11e657` was regenerated without executing tests and records backend `3fcee24c`, frontend
  `f7f9aae`, 2,341 rows, 2,305 code cases, and 19,452 generated API requests.
- Deployment `3fcee24c` contains 58 exact resource/target files. Live entrypoints are
  `main.0d1c19c7.js` (SHA-256
  `D7485EE02FF812470E5467FE164EDBA190645E91D744BF95D515231EE0402F22`) and
  `main.11a8513b.css` (SHA-256
  `89C813BE043B8A39BD450667E9FAF35D39344B703E893DAEB0642A039A688406`). PID `31360` serves
  them on 62094/62095; Smoke Test connected and received bootstrap/status synchronization, and six
  new logs had zero strict matches at restart. Later user browsing added only page-console HTTP 404
  and 400 resource messages; no JVM/SQLite/snapshot/WebSocket operation failure is present.
  Browser-control was unavailable, so visual/action acceptance is not claimed.

### Smoke Test execution-type toggle and exact manual-action binding

- Backend `a1d6bd3e` authorizes the existing database-backed `gridItem.webElementType.update` operation
  from the exact active Smoke Test binding as well as Bot Job Details. Owner, workspace epoch, graph
  revision, expected type, exact-one write, and current-recipient checks remain enforced.
- Frontend `3fde7be` reuses the established GridItem/Page Scanner `WebElementTypeToggle`, so every
  applicable Smoke flow row cycles `INPUT -> OUTPUT -> CLICK` with the same icons and semantics. The
  Active/Inactive button now contains only its power/spinner SVG, while the power, type, Test Input,
  and Test Click controls share one row. Manual Test Input/Click requests now include the required
  Smoke `bindingEpoch`; the earlier binding-less requests were rejected before Playwright execution.
- The focused Java execution-type matrix passed 13/13. `mvn -DskipTests compile` passed with 564
  sources and the two existing warnings; `npm run build` passed with existing warnings. No frontend
  suite or live Integration action ran.
- Deployment `74168d27` and catalog `3609803d` are pushed. The 58 source/target files and 19 images
  match exactly; stale target bundles were removed. PID `20668` serves `main.45672047.js` (SHA-256
  `C793B11EC3E6D7496B83C721A2AA8B085D7C756CC8AB12067815F3C9424A1157`) and
  `main.aacbfa82.css` (SHA-256
  `0EAD57019FEDDE86C53558714F1A3F3F9B3C6378B2573EA9D2CB90DA335C908B`) on 64433/64434. HTTP
  freshness passed and the six new `.3` logs have zero strict operational failures at checkpoint.

### Smoke Integration selected-page startup and Stop recovery

- Backend `9fe40dbf` replaces the preserve-current-page START seam with strict navigation to the
  selected plan URL plus the existing 15-second page-settled gate. The entire navigation/settle is
  fenced by the exact Bot Job workspace generation, and START refuses blank/`about:blank` state.
- Frontend `882af61` makes Stop/Finish one terminal request at a time, prevents the canceled step's
  completion/rejection from restoring READY over STOPPING, and resets its response cursor from the
  atomic WebSocket message-buffer generation. This fixes the observed need to switch Bot Jobs after
  Stop before row controls became usable again.
- Focused checks passed: Java 3/3 and frontend 8/8 across two suites. The frontend run retained only
  existing React `act`/open-handle warnings. Java compilation completed with 564 sources and the two
  existing warnings; `npm run build` completed with existing repository warnings.
- Deployment `4dade5a0` contains 58 exact source/target files and removes the stale prior JS bundle.
  PID `29912` serves `main.6a91a10f.js` (SHA-256
  `36DA34C21B87826BFC1939E7BA8AACE1833F1BECA9876B2F7F3AE01C08CDEE36`) and
  `main.aacbfa82.css` (SHA-256
  `0EAD57019FEDDE86C53558714F1A3F3F9B3C6378B2573EA9D2CB90DA335C908B`) on 59091/59092.
  The six new `.5/.4` logs contain zero strict Java/SQLite/snapshot/Smoke-start failures.
- Catalog `ce41e3f7` records 2,342 rows and 2,306 code cases without executing tests. No migration,
  package, or container image was created. Live Lloyds START/Stop acceptance remains user-driven;
  the two locator refusals remain intentionally fail-closed and are not presented as fixed actions.

### Smoke runtime locator-strength correction

- Live instruction `1749` (`personal_2`) had one exact scanned registry row, but the shared
  `span.btn-text` CSS expanded preparation to 73 candidates and left two live targets. The runtime
  correctly refused before any physical click.
- Backend `453710d2` ranks exact XPath above stable attributes above CSS-only identity. A single
  authoritative registry candidate may use its persisted scanned text to narrow a broad selector,
  but action still requires exactly one visible, boundary-compatible target; genuine duplicates
  remain fail-closed.
- Focused verification passed 2/2 and Java compilation completed with 564 main sources. Catalog
  `6d88c97c` was regenerated without executing tests and records 2,344 rows / 2,308 code cases.
- PID `27756` runs the rebuilt BancaStato `target/classes` on 59032/59033; HTTP root returns 200 and
  the fresh `.5/.6` logs contain zero strict runtime-healing/JVM/SQLite/snapshot failures. No
  frontend build, migration, package, or container image was produced.
- User acceptance remains: retry `personal_2` and record the new structured diagnostic. Success
  must show one physical attempt; any remaining real duplicate must stay at zero attempts.

### License Request status and validation UI

- Frontend `1e8d528` renames Info to `About this Software` and the license workspace/header to
  `License Request`. A compact green/yellow/red top-bar status now reports Ready, progress,
  connection/backend failures, malformed responses, and mandatory-field errors.
- Organization, Owner, valid email, response/license file, and agreement acceptance are validated
  before send. The backend remains authoritative for license validity and activation state.
- Focused frontend verification passed 2 suites / 8 tests; `npm run build` passed with existing
  warnings. No Java source changed and no Java test ran.
- Deployment `c320f5a6` mirrors 58 exact files and 19 images. PID `28552` serves
  `main.7a606860.js` (SHA-256
  `3E0C2D347E8861C68D04208ED7F352146DE1FC17B184869A4E6464448277DD48`) and
  `main.834b1a93.css` (SHA-256
  `B2EACC407DF28A4CCA57F3B1AAE8770BC5C19164735052743D5AA8B9E04E87C3`) on 53768/53769.
  Catalog `2f0db93e` was regenerated without executing tests.
- The in-app browser surface was unavailable; HTTP freshness and focused component behavior passed,
  but visual License/About approval remains a separate user gate.

### Page Mappings Scan Flow and remaining verification

- Backend `dd38963f`, frontend `08957d6`, and deployment `98d59860` already provide the read-only
  organization -> Bot Job -> page Scan Flow tree. Its filter updates both the visible tree and its
  Web Elements/Bot Jobs/Pages cards.
- No dedicated test currently covers the backend inventory aggregation/owner isolation, frontend
  modal filter/cards/tree/focus, bootstrap parser, or retarget clearing. These are the first focused
  Page Mappings tests to add.
- Current read-only DB state is 881 Home Banking 2 registry rows across two Bot Jobs/five page keys;
  Job 32 has 20 READY captures and its latest is a 239-element, fingerprint-bearing READY capture.
- The 881 owner total includes 93 Job 32 `scanned_element` rows for Lloyds. The previous targeted
  cleanup removed only the bad snapshot rows/artifacts. Do not call 881 a clean BancaStato-page
  count and do not delete the registry rows without reference/instruction audit plus authorization.
- Remaining acceptance/test groups are Use Existing zero-write, non-default adaptive Rescan,
  29 -> 32 live retarget, retention Save/Purge, OCR Apply, Memory Add/drag/Apply, reconnect/takeover/
  same-ID/multi-page behavior, orphan reconciliation, other DB/SQL Server rollout, and packaging.

### Page Mappings OCR Review guidance and alias rollback

- Frontend `4ff3a99` expands the rules modal with the exact selected-screenshot Review, safe-test,
  Apply, Memory, rollback, limit, and recovery rules.
- Read-only SQLite evidence confirms the user's save succeeded: scanned row 672 for Bot Job 32 is
  canonical `bancastato` with `client_named=Banca Stato`. The UI looked reverted because editing did
  not auto-select Use and the Apply response caused the old OCR proposal to be initialized again.
- Frontend `3f67e5a` auto-selects a valid edited draft, preserves the acknowledged value for the same
  Review request, and removes the pending selection after it becomes authoritative. Its compact
  restore icon submits one exact `clientNamed=null` change through the existing OCR Apply contract;
  there is no Rescan or new backend route.
- Successful Apply refreshes matching staged Memory labels/revisions. Add/drag carries the saved
  alias, while Bot Job instruction creation remains a separate Memory List Apply action.
- Deployment `64f499e1` and catalog `91bab2a3` are pushed. PID `1556` serves exact
  `main.b8284312.js` / `main.680e6c4a.css` bytes on 57395/57396. No visual browser acceptance or
  test suite ran.

### Page Mappings clickable Memory List card

- Root cause was frontend-only: the visible Memory List summary was a passive section and only
  Add/drop requested `memoryList.open`.
- Frontend `0cd8bed` makes the whole card clickable and keyboard-actionable, including with zero
  selected items, through the existing owner-bound open/focus request. It adds the cyan glowing
  `Drop captured elements here, or use Add.` badge and honors reduced-motion preferences.
- The Windows message means browser focus was delivered but foreground ownership was not confirmed.
  Exact native focus/topmost fallback already runs and Windows may still refuse; this checkpoint
  does not hide the result or broaden the shared native focus service.
- Build passed with existing warnings. Resource deployment `bfe4cf87` has 58 exact files;
  catalog `729f0850` was regenerated without executing tests. The prior PID ended externally, and
  no target copy/restart/live click-through is claimed.

### Page Mappings client-name priority and instruction guidance

- Live SQLite and source evidence agree that Rescan did not erase `Banca Stato`: row 672 retained
  its `client_named`, and the registry upsert deliberately preserves the existing alias. The UI
  problem was OCR-first Proposed-name priority, which could make a rerun look reverted and prepare
  an accidental overwrite.
- Frontend `57c3118` makes the saved alias the Proposed default. Existing aliases stay unselected
  after Run again; OCR text remains visible separately, and only manual edit/select/Apply changes
  the alias. Restore remains the intentional canonical-name rollback.
- The rules dialog now has separate `Workspace Rules` and `Client Names & Instructions` tabs. It
  explains the canonical/client-name boundary, migration-reference role, Rescan/OCR preservation,
  Add/drag staging, separate Memory Apply, and fail-closed duplicate/cross-page identity rules.
- Focused verification passed 2 suites / 3 tests. Build passed with existing warnings; no Java or
  Maven command ran. Deployment `e3469fb7` and catalog `3db5da92` are pushed.
- The 58-file/19-image source and `target/classes` copies match exactly. Current entrypoints are
  `main.0b70d82f.js` (SHA-256
  `1F1B5A29BB1917E18C035715FD4EC4FA526B46034A058768AB400C4392513C89`) and
  `main.8822f0dc.css` (SHA-256
  `8290860100F7E9284FD30031BDBE45B3A022C0118F690D6999DE65F84B6BBC9B`). No app was running during
  target deployment, so startup and live OCR acceptance remain user gates.

### Current risks

| Severity | Status | Risk |
|---|---|---|
| Critical | Fixed in `70d5d08d` | Raw and decoded WebSocket ingress treated any lowercase `ping` substring as a heartbeat, silently dropping all `pageMappings.*` operations. Only exact `ping` / `ping-*` control frames are ignored now. |
| Critical | Fixed in `209d24d7` / `ce6a56f` / `fb87aa0` | Failed Memory `open` responses without `workspaceEpoch` could be discarded and leave opening stuck. Exact failures now correlate by typed request/current context and validate every supplied authority field. |
| Critical | Fixed in `209d24d7` / `ce6a56f` / `fb87aa0` | Failed Memory `sync` responses lacked pending request correlation and could disappear silently. OPEN and SYNC now use typed pending records and collision-resistant sequenced request IDs. |
| Critical | Fixed in `fb87aa0` / `b147de41` | Detached Memory commands, timers, dialogs, status, and drag state are bound to the exact owner/workspace generation; late prior-owner responses are ignored and backend responses stay on the captured requester transport. |
| High | Bounded live acceptance open | PID `31360` runs the latest exact frontend/backend deployment, but one user-driven SCROLL PAGE Rescan with a selected non-default limit is still needed to compare Page Mappings visual completeness. Build/restart and earlier default-limit captures do not close this gate. |
| High | Smoke Test live acceptance open | Selected-page START and single-flight Stop recovery are deployed, but no post-deployment bank action ran. Lloyds Bot Job 29 still needs one user-driven START proving automatic strict URL opening/settlement, then one Stop proving controls recover without switching Bot Jobs. The coordinate-invalid and ambiguous-target refusals remain fail-closed locator diagnostics. |
| Medium | Cleanup complete and recoverable | The two exact Job 32 Lloyds rows/artifacts are absent from active storage. The guarded pre-write database and quarantined artifacts remain under `Backup-CODEX-2026-08-10-job32-lloyds-cleanup`. |
| Medium | Bounded browser limitation | Virtualized lists, nested scroll containers, canvas/video, CSS background resources, and unbounded infinite pages cannot be guaranteed. The adaptive traversal fails closed on its bounds rather than storing known-incomplete output. |
| Medium | Open verification | Live detached-window reload, takeover, retarget, deletion, same-ID reuse, and multi-page WebSocket behavior remain unverified. |
| Medium | Data reconciliation open | Home Banking 2's 881-row Scan Flow includes 93 Lloyds `scanned_element` rows under Job 32. Wrong immutable captures were removed earlier, but cumulative registry cleanup requires a separate reference audit and authorization. |
| Medium | Open verification | The final affected-path JSDOM suite passed 44/44. The complete repository-wide frontend suite was not run. |
| Low | Existing | Production build lint/dependency/bundle-size warnings remain outside the Page Mappings retention files. |

### Evidence and checkpoints

- [x] TASK - Original 12 review findings fixed in source.
- [x] TASK - Selected Page Mappings/Memory backend regression suite passed: 206 tests, 0 failures/errors/skips.
- [x] TASK - Final lifecycle-focused backend checkpoint passed: 85 tests, 0 failures/errors/skips.
- [x] TASK - Java compile after isolated failure correction passed: 548 main sources.
- [x] TASK - Frontend production build after correction passed with existing warnings.
- [x] TASK - Memory request/command/retarget/drag focused frontend suite passed: 25 tests, 0 failures.
- [x] TASK - Nearby selected frontend run passed 27 of 35 tests; eight stale `GridItemComp.memoryParity` expectations remain open and were not modified.
- [x] TASK - Latest frontend production build passed with existing warnings.
- [x] TASK - Build mirror verified: 58 source files / 58 backend files; `main.16e24f7b.js` SHA-256 `ED8C10BCA7B21661C19EA67613DE04884EC27CA5920C06D6A65A1E469A112004` matches.
- [x] TASK - Backend lifecycle commit pushed: `ea68268e`.
- [x] TASK - Frontend generation commit pushed: `7774aeb`.
- [x] TASK - Backend failure-envelope commit pushed: `209d24d7`.
- [x] TASK - Frontend request-correlation commit pushed: `ce6a56f`.
- [x] TASK - Exact Memory requester-routing commit pushed: `b147de41`.
- [x] TASK - Detached Memory command-generation commit pushed: `fb87aa0`.
- [x] TASK - Latest deployment-assets commit pushed: `9a9dc6db`.
- [x] TASK - P5 cache-first scanning pushed: backend `c8e722cd` / `823ab2dc`; frontend `14b7832`.
- [x] TASK - P6 safe runtime healing pushed: backend `668a7acb`.
- [x] TASK - P7 OCR Review core pushed: backend `89bbce24`; frontend `4dc51aa`.
- [x] TASK - Earlier P5-P7 core Java compile passed: 555 main sources.
- [x] TASK - Focused Page Mappings OCR frontend lint passed with 0 errors and one existing hook warning.
- [x] TASK - Legacy OCR Results production launcher/route/session/components retired: backend `07f3fd47`; frontend `b2d8a59`.
- [x] TASK - Private snapshot ACL and retention/pin/purge lifecycle pushed: backend `478a51b2`; frontend `dfd4836`.
- [x] TASK - Follow-up lifecycle/ACL/recovery/retention/WebSocket hardening pushed: backend `09fa2824`; frontend `cb64ab3` / `6750c3b`.
- [x] TASK - Final Java compile after hardening passed: 561 main sources.
- [x] TASK - Clean frontend `npm run build` passed with existing warnings; no Page Mappings retention warning remains.
- [x] TASK - Exact 58-file build mirror pushed in `c3a86e6f`: `main.5501261a.js`, SHA-256 `4A2F8128F929BA7C6D85059742A366466F096982603A89141E6A6E3CAA87392B`.
- [x] TASK - Automation catalog regenerated after source commits: backend `09fa2824`, frontend `6750c3b`; retired OCR Results workspace entries are absent. Catalog generation executed no tests.
- [x] TASK - Frontend retention verification passed: 2 suites / 15 tests.
- [x] TASK - Backend snapshot/retention hardening matrix passed: 71 tests.
- [x] TASK - Frontend OCR reconnect lifecycle and stale OCR Config contract correction pushed: `449f9ea` / `a51e792`.
- [x] TASK - Final frontend affected-path JSDOM matrix passed: 8 suites / 44 tests.
- [x] TASK - Backend OCR authority, reconnect replay, fatal release, and alias outcome recovery pushed: `3e365b25`.
- [x] TASK - Final backend non-browser OCR/session/retirement matrix passed: 106 tests.
- [x] TASK - Explicit `mvn compile` passed: 562 main Java sources.
- [x] TASK - Exact 58-file build mirror pushed in `fb23c531`: `main.eb4f02b1.js`, SHA-256 `965E2A606FA0AA0A5744C0443BC1EF2FA97FDCE66EB83C4050BE0A5C82E83C56`.
- [x] TASK - Catalog `d76362ff` records 2,341 rows, 2,305 code cases, 13 focused Page Mappings OCR cases, and zero legacy OCR Results rows.
- [x] TASK - Exact BancaStato pre-migration backup created: 5,050,368 bytes, SHA-256 `6256AEDB77C489060CC22F7F00E465349008265C3330ABE4E0D513F0375D8AD3`.
- [x] TASK - Exactly the three registered snapshot migrations were applied in one SQLite transaction; post-state is 24 migration rows, expected table/indexes, `quick_check=ok`, and zero FK violations.
- [x] TASK - The later item-7 authorization superseded the earlier test pause; only local JSDOM/JVM/embedded-SQLite tests were run, with no browser or native OCR.
- [x] TASK - `mvn -DskipTests compile` passed; BancaStato acceptance PID `2852` ran the rebuilt `target/classes` code and served `main.eb4f02b1.js` / `main.df7752f0.css`. It is now stopped.
- [x] TASK - WebSocket heartbeat ingress fix `70d5d08d` is pushed; exact `ping` / `ping-*` control frames remain ignored while `pageMappings.*` reaches authorization and dispatch.
- [x] TASK - Live scan `16a2d848-6660-4f86-9786-5726d209d4e9` is the sole row: READY, 93 elements, final `pinned=0`, and its manifest SHA-256 matches the database.
- [x] TASK - The exact capture folder and five artifacts passed hash/content and protected Windows ACL verification; no SQLite sidecar, deletion/retention journal, staging folder, or temporary artifact remains.
- [x] TASK - Targeted live Page Mappings open, manager connection, bootstrap, 734,829-byte capture, cache state, four explicit pin round trips, and capture reload completed without a Page Mappings operation error.
- [x] TASK - Step 8 backend owner/browser/workspace retarget and mutation fencing pushed in `3721c049`; frame-safe top-document reuse pushed in `c594ba5b`.
- [x] TASK - Step 8 frontend message-generation and stale-owner retirement pushed in `17748b8`.
- [x] TASK - Duplicate Page Mappings Close control removed in frontend `e23c6d6e`; one guarded Close
  action remains.
- [x] TASK - Bounded open-Shadow-DOM fingerprint and top-document scanner scope pushed in `b9222d2f`; independent backend/frontend/fence/shadow reviews found no concrete blocker.
- [x] TASK - Clean `npm run build` passed; exact 58-file mirror pushed in `242095b2`; final `mvn -DskipTests compile` passed with 562 sources.
- [x] TASK - PID `8032` runs final `target/classes` on 60711/60712; HTTP assets match, `.10` logs have zero relevant errors, and read-only SQLite health is clean.
- [x] TASK - No Step 8 tests were created/run, no package/image was built, no Codex scan was triggered, and unrelated dirty state was preserved.
- [x] TASK - Page Mappings UI/help/pan and SCROLL PAGE frontend commits pushed: `716a686`, `7264fa7`, `f5cb822`, `7f04cbc`, `af6fffd`.
- [x] TASK - Owner-bound full-page traversal/default retention pushed in `260f2025`; adaptive render readiness pushed in `20c8a4bc`.
- [x] TASK - Exact 58-file frontend deployment mirror pushed in `6b2d1350`; production build passed with existing warnings.
- [x] TASK - The two exact Job 32 Lloyds rows/artifacts were removed from active storage after guarded backup/quarantine; current Job 32 Lloyds count is zero.
- [x] TASK - Final Java compile passed with 563 sources; no tests/package/image. PID `4428` serves matching assets on 54668/54669 and `.16` logs have zero relevant errors.
- [x] TASK - Per-Bot-Job SCROLL PAGES source commits are pushed: frontend `d5f6dad`, backend `805968ad`; strict owner/range/correlation and selected-movement semantics are implemented.
- [x] TASK - Clean frontend build is mirrored/pushed in `4e5813c0`; source resources and `target/classes` match across 58 files, 19 image assets match, and 24 stale target bundles are absent.
- [x] TASK - Java compile passed with 563 sources; no tests were run. PID `21796` serves matching current assets on 53734/53735 and six `.17` logs have zero relevant errors.
- [x] TASK - Authoritative capture total and Main Dashboard Page Mappings shell/header pushed in frontend `cf16efe`; exact frontend-only mirror pushed in backend `98ae848b`.
- [x] TASK - No tests/Maven command ran; PID `12944` serves matching `main.92c3e040.js` / `main.1ac3c57f.css` on 55720/55721 and six `.18` logs have zero errors.
- [x] TASK - Smoke Test browser-refresh source is pushed in frontend `7d5a157` and backend
  `aab60fca`; exact frontend deployment assets are pushed in `cd9bf34a`.
- [x] TASK - Frontend build and Java compile passed without tests; PID `17864` served matching
  `main.9a55ef9b.js` / `main.069de826.css` on 62590/62591 before a normal Main-window shutdown.
- [ ] TASK - Restart ARWeb, open Lloyds Bot Job 29's Playwright page, then click Refresh Web Page once
  from idle Smoke Test Integration and confirm it reloads the browser without reloading React.
- [x] TASK - Inactive selected Blocks remain visible/bypassed but are excluded from Integration start
  scope in frontend `124ecb6`; build/deployment `1883a1bc` is live without tests or Maven.
- [x] TASK - Unified Smoke Test Playwright healing and exact Smoke transport/status authority are
  pushed in backend `4c11186e`; focused Java verification passed 47/47 and Java compile passed.
- [x] TASK - Isolated Smoke Test Active/Input/Click row controls are pushed in frontend `f7f9aae`;
  the production build passed with existing warnings and no frontend suite ran.
- [x] TASK - Exact deployment `3fcee24c` is live on PID `31360`; 58 resource/target files match and
  HTTP serves the new JS/CSS hashes. Only two later user-page resource messages (404/400) are present;
  no JVM/SQLite/snapshot/WebSocket operation failure was found.
- [x] TASK - Automation catalog `2b11e657` records the final backend/frontend commits and unchanged
  2,341-row inventory; catalog generation executed no tests.
- [x] TASK - Exact Smoke execution-type authority is pushed in backend `a1d6bd3e`; focused Java
  verification passed 13/13 and final Java compilation passed with 564 sources.
- [x] TASK - Icon-only Active plus same-row INPUT/OUTPUT/CLICK and Test controls are pushed in
  frontend `3fde7be`; the production build passed without running a frontend test suite.
- [x] TASK - Deployment `74168d27` and catalog `3609803d` are pushed; PID `20668` serves matching
  `main.45672047.js` / `main.aacbfa82.css` bytes on 64433/64434.
- [x] TASK - Strict selected-Bot-Job START is pushed in backend `9fe40dbf`; Stop single-flight and
  message-generation recovery are pushed in frontend `882af61`. Focused checks passed Java 3/3 and
  frontend 8/8.
- [x] TASK - Deployment `4dade5a0` and catalog `ce41e3f7` are pushed; PID `29912` serves exact
  `main.6a91a10f.js` / `main.aacbfa82.css` on 59091/59092 and six new logs have zero strict matches.
- [ ] TASK - Retry the intended Lloyds Bot Job 29 run. The prior Step 0 error belongs to Bot Job 32
  inactive Block 204 and is not Lloyds plan evidence. START must open/settle Lloyds automatically,
  and Stop must restore controls without a Bot Job switch.
- [ ] TASK - User must run one fresh adaptive `SCROLL PAGE` Rescan with a selected non-default limit and compare the full-page visual result; Codex did not trigger this scan.
- [x] TASK - OCR Review rules, proposal acknowledgement/auto-selection, and canonical rollback are
  pushed in frontend `4ff3a99` / `3f67e5a`; exact deployment `64f499e1` and catalog `91bab2a3` are live.
- [ ] TASK - Reopen Page Mappings and visually confirm the saved Banca Stato alias remains displayed;
  use the restore icon only if rollback is intended, then verify Add/drag and separate Memory Apply.
- [ ] TASK - Copy `bfe4cf87` to the runtime/restart only after the active VPN production session is
  safe to interrupt, then click the complete Memory card with zero and nonzero selections and verify
  one Memory List opens/focuses without changing staged data.
- [x] TASK - Saved client-name priority and separated instruction guidance are pushed in frontend
  `57c3118`; focused verification passed 2 suites / 3 tests.
- [x] TASK - Exact deployment `e3469fb7` and catalog `3db5da92` are pushed; source resources and
  `target/classes` match across 58 files, with no Java/Maven execution.
- [ ] TASK - Start ARWeb and verify a rerun OCR Review keeps saved `Banca Stato` as Current and
  Proposed with Use unchecked; OCR text may differ but must not overwrite without explicit Apply.
- [ ] TASK - Package/image delivery, other-database/SQL Server rollout, and broader reconnect/takeover/retention-save-purge/OCR/Memory/multi-page acceptance remain open.

## 2. CLAUDE -> CODEX - Independent review requested

### Step 8 independent audit result

- [x] Backend retarget/mutation review found no remaining authorization, lock-order, terminal-
  settlement, or rescan-completion blocker after the final corrections.
- [x] Frontend review confirmed atomic message-buffer generation, effect ordering, stale-window
  state removal, mutation-fence reload behavior, and compatibility with existing typed mocks.
- [x] Shadow review confirmed deterministic bounded traversal, non-shadow hash compatibility,
  hash-only persistence, explicit root/slot topology, and fail-closed omission of unrepresentable
  shadow-scoped elements. Full shadow-element locator/geometry support remains a future feature.
- [x] Independent adaptive-scroll review confirmed bounded paint waits, class/style mutation coverage,
  finite near-viewport animation gating, image decode/font readiness, deadline-bounded restore, and
  fail-closed timeout behavior. No concrete source blocker remains.
- [ ] Validate the Smoke Test refresh contract: exact current transport/binding/workspace/owner/graph
  authorization, browser-owner exclusion, Bot Job mutation fencing during Playwright reload/settle,
  correlated response handling, and the idle-only toolbar gate immediately before Stop.
- [ ] Validate `124ecb6`: partial/all selections must send selected active IDs only, inactive blocks
  must remain visible and bypassed locally, and inactive-only selection must never send a request or
  leave the Integration hook in STARTING.
- [ ] Validate `4c11186e` / `f7f9aae`: Smoke manual actions must require the exact current transport,
  owner/workspace/graph generation, reuse the same ordered locator-healing path as Integration, run
  one physical action only, keep ambiguity fail-closed, and synchronize Active state with Bot Job.
- [ ] Validate `a1d6bd3e` / `3fde7be`: execution-type changes must require the exact current Smoke
  binding, persist only `instruction.actions`, synchronize both Bot Job and Smoke views, and keep
  power/type/Input/Click controls on one accessible row without broadening mutation authority.
- [ ] Validate `9fe40dbf` / `882af61`: START must hold the exact workspace generation through strict
  selected-URL navigation and settlement; Stop/Finish must remain single-flight when canceling a
  pending step, consume the one terminal acknowledgement after a message-buffer reset, and restore
  controls without switching Bot Jobs.
- [ ] Validate exact Home Banking/Bot Job browser-storage isolation, post-authorization explicit
  `1..40` validation, request/status `scrollPage` + `scrollPages` correlation, and the rule that N
  counts only confirmed downward viewport movements.
- [ ] Validate one user-driven post-`20c8a4bc` SCROLL PAGE Rescan. Do not infer visual completeness
  from compile, restart, or the pre-adaptive READY captures.

- [ ] TASK - Review frontend OCR reconnect commit `449f9ea`; confirm read-only Review is discarded,
  mutating Apply resends the byte-identical request before bootstrap, and timeout/retarget/malformed
  responses cannot clear the reload gate without correlated bootstrap plus verified capture.
- [ ] TASK - Review backend OCR recovery `3e365b25`; confirm pre-admission exact-transport authority,
  immutable owner-derived ledger keys, same-payload subscriber attachment, conflicting-payload drop,
  success-only Apply replay, per-recipient terminal authorization, and bounded collections.
- [ ] TASK - Confirm alias commit/close ambiguity cannot run rollback or `setAutoCommit(true)` after
  an unknown commit attempt and always reaches React as correlated `reloadRequired=true`.
- [ ] TASK - Confirm abrupt `Error` paths detach the active OCR lane before allocation/copy work and
  an outer read-connection close cannot replace an already-built mutation response.
- [ ] TASK - Validate frontend `3f67e5a`: edited Proposed names auto-select Use, the same Review does
  not restore stale OCR text after success, and rollback sends one exact null alias while preserving
  owner/revision/reconnect safety and Memory projection updates.
- [ ] TASK - Validate frontend `0cd8bed`: the full Memory card sends one owner-bound open request for
  mouse/Enter/Space, permits the empty snapshot, preserves Add/drop synchronization, blocks duplicate
  pending clicks, and keeps the cyan animation accessible under reduced-motion preferences.
- [ ] TASK - Validate frontend `57c3118`: a nonblank saved client alias must remain the default
  Proposed value and unselected across a new OCR Review request, while rows without an alias retain
  OCR proposal/selection behavior. Confirm the instruction tab never presents aliases as automatic
  cross-page identity or a replacement for exact owner/revision/locator checks.
- [ ] TASK - Review snapshot verification commit `380841af`; confirm FAILED creation recovery honors
  NOT NULL migration columns and extended-length ACL handling preserves the private/no-follow model.
- [ ] TASK - Review assets/catalog `fb23c531` / `d76362ff`; confirm the manifest selects
  `main.eb4f02b1.js`, source/backend manifests match, and retired OCR Results entries remain absent.
- [ ] TASK - Validate `70d5d08d`: both raw and decoded ingress paths must preserve exact plain and
  encoded heartbeat frames without classifying any `pageMappings.*` operation as a ping.
- [ ] TASK - Validate the BancaStato backup/migrations plus the targeted READY-row, manifest,
  artifact, ACL, current-asset, and Page Mappings open/bootstrap/capture/cache/pin evidence,
  including the BancaStato-only rollout boundary.
- [ ] TASK - Confirm package/image delivery, orphan inventory/reconciliation, other-database/SQL
  Server rollout, and broader reconnect/takeover/retarget/delete/same-ID/Use Existing/Rescan/
  retention-save-purge/OCR/Memory/multi-page acceptance remain open and are not inferred from this
  targeted live path.
- [ ] TASK - Record any concrete blocker with producer, consumer, exact interleaving, and smallest
  authoritative fix. Do not mark deployment or live behavior complete from source/build evidence.

### Bot Job instruction-row selection and safe deletion - 2026-08-11

- [x] Frontend `4319195` adds the requested Bot Job row checkbox after Active and a dynamic glowing red block-header trash/count after collapse. First-row selection offers first-only/all; remaining rows can be adjusted independently.
- [x] Backend `4ffd41d3` makes variables independent of instruction/block deletion by detaching deleted producers instead of deleting definitions; surviving parent repairs clear both parent fields.
- [x] Focused verification passed: frontend 8/8, backend 4/4, Java compilation, and the production frontend build. The initial TypeScript narrowing issue and stale legacy backend test fixture were corrected before the final passing runs.
- [x] Deployment `af56bd24` contains 58 exact files in resources and `target/classes`; catalog `7a9e1be5` is pushed. Current assets are `main.7984b953.js` / `main.af1d2d62.css`.
- [ ] ARWeb was not running, so live visual/deletion acceptance remains open. Clone Job legacy `variable_id` and the ExcelWrite instruction-level redesign are the next separate items.
