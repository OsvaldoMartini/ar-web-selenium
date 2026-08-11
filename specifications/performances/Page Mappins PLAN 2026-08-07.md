# Page Mappings Plan — 2026-08-07

Yes—I understand the objective. You want a durable **Page Mappings** workspace that reuses previous scans, preserves historical screenshots, safely handles renamed Web Elements, and lets the client drag or add selected elements into Memory List without repeatedly running Page Scanner.

## Investigation verdict

Do not directly rename the current OCR Results implementation. Create an isolated Page Mappings workspace first, then expose the existing OCR functionality as an **OCR Review** mode inside it.

| Current behavior | Finding |
|---|---|
| Scan repository | The real table is `scanned_element`—singular. It is a cumulative locator registry, not scan history. |
| Existing history | Today’s read-only database check found 1,234 rows, 3 Bot Jobs, 8 page keys, and 20,939 scan observations. |
| Historical scans | Cannot be reconstructed. Rows only have `first_scanned_at`, `last_scanned_at`, and `scan_count`; there is no scan ID or exact membership. |
| Screenshots | Files such as `page-BJ.png` and related JSON are globally overwritten after each scan. |
| OCR Results | It selects the newest global diagnostic files by timestamp, not by Bot Job/page. A simple rename could display another Bot Job’s scan. |
| GridItem rename | Correctly writes only `instruction.client_named`; canonical name and locators remain unchanged. |
| Page Scanner rename | **P0 complete:** typed `pageScanner.element.rename` persistence updates exactly one owner/page-scoped `scanned_element.client_named` row and returns an authoritative acknowledgement. |
| Memory List rename | **P0 complete for Page Scanner-owned rows:** an acknowledged alias change updates the already-staged payload through the existing `memoryList.sync` projection. A standalone Memory List rename editor remains outside P0. |
| Rescan | **P0 complete:** existing registry aliases are authoritative, survive rescans, and are rehydrated into the outgoing scanner DTO. |
| Execution fallback | Uses the current page’s repository, but starts from canonical `instruction.name`; the instruction’s own `clientNamed` is not an alternate lookup name. |
| Live name lookup | Does not currently exist. The fallback finds a registry row and retries its saved locator. |
| OUTPUT recovery | **P0 complete:** typed Playwright text results preserve legitimate empty text while missing elements reach page-scoped self-healing. |

Relevant implementations include [ScannedElementRepository.java](/D:/Projects/AllinWeb/ar-web-selenium/src/main/java/com/allinweb/ch/db/ScannedElementRepository.java), [PageMappingsOcrReviewService.java](/D:/Projects/AllinWeb/ar-web-selenium/src/main/java/com/allinweb/ch/facade/PageMappingsOcrReviewService.java), [PageMappingsWorkspaceService.java](/D:/Projects/AllinWeb/ar-web-selenium/src/main/java/com/allinweb/ch/socket/PageMappingsWorkspaceService.java), and [PageMappingsPage.tsx](/D:/Projects/AllinWeb/abr-react-ts-grid/src/components/PageMappingsPage.tsx).

## Recommended architecture

```text
Page Scanner
   ├── update current locator registry → scanned_element
   └── create immutable capture → page_scan_snapshot + Scanned/... files
                                            │
                                            ▼
                                  Page Mappings workspace
                                  ├── history/image/overlays
                                  ├── rename/retest
                                  └── + or drag/drop
                                            │
                                            ▼
                                      Memory List
                                            │ explicit Apply
                                            ▼
                                      Bot Job instruction
```

### Minimal storage design

Keep the number of tables low:

| Responsibility | Source of truth |
|---|---|
| Latest reusable locators | Existing `scanned_element` |
| Scan metadata/history | One new `page_scan_snapshot` table |
| Screenshot and exact elements from that scan | Immutable files |
| Bot Job user-defined label | `instruction.client_named` |
| Temporary selected elements | Memory List |

Do not store screenshots as database BLOBs and do not create another element table.

Suggested storage:

```text
<PATH_DB>/page_diagnostics/Scanned/
  org-{organizationId}/
    bot-job-{botJobId}/
      {safePageHash}/
        {UTC-timestamp}-{scanId}/
          manifest.json
          screenshot.png
          thumbnail.png
          elements.json
          rects.json
          meta.json
          ocr.json                 optional
          annotated.png           optional
```

`page_scan_snapshot` should contain only:

- `scan_id`
- organization, Bot Job and Home URL IDs
- `page_key`, page URL and title
- UTC capture timestamp
- structural `view_fingerprint`
- element count
- relative artifact path
- screenshot/manifest checksum
- `STAGED`, `READY`, or `FAILED`
- optional `pinned`

`elements.json` preserves the exact historical membership, avoiding a second history table.

## Page Mappings design

The detached page should have two primary areas:

1. **Capture explorer**

   - Page selector grouped by URL/page key.
   - Timestamp history.
   - Screenshot with clickable Web Element rectangles.
   - Searchable and sortable element grid.
   - Selection in the grid highlights the screenshot, and vice versa.
   - Statuses: Current, Changed, Stale, Ambiguous, In Memory, In Bot Job.

2. **Selected for Bot Job**

   - Target Block.
   - Ordered draggable elements.
   - `+` and drag/drop use the same idempotent function.
   - Open/focus Memory List automatically.
   - Explicit Apply remains responsible for creating instructions.

The Memory List payload should carry identifiers:

```text
scannedElementId
captureId
pageKey
expectedLastScannedAt
```

The backend must reload the authoritative row. React should not be trusted to submit arbitrary historical locators.

Add **Mappings** launchers to:

- Bot Job page: opens the latest capture/page selector.
- Page Scanner: opens with its current server-owned page selected.

## Avoiding unnecessary scans

Do not skip Page Scanner merely because the URL exists.

Use this inexpensive check:

1. Read the live normalized URL.
2. Calculate a lightweight server-side DOM fingerprint.
3. Compare `organization + Bot Job + pageKey + fingerprint` with the latest READY capture.
4. If equal: show **Saved mapping current — Use existing**.
5. If different: show **Page changed — Rescan recommended**.
6. If no active browser exists: historical mapping remains viewable, but live tests are disabled.

This handles SPAs and banking workflows where the same URL can show different screens.

## Correct naming rules

| Field | Meaning |
|---|---|
| `name` / `defined_name` | Immutable canonical machine name |
| `client_named` | User-facing alias |
| XPath/CSS/attributes/hash | Element identity and execution |
| Excel data key | Currently alias-first, with canonical fallback |

Required behavior:

- GridItem rename updates `instruction.client_named`.
- Page Mappings/Page Scanner rename updates `scanned_element.client_named`.
- Memory List draft rename updates the owning staged payload.
- A rescan must preserve a nonblank user alias, just as custom XPath is preserved.
- Blank or canonical-equivalent rename clears `client_named`.
- Duplicate aliases may exist, but execution must never select the first duplicate silently.
- Renaming a repository element should not automatically rename every existing instruction unless the client explicitly chooses **Apply label to linked instructions**.

One important compatibility point: `clientNamed` currently affects Excel column lookup through `displayKey()`. That behavior must be regression-tested before changing rename semantics.

## Safe execution fallback

The production-safe lookup order should be:

1. Authored custom XPath, XPath, CSS and stable references.
2. Current-page `scanned_element` locators and stable attributes.
3. Unique visible/actionable live DOM match using canonical name.
4. Unique live DOM match using `client_named`.
5. Coordinates only as the final fallback.

Every name-based lookup must:

- be scoped to the active Bot Job and current Playwright page;
- validate frame, shadow root, tag and expected action;
- require exactly one candidate;
- refuse ambiguity visibly;
- resolve once and execute the physical click/input/read once.

## Implementation checkpoints

1. **P0 — Naming and execution safety — COMPLETE IN SOURCE**

   - [x] Preserve scanner aliases during rescans.
   - [x] Add typed, owner-scoped rename acknowledgements with `affectedRows == 1`.
   - [x] Synchronize staged Memory List aliases.
   - [x] Fix OUTPUT missing-versus-empty handling.
   - [x] Add rename and duplicate-alias regression tests.

2. **P1 — Immutable scan storage**

   - [x] Add `page_scan_snapshot`.
   - [x] Implement atomic owner-scoped snapshot folder creation.
   - [x] Write snapshot-owned screenshot/rectangle artifacts directly; never copy mutable
     `page-BJ.*` files into an immutable capture.
   - [x] Persist exact `elements.json`, metadata, manifest checksums, and READY/FAILED status for every scan, including empty scans.

3. **P2 — Isolated Page Mappings workspace**

   - [x] New `PageMappingsPage`, dedicated module stylesheet, detached route/session, and WebSocket bootstrap.
   - [x] Load integrity-verified images, rectangles, metadata, and exact element payloads for the selected READY capture.
   - [x] Preserve the old OCR route temporarily while OCR Review moves into Page Mappings, then retire it after the new source path is complete.

4. **P3 — Mappings launchers and explorer**

   - [x] Bot Job and Page Scanner launch buttons.
   - [x] Capture history and selected-capture statuses/metadata.
   - [x] Image overlays with selectable immutable element rectangles.
   - [x] Immutable artifact loading and frontend element search.

5. **P4 — Memory List integration**

   - [x] Add an explicitly authorized `PAGE_MAPPINGS` source.
   - [x] Implement `+`, drag/drop, target Block and idempotent staging.
   - [x] Reuse the existing Memory List WebSocket and transactional Apply path.

6. **P5 — Cache-first scanning — COMPLETE IN SOURCE**

   - [x] Current-page fingerprint with fail-closed unsupported/truncated states.
   - [x] Use Existing / Rescan states with verified immutable-capture reuse.
   - [x] Stale and changed-page diagnostics with bounded request and timeout lifecycle.

7. **P6 — Safe runtime healing — COMPLETE IN SOURCE**

   - [x] Owner/page-scoped canonical and alias live-page lookup.
   - [x] Unique visible/actionable candidate and page/frame/tag/action enforcement.
   - [x] One-action guarantee, pinned Playwright target, and structured safe diagnostics.

8. **P7 — OCR Review consolidation — COMPLETE IN SOURCE**

   - [x] Add isolated public `PageMappingsOcrReview*` components and reduced typed contracts.
   - [x] Move selected immutable-capture OCR comparison and atomic alias Apply into Page Mappings.
   - [x] Replace the legacy `OCR Results` launcher in `GridItemScann` with the existing MAPPINGS path.
   - [x] Retire the old `ocr-results-*` production route/session/components and Results-only backend operations.

Because screenshots may contain banking data:

- [x] Keep capture access owner-scoped and redact URL/query values in persisted/displayed metadata.
- [x] Apply explicit private Windows ACLs to capture folders in the snapshot write/read/delete/restore lifecycle.
- [x] Implement configured retention, pin/unpin, bounded purge, journal recovery, and Page Mappings controls.

## P0 delivery evidence

- Frontend focused tests: 2 suites, 10 tests, 0 failures.
- Java focused P0 tests: 41 tests, 0 failures or errors.
- Duplicate-alias execution-confidence regression: 9 tests, 0 failures or errors.
- Frontend production build passed with pre-existing repository warnings.
- Generated resource mirror: 58 source files, 58 backend files, zero missing, extra, or SHA-256 differences.
- Frontend commit pushed: `eb6181b`.
- Backend alias persistence commit pushed: `8db3f813`.
- OUTPUT semantics commit pushed: `ebb4da75`.
- Deployment-assets commit pushed: `2f18f48d`.
- Duplicate-alias regression commit pushed: `8718ed63`.
- No migration was required for P0. The backend was compiled/tested but was not packaged, restarted, or live-verified.
- This paragraph records the earlier P0 checkpoint. Later checkpoints below deliver P1-P7 and the
  subsequent legacy OCR retirement plus snapshot privacy/retention work.

## P2 delivery checkpoint - 2026-08-07

- Added detached `pageMappingsManager` routing with an isolated `PageMappingsPage.module.scss`.
- Added the read-only `pageMappings.bootstrap` WebSocket contract. It returns only owner-scoped
  snapshot metadata from `page_scan_snapshot`; it does not expose arbitrary filesystem paths or
  mutate scanner state.
- The page provides capture history, selected-capture metadata, status, element count, artifact
  path, and manifest checksum. Image/element artifact loading, OCR compatibility, launch buttons,
  and Memory List actions remain deliberately deferred to P4.
- Frontend build passed with existing lint warnings; bundle was mirrored into backend source resources.
- Frontend commit: `f4f40a3`. Backend code commit: `80116a01`.
- Backend compile passed after the P2 handler change. Packaging, restart, and live acceptance remain open.

## P3 delivery checkpoint - 2026-08-07

- Bot Job Details now exposes a MAPPINGS launcher backed by `SHOW_PAGE_MAPPINGS` and the existing
  detached-window focus mechanism.
- Page Scanner exposes the same launcher and uses the reduced `pageMappings.open` request; the
  detached Page Mappings session accepts only its bootstrap/read operations.
- Capture history/status metadata, immutable artifact loading, frontend element search, and selectable
  rectangle overlays are now visible in the isolated page. Memory List actions remain deferred to P4.

- [x] TASK - Page Mappings capture image and element overlays delivered in frontend commit `0d5b5a0`.
- Frontend launcher commit: `2a6ba3e`. Backend launcher commit: `38a17612`.
- Bundle mirror commit: `4a2ec036`; bundle is `main.727f368f.js`.
- Artifact/search frontend commit: `3a86be9`; backend artifact contract commit: `c2bff462`.
- Latest bundle mirror commit: `cace5e6b`; bundle is `main.8b8a7868.js`.
- Frontend build and backend compile passed with existing warnings. Packaging/restart/live launcher verification remain open.

## P1 delivery evidence - 2026-08-07

- Migration `2026-08-07__page_scan_snapshot` creates one owner-scoped scan-history table with page and capture indexes.
- `PageScanSnapshotStore` writes a UUID-named staging directory, atomically moves it into
  `page_diagnostics/Scanned/org-{homeBankingId}/bot-job-{botJobId}/{pageKey}/`, and records the
  relative artifact path, manifest SHA-256, element count, and status.
- Exact scan membership is preserved in `elements.json`; metadata and checksums are recorded in
  `meta.json` and `manifest.json`. Current source writes scan-owned screenshot and rectangle files
  directly and never copies mutable `page-BJ*` artifacts into an immutable capture.
- Empty scans are captured as valid READY snapshots. A failed artifact write is recorded as FAILED
  and does not silently masquerade as a successful capture.
- Focused Java tests: 2 tests, 0 failures. Backend compile: 538 main sources, 309 test sources.
- Packaging, restart, UI, and live acceptance remain open; no Page Mappings UI was changed.

## P0.5 Web Element execution-type checkpoint

The Page Scanner and persisted GridItem now expose the same controlled `INPUT -> OUTPUT -> CLICK`
choice without changing locator identity:

- Page Scanner stores a transient `executionTypeOverride` in its source, grouped, and staged Memory
  List projections.
- Pane-free apply and the legacy Save / Send All / Update All preparations use one shared Java
  mapper and preserve the physical DOM tag.
- GridItem persists only `instruction.actions` through the reduced
  `gridItem.webElementType.update` contract with owner, workspace, graph-version, revision, expected
  value, exact-one write, idempotency, and authoritative reload checks.
- Relationships, variable slots, parents, locators, references, coordinates, and `scanned_element`
  identity remain unchanged.

Delivery evidence:

- Frontend focused tests: 4 suites, 19 tests, 0 failures.
- Java focused tests: 24 tests, 0 failures or errors.
- Frontend production build: passed with existing repository warnings.
- Generated bundle: `main.83054b52.js`; resource mirror 58/58 with zero hash differences.
- Frontend commit pushed: `a289663`.
- Backend source/test commit pushed: `99ad9c2f`.
- Backend deployment-assets commit pushed: `46dd420e`.
- Packaging, restart, and live acceptance remain open. P5, P6, P7, and the subsequent legacy OCR
  retirement were delivered in later 2026-08-08 checkpoints.

## Page Mappings review remediation checkpoint — 2026-08-08

The 12 findings recorded in `Page Mappings REVIEW FIXES 2026-08-07.md` are fixed in source and
pushed. This includes fixed-presentation registration, server-owned owner binding, authoritative
retargeting, scan-owned immutable artifacts, verified capture geometry, selected-capture response
correlation, authoritative Apply reload, SQL Server-safe bounded schema repair, deletion lifecycle,
URL redaction, finalized-artifact cleanup, and FAILED-history recording.

### Frontend risks currently known

| Severity | Status | Risk |
|---|---|---|
| Critical | Fixed | Missing-epoch Memory OPEN and SYNC failures settle through exact typed request correlation; any authority fields supplied on a failure must match, and producer request IDs include a monotonic sequence. |
| Critical | Fixed | Detached `MemoryList` commands, timers, dialogs, status, and drag state are bound to the complete owner/workspace generation and retired synchronously across retarget. Backend responses remain on the captured exact requester transport. |
| High | Deployment gate | New backend static-source epoch enforcement and the matching frontend must deploy together. The build is mirrored in Git but is not packaged/restarted. |
| Medium | Open verification | Detached-window reconnect/retarget/delete/same-ID reuse behavior still needs live desktop acceptance. |
| Medium | Open verification | The affected-path frontend suite passed 25/25 and the production build passed. The complete frontend suite was not run; eight stale `GridItemComp.memoryParity` expectations remain in a nearby selected run. |
| Low | Existing | Production build warnings and bundle-size warnings remain. |

Checkpoint commits:

- Backend lifecycle: `ea68268e`; isolated failure contract: `209d24d7`; exact requester
  response routing: `b147de41`.
- Frontend workspace generation: `7774aeb`; request correlation: `ce6a56f`; detached command and
  drag generation binding: `fb87aa0`.
- Latest mirrored frontend assets: `9a9dc6db`, `main.16e24f7b.js`, SHA-256
  `ED8C10BCA7B21661C19EA67613DE04884EC27CA5920C06D6A65A1E469A112004`.
- Backend selected regression suite before the final failure correction: 206/206 passed.
- Frontend affected-path suite: 25/25 passed. Frontend production build passed with existing
  warnings. Java compilation was not rerun for the requester-routing checkpoint under the repository
  no-Maven directive.

Operational gates remain separate: migration application, real SQL Server inspection, backend
package/restart, deployed health, and live behavior are not complete.

## P5-P7 delivery checkpoint — 2026-08-08

### P5 — cache-first scanning

- Backend commit `c8e722cd` adds privacy-safe current-page fingerprints, verified READY-capture
  reuse, strict scan persistence, bounded in-flight gates, and current/changed/stale diagnostics.
- Frontend commit `14b7832` adds Use Existing / Rescan controls and correlated timeout/disconnect
  handling inside Page Mappings.
- Backend correction `823ab2dc` binds the fingerprint to the actual asynchronous scan/capture and
  fails closed on unsupported or changing pages.

### P6 — safe runtime healing

- Backend commit `668a7acb` adds an isolated owner/Bot Job/page registry preparation service and a
  serialized Playwright runtime executor for Test Run and Smoke Integration.
- Resolution enforces current-page identity, unique visible/actionable candidates, physical tag and
  action compatibility, pinned DOM handles, one physical operation, and structured non-secret
  diagnostics. Existing Grid/manual test APIs remain unchanged.

### P7 — Page Mappings OCR Review and legacy retirement

- Backend commit `89bbce24` adds selected immutable-capture OCR review, full capture checksum and
  owner/revision validation, an isolated OCR worker, retarget-safe response delivery, and
  SERIALIZABLE all-or-nothing `scanned_element.client_named` Apply.
- Frontend commit `4dc51aa` adds isolated `PageMappingsOcrReview*` types, grid, panel, styles, exact
  success correlation, safe failure settlement, visible-row-only Apply, and authoritative updates
  to loaded capture and staged Memory List projections.
- The OCR Review core commits did not modify either Grid. The later isolated frontend retirement
  commit `b2d8a59` removed only the legacy OCR Results producer/handler/button from `GridItemScann`;
  unrelated concurrent Grid changes were left unstaged. Backend retirement is `07f3fd47`.
- The `ocr-results-*` production route/session/components, Results-only socket operations, and dead
  legacy OCR service are retired. OCR Config and Page Mappings OCR Review remain.

### Snapshot privacy and retention checkpoint

- Backend commit `478a51b2` adds protected Windows ACLs (process user, LocalSystem, and Administrators),
  POSIX-private permissions, no-link path validation, and privacy checks across snapshot writes,
  reads, deletion/retention journals, restore, and startup reconciliation.
- The same commit adds owner-scoped capture operations governed by one system-wide retention
  policy, pin/unpin, bounded purge batches, shared snapshot mutation locking, request idempotency,
  ambiguous-commit journal recovery, and SQLite-compatible JDBC behavior. Cleanup runs after
  successful scans or by explicit Purge Eligible; it is not a time scheduler.
- Frontend commit `dfd4836` adds the isolated Page Mappings retention panel and correlated pin,
  policy-save, purge, reload-required, and missing-storage states.
- Backend hardening commit `09fa2824` recursively hardens every no-follow descendant during
  controlled startup/recovery while read paths reject unexpected ACLs. It adds fail-closed storage
  health, PATH_DB/database-generation invalidation, STAGED-creation cleanup, exact owner/snapshot-
  generation deletion journals, unified lifecycle reconciliation, missing-artifact progress,
  typed stale/unknown pin outcomes, expected-policy purge, binding-linearized mutations, and
  authorized cached/reconnect response delivery.
- Frontend commits `cb64ab3` and `6750c3b` enforce the reload-required latch across timeout,
  disconnect, malformed/stale success, and backend ambiguity; assert the displayed policy on purge;
  remove hidden Memory pending coupling; add permanent-purge confirmation; and reset policy drafts
  on every authoritative bootstrap revision. The UI explicitly distinguishes the system-wide
  policy from Bot Job-scoped counts and purge.
- No migration or DDL was applied. By explicit user direction, migration activation and other-
  database rollout remain parked for a later authorized maintenance window.

### Verification and separate completion gates

- Final `mvn compile`: BUILD SUCCESS, 561 main Java sources, 2026-08-08. Only the existing Lombok
  builder and inexact-varargs warnings remained.
- A clean worktree at frontend `6750c3b` excluded the unrelated dirty Grid change.
  `npm run build` passed with existing repository warnings. The 58-file mirror was exact before
  cleanup and was pushed in backend `c3a86e6f`.
- Current entrypoints: `main.5501261a.js` (SHA-256
  `4A2F8128F929BA7C6D85059742A366466F096982603A89141E6A6E3CAA87392B`) and
  `main.53308f43.css` (SHA-256
  `FA404C80C92070EC95FC3D66B0ED3C3B279DBC45D6BF27C9B4CAD247DF8CA822`).
- `automation-tests.json` was regenerated after the source commits and now records backend
  `09fa2824` plus frontend `6750c3b`; retired OCR Results workspace entries are absent. Catalog
  generation did not execute tests.
- Per explicit instruction, no tests were created or run. The backend was not packaged or restarted.
- Migration application remains intentionally deferred. Real database inspection, live Windows ACL
  inspection, running-service health, and live desktop/browser behavior remain open gates.

## Item-7 verification and OCR recovery checkpoint - 2026-08-10

The later user authorization to continue item 7 superseded the earlier test pause for this
checkpoint. Verification was kept local and deterministic: no Playwright browser, live service,
native OCR bridge, migration, or production database was started.

### Defects found and fixed

- Snapshot verification exposed an invalid STAGED-recovery write of `NULL` into migration-defined
  NOT NULL artifact columns. Recovery now uses the established FAILED empty-string representation.
- Windows ACL verification exposed a valid capture path beyond the legacy Win32 path limit. Private
  ACL reads and writes now use extended-length paths while retaining no-follow owner checks.
- A pending OCR alias Apply was previously discarded by React on disconnect, while Java silently
  dropped its duplicate and bound terminal delivery to the replaced transport. The database could
  commit with no authoritative result reaching the new window.
- React now retains the exact serialized Apply body and request ID, resends it before bootstrap on
  reconnect, and blocks further OCR mutation after an unknown outcome until a correlated bootstrap
  plus integrity-verified capture response succeeds. Read-only OCR Review remains safely rerunnable.
- Java now authorizes the exact Page Mappings transport and full owner contract before the OCR
  ledger, attaches byte-identical reconnect subscribers, drops conflicting payloads, caches only
  successful Apply responses, and reauthorizes every terminal recipient.
- Alias commit acknowledgement and connection-close ambiguity now return a typed correlated
  `reloadRequired=true` failure. No rollback or connection-state mutation runs after an ambiguous
  commit attempt. An outer read-connection close can no longer replace an already-built success.
- Abrupt worker errors detach the active ledger state before allocation/copy work, send best-effort
  fail-closed terminal responses, and cannot permanently leave OCR requests BUSY.
- The broader frontend run found one stale OCR Config test that contradicted the later desktop-shell
  design. The test now correctly records the header as the containing workspace frame's drag handle;
  production presentation was not changed.

### Commits and verification

- Frontend retention test checkpoint: `f8dd5aa`.
- Backend snapshot/retention tests and hardening: `380841af`.
- Frontend OCR reconnect lifecycle: `449f9ea`.
- Frontend OCR Config contract correction: `a51e792`.
- Backend OCR authority, ledger, and transaction recovery: `3e365b25`.
- Mirrored production frontend assets: `fb23c531`.
- Regenerated automation catalog: `d76362ff`.
- Frontend retention-focused verification: 2 suites / 15 tests passed.
- Final frontend affected-path verification: 8 suites / 44 tests passed.
- Backend snapshot/retention hardening matrix: 71 tests passed.
- Final backend non-browser OCR/session/retirement matrix: 106 tests passed.
- `npm run build`: passed with existing repository lint/dependency/bundle-size warnings.
- `mvn compile`: BUILD SUCCESS, 562 main Java sources; only the existing Lombok builder and
  inexact-varargs warnings remain.
- Exact frontend mirror: 58 source files / 58 backend files / zero manifest differences.
  Entrypoints are `main.eb4f02b1.js` (SHA-256
  `965E2A606FA0AA0A5744C0443BC1EF2FA97FDCE66EB83C4050BE0A5C82E83C56`) and
  `main.df7752f0.css` (SHA-256
  `912DEAE51E4B60DE97A1BEAEB74F6AAE6719EFBB54ED8D28388E77A1518AE70C`).
- Catalog: 2,341 rows, 2,305 code cases, 19,452 generated API requests, 13 focused Page Mappings
  OCR cases, and zero retired OCR Results rows. Source metadata is backend `3e365b25` / frontend
  `a51e792`.

### Operational gates still open

- No migration or DDL was created or applied.
- No backend package was built and no service was restarted.
- No running image/service freshness, real database, live Windows ACL, desktop-window, or browser
  behavior was verified.

## BancaStato SQLite activation - 2026-08-10

This checkpoint supersedes the earlier deferred-migration and restart status only for
`D:\Projects\ARWebBancaStato\ARWeb\database.db`. The earlier text remains the historical state when
written. No other installation or SQL Server database was changed.

### Incident and migration

- The live Page Scanner updated its locator registry and saved both 93-entry diagnostic JSON files,
  then immutable snapshot persistence failed with `[SQLITE_ERROR] no such table:
  page_scan_snapshot`. The optional legacy scan completed, but there was no new immutable capture.
- ARWeb was quiesced before the database write. The exact pre-migration backup is
  `D:\Projects\ARWebBancaStato\ARWeb\Backup-CODEX-2026-08-10-page-scan-snapshot\database.db`
  (5,050,368 bytes; SHA-256
  `6256AEDB77C489060CC22F7F00E465349008265C3330ABE4E0D513F0375D8AD3`).
- One SQLite transaction applied and recorded exactly these registered migrations, in order:
  `2026-08-07__page_scan_snapshot`,
  `2026-08-08__page_scan_snapshot_sqlserver_key_repair`, and
  `2026-08-08__page_scan_snapshot_view_fingerprint`.

### Verification and remaining gates

- Post-migration inspection found 24 migration rows; `quick_check=ok`; zero foreign-key violations;
  zero snapshot rows; and no WAL, SHM, or journal sidecar. The table has
  `scan_id, home_banking_id, bot_job_id, home_url_id, page_key, page_url, captured_at,
  element_count, artifact_path, manifest_sha256, status, pinned, view_fingerprint`, indexes
  `idx_page_scan_snapshot_owner` and `idx_page_scan_snapshot_page`, plus its primary-key autoindex.
- `mvn -DskipTests compile` passed with 562 Java sources, copied 273 resources, and retained only the
  existing Lombok-builder and inexact-varargs warnings. Source and `target/classes` hashes match for
  `main.eb4f02b1.js` and `main.df7752f0.css`.
- ARWeb restarted from `target/classes` outside the IntelliJ debugger as PID `33084`, using the exact
  BancaStato config. It is responsive on dynamic ports `127.0.0.1:50612` and `127.0.0.1:50613`;
  HTTP returns 200 for the current JS/CSS assets; six new `.2` logs contain zero error/exception
  matches; and a concurrent read-only database check remains healthy.
- No package or image was built. A fresh scan/READY row, live snapshot ACL verification, orphan
  inventory/reconciliation, other-database and SQL Server rollout, and full desktop/browser
  acceptance remain open. The pre-existing orphan capture folder was intentionally left untouched.

## BancaStato targeted Page Mappings acceptance - 2026-08-10

This checkpoint supersedes the activation section's open fresh-scan and capture-ACL gates only for
the targeted BancaStato Bot Job 29 path. It does not change any other installation or SQL Server
database.

- A live Page Scanner run created the sole snapshot row
  `16a2d848-6660-4f86-9786-5726d209d4e9` for Home Banking `13`, Bot Job `29`, and Home URL `15`:
  93 elements, `READY`, captured `2026-08-10T12:12:10.059962200Z`, final `pinned=0`, manifest
  SHA-256 `e5e099c71f9d3099943121cd285627da991bd6d5a00f7c117bc90bd18c305bcd`.
- The capture contains exactly the five expected files. Manifest/payload hashes and all owner,
  capture, geometry, and element counts agree. The capture tree and files have protected Windows
  ACLs limited to the process user, SYSTEM, and Administrators. SQLite remains healthy with no
  sidecar, deletion/retention journal, staging folder, or temporary snapshot artifact.
- The first Mappings clicks exposed an ingress bug: `contains("ping")` matched the lowercase
  `ping` inside every `pageMappings.*` operation. Backend commit `70d5d08d` narrows both raw and
  decoded filters to exact `ping` / `ping-*` control frames, preserving the actual heartbeat
  contracts while allowing Page Mappings through authorization and dispatch.
- After compile and restart from `target/classes`, PID `2852` opened the native Page Mappings
  window. Live `.4` logs record `pageMappings.openResponse`, `pageMappingsManager` connect,
  bootstrap, a 734,829-byte capture response, cache state, four explicit pin responses, and a later
  capture reload. The four pin operations ended at the original `pinned=0`; bootstrap/capture made
  no unexpected database, journal, or artifact change. No Page Mappings failure was recorded after
  the open request.
- HTTP on `127.0.0.1:65278` served the current `main.eb4f02b1.js` and
  `main.df7752f0.css` bytes during acceptance. PID `2852` is now stopped and both ports are closed.
  No package/image was built. Other-database/SQL Server rollout, orphan inventory/reconciliation,
  reconnect/takeover/retarget/delete/same-ID reuse, Use Existing/Rescan, retention policy-save/purge,
  OCR/Memory workflows, and broader multi-page desktop/browser acceptance remain open.

## Step 8 - Bot Job switch isolation and cache reuse - 2026-08-10

The code and target/classes deployment are complete. The final live acceptance remains deliberately
separate and requires a user action; Codex did not trigger a validating scan.

- [x] Root cause confirmed: history SQL is owner-filtered, while the shared Playwright page was not
  owner-generation-bound and polluted Bot Job 32 with real Lloyds captures.
- [x] Backend owner/browser/workspace retarget and mutation fencing implemented and pushed in
  `3721c049`.
- [x] Safe top-document reuse on pages containing frames implemented and pushed in `c594ba5b`.
- [x] Frontend atomic WebSocket-buffer generation and stale-owner state retirement pushed in
  `17748b8`.
- [x] Duplicate Page Mappings Close control removed in frontend `e23c6d6e`; one guarded Close
  action remains.
- [x] Bounded open-Shadow-DOM fingerprinting and fail-closed top-document scanner scope pushed in
  `b9222d2f`; closed roots and shadow-scoped element capture remain unsupported.
- [x] Clean production frontend build mirrored exactly and pushed in backend `242095b2`: 58 files,
  zero differences, `main.d31d8186.js` and `main.9afd0737.css`.
- [x] Java compilation passed with 562 main sources and only the two existing warnings.
- [x] No tests were created or run, per current user direction; no package/image was built.
- [x] Final runtime PID `8032` is healthy on `127.0.0.1:60711` / `127.0.0.1:60712`; live assets
  return HTTP 200 with the committed hashes and new `.10` logs have zero relevant error matches.
- [x] Read-only SQLite health passed: `quick_check=ok`, 24 migrations, zero FK violations, 13 READY
  snapshots, and no sidecar. The final code deployment has not produced a new snapshot yet.
- [ ] User-driven Bot Job 29 -> 32 automatic Page Scanner/Page Mappings/browser retarget acceptance.
- [ ] One fresh post-`b9222d2f` Page Mappings Rescan must create a READY capture with a 64-character
  fingerprint and make cache state CURRENT / Use Existing enabled.
- [ ] Use Existing must load that capture without adding a database row or artifact.
- [ ] Pre-fix Lloyds captures stored under Bot Job 32 remain untouched; cleanup requires explicit
  authorization.
- [ ] Package/image delivery and other-database/SQL Server rollout remain open.

## Step 8 UI completion and adaptive full-page Rescan - 2026-08-10

This checkpoint supersedes the older contaminated-capture, Page Mappings UI, fixed-delay scroll,
and running-PID statements in the preceding Step 8 section. Historical implementation evidence
remains valid.

- [x] Frontend `716a686` adds independently scrollable history/details regions; `7264fa7` adds the
  search clear-X and pulsing Pages badge; and `f5cb822` adds the focus-contained bottom-right rules
  guide plus horizontally pannable natural-width screenshots with overlays on the same canvas.
- [x] Frontend `7f04cbc` adds the correlated red/green `SCROLL PAGE` toggle; `af6fffd` closes the
  modal focus boundary. `Use Existing` remains a read-only load of the latest fingerprint-matching
  READY capture and intentionally selects that newest reusable row.
- [x] Backend `260f2025` owner-authorizes and propagates `scrollPage`, performs bounded scrolling
  before the authoritative scan, restores/revalidates page identity, and forces only that snapshot
  to `full_page`. Explicit OFF and normal Page Scanner behavior are unchanged.
- [x] Retention-days fallback is 30 for missing/blank/invalid/out-of-range configuration; explicit
  `0` still disables age cleanup. Max-unpinned fallback remains `0`, and pinned/newest-per-page
  safety rules remain unchanged.
- [x] The two exact Lloyds rows incorrectly persisted under Home Banking 2 / Bot Job 32 and their
  active artifact folder were removed through guarded maintenance. The recoverable database/artifact
  backup is `D:\Projects\ARWebBancaStato\ARWeb\Backup-CODEX-2026-08-10-job32-lloyds-cleanup`;
  database backup SHA-256 is
  `8B4BD1F19A535644824F953E2D7FDEA4291592020A7E082F1CBC1DF0D7F95716`.
- [x] Read-only post-cleanup/current DB evidence: `quick_check=ok`, 24 migrations, zero FK violations,
  17 READY Job 32 BancaStato captures, two pinned, zero Job 32 Lloyds rows, and no SQLite sidecar.
- [x] Live evidence confirmed the initial 200 ms traversal was too fast for visual fidelity: all
  repeats found 239 locators, but available OCR words increased 10 -> 25 -> 51 while the scroll
  completed in about 2.3 seconds.
- [x] Backend `20c8a4bc` replaces the fixed delay with bounded adaptive render readiness covering
  paint frames, relevant DOM/class/style quiet, near-viewport image decoding, fonts, and finite
  visible animations. Per-viewport and 45-second global deadlines, stable-bottom confirmation,
  restore-time readiness, exact page identity, and fail-closed timeout behavior remain enforced.
- [x] Clean frontend build passed with existing warnings; exact 58-file mirror `6b2d1350` serves
  `main.3f8cb24e.js` and `main.c51a1b29.css`. Source/target resource hashes match.
- [x] Java compile passed with 563 sources and the two existing warnings. No tests were created/run,
  no package/image was built, and all unrelated dirty files were preserved.
- [x] PID `4428` runs the adaptive class from `target/classes` with the exact BancaStato config on
  127.0.0.1:54668/54669. HTTP root/assets match the committed hashes and six new `.16` logs have
  zero relevant error matches.
- [ ] User live gate: run one new Page Mappings Rescan with `SCROLL PAGE` ON and compare the visual
  completeness. Do not infer this from compile, restart, or pre-adaptive captures.
- [ ] Explicit bounded limitations remain: virtualized lists, nested scroll containers, canvas/video,
  CSS background-image readiness, and unbounded infinite pages cannot be guaranteed.
- [ ] Package/image delivery, other-database/SQL Server rollout, and broader reconnect/takeover/
  retention/OCR/Memory/multi-page acceptance remain open.

## Per-Bot-Job SCROLL PAGES limit and redeployment - 2026-08-10

This checkpoint supersedes only the prior current-asset/PID and fixed scroll-limit status. Earlier
Step 8 implementation and incident history remains valid evidence.

- [x] Frontend `d5f6dad` adds a styled `SCROLL PAGES` integer control with default `5`, range `1..40`,
  and browser persistence isolated by exact Home Banking and Bot Job IDs. The ON/OFF toggle remains
  transient OFF, and dirty/invalid drafts block cache actions and Rescan until committed.
- [x] Backend `805968ad` validates explicit values after exact detached authorization, defaults a
  missing legacy field to `5`, and correlates the exact boolean/count on accepted and terminal frames.
  The traversal counts confirmed downward viewport movements, succeeds at stable bottom or the chosen
  bounded count, and retains fail-closed technical/render limits.
- [x] Frontend `npm run build` passed with existing repository warnings. Exact deployment mirror
  `4e5813c0` contains 58 matching source/resource files and 19 matching image assets; 24 stale
  `target/classes` bundles were removed. Old `main.3f8cb24e.js` / `main.c51a1b29.css` are absent.
- [x] Current entrypoints are `main.15510fe8.js` (2,065,999 bytes; SHA-256
  `50F04B0F4BB47EF58F2A393A49415479D5D6F4C7704DA28BA939B0D5CE048902`) and
  `main.974b35cd.css` (498,096 bytes; SHA-256
  `7BCBDD73DD3F192571D806928F9E170E77E8AE7F06FD4F9DFCC666B4EC674E63`).
- [x] `mvn -DskipTests compile` passed with 563 main sources and the two existing warnings. No tests
  were created or run per user direction; no migration, backend package, or container image was made.
- [x] PID `21796` runs the rebuilt `target/classes` with the exact BancaStato config on
  127.0.0.1:53734/53735. HTTP root/JS/CSS return 200 with matching bytes and six `.17` logs contain
  zero relevant error matches.
- [ ] User live gate: run one Page Mappings Rescan with `SCROLL PAGE` ON and a selected non-default
  limit, then compare the resulting full-page visual. Codex did not trigger a scan.
- [ ] Bounded limitations remain: virtualized lists, nested scroll containers, canvas/video, CSS
  background-image readiness, and unbounded infinite pages cannot be guaranteed.
- [ ] Package/container-image delivery, other-database/SQL Server rollout, and broader reconnect/
  takeover/retention/OCR/Memory/multi-page acceptance remain open.

## Page Mappings header and element-total refresh - 2026-08-10

This checkpoint supersedes only the preceding current frontend assets and PID.

- [x] Frontend `cf16efe` places `Total Web Elements: <count>` at the right of the captured-element
  search label and uses the selected capture's authoritative `elementCount`, independent of filtering
  and the 200-row result-display cap.
- [x] The Page Mappings page and top bar now follow the Main Dashboard pattern. The old inset gradient,
  rounded header, and eyebrow are removed; the two-column layout, responsive stacking, horizontal
  capture pan, and independent vertical scroll owners are unchanged.
- [x] `npm run build` passed with existing warnings. No tests, Maven command, Java compilation,
  migration, backend package, or container image was run or created.
- [x] Exact mirror `98ae848b` contains 58 matching files and 19 matching image assets. Current
  entrypoints are `main.92c3e040.js` (SHA-256
  `2E288579487DE1E0FE8C4C9D85E9AC70B9782749D091D485AE3EA8C69BFA329F`) and
  `main.1ac3c57f.css` (SHA-256
  `00197A6964A29C506217792B5F37E47EA4BB5BF9E351883F5DA731D6A968B4AE`).
- [x] PID `12944` serves the exact target assets from the BancaStato config on 55720/55721; six `.18`
  logs contain zero errors or strict operational failures.
- [x] Read-only DB count: BancaStato has 881 active scanned-element rows; Job 32's latest READY capture
  has 239 elements and Job 32 has 18 READY capture versions.
- [ ] User visual check of the compact header and right-aligned total remains open, plus one Rescan at
  a selected non-default scroll limit.

## Smoke Test Playwright page refresh - 2026-08-10

This checkpoint supersedes only the prior current frontend assets and runtime PID; it does not alter
the Page Mappings acceptance gates above.

- [x] Frontend `7d5a157` adds the compact two-line `Refresh` / `Web Page` control immediately before
  `Stop` in Smoke Test Integration, with isolated styling matching the established data-mode toggle.
- [x] Backend `aab60fca` adds an exact owner/binding/workspace/graph-authorized refresh operation that
  reserves the shared Playwright browser, calls current-page reload, waits for settlement, and holds
  the Bot Job mutation generation throughout. It does not reload the React page.
- [x] Frontend production build passed with existing warnings; Java compile passed with 564 main
  sources and the two existing warnings. No tests ran per user direction.
- [x] Deployment `cd9bf34a` mirrors 58 files and 19 images. Current entrypoints are
  `main.9a55ef9b.js` (SHA-256
  `379BB80F481BFE97BB563BFAA98071125BEDDA06A97F85DD4F8DE53940F965F9`) and
  `main.069de826.css` (SHA-256
  `08535CB786F8B8A3D27FCA4BFF7953F48B1B20B27667A279337DBCD98101C16F`).
- [x] PID `17864` runs the rebuilt BancaStato `target/classes` on 62590/62591; root/JS/CSS return
  HTTP 200 with matching hashes.
- [ ] User live gate: open Lloyds Bot Job 29 Smoke Test Integration while idle, confirm Refresh Web
  Page appears immediately before Stop, click it once, and verify the active Lloyds Playwright page
  reloads. Codex did not click the control or execute a test.
