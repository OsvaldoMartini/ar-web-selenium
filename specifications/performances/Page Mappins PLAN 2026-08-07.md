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
