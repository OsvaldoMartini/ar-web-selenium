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

Relevant implementations include [ScannedElementRepository.java](/D:/Projects/AllinWeb/ar-web-selenium/src/main/java/com/allinweb/ch/db/ScannedElementRepository.java:89), [OcrTestService.java](/D:/Projects/AllinWeb/ar-web-selenium/src/main/java/com/allinweb/ch/facade/OcrTestService.java:43), [useGridData.ts](/D:/Projects/AllinWeb/abr-react-ts-grid/src/components/bot-job-details/grid/hooks/useGridData.ts:3286), and [GridItemScann.tsx](/D:/Projects/AllinWeb/abr-react-ts-grid/src/components/GridItemScann.tsx:2718).

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

   - Add `page_scan_snapshot`.
   - Implement atomic snapshot folder creation.
   - Keep writing legacy `page-BJ.*` files temporarily for compatibility.

3. **P2 — Isolated Page Mappings workspace**

   - New `PageMappingsPage`, module stylesheet, route, session and WebSocket coordinator.
   - Lazy-load images and full element payloads.
   - Keep old OCR route as a temporary compatibility adapter.

4. **P3 — Mappings launchers and explorer**

   - Bot Job and Page Scanner buttons.
   - Capture history, image overlays, element search and statuses.

5. **P4 — Memory List integration**

   - Add an explicitly authorized `PAGE_MAPPINGS` source.
   - Implement `+`, drag/drop, target Block and idempotent staging.

6. **P5 — Cache-first scanning**

   - Current-page fingerprint.
   - Use Existing / Rescan states.
   - Stale and changed-page diagnostics.

7. **P6 — Safe runtime healing**

   - Canonical and alias live-page lookup.
   - Unique candidate enforcement.
   - One-action guarantee and structured diagnostics.

8. **P7 — Complete visible rename**

   - Rename public page/components to `PageMappings*`.
   - Move existing OCR comparison into OCR Review.
   - Retire the old `OCR Results` names only after route/session parity tests pass.

Because screenshots may contain banking data, access must remain owner-scoped, files need private Windows permissions, URLs/query values should be redacted in UI/logs, and retention should require an explicit configured policy.

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
- P1 through P7 remain planned and unimplemented. Existing unrelated untracked files were left untouched.
