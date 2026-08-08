# Page Mappings Review Fixes — 2026-08-07

Status: all 12 original review findings are fixed in source and pushed. Independent backend review
found no remaining lifecycle blocker. The migration is created but deliberately not applied; backend
packaging, restart, real SQL Server verification, and live desktop acceptance remain separate open
gates.

## 2026-08-08 Codex remediation checkpoint

Primary backend commits: `9436d81a`, `ec779ed5`, `6c429598`, `2712b32a`, `230939cf`,
`74a0b487`, `537dea47`, `4667a386`, `ea68268e`, `209d24d7`, and `b147de41`.

Primary frontend commits: `cfa1607`, `df5cc1b`, `2c15f6c`, `5dc60d0`, `7774aeb`,
`ce6a56f`, and `fb87aa0`. Latest mirrored frontend assets: `9a9dc6db`, bundle
`main.16e24f7b.js`.

Verification evidence:

- Selected Page Mappings/Memory backend regression suite: 206 tests, 0 failures/errors/skips.
- Final lifecycle-focused backend checkpoint: 85 tests, 0 failures/errors/skips.
- Frontend focused checkpoint before the failure-correlation correction: 22 tests passed plus the
  affected Component parity test.
- The final Memory request/command correlation suite passed 25 tests with no failures. It covers
  missing-epoch OPEN/SYNC failures, supplied generation mismatches, sequenced IDs, owner retarget,
  late responses in the same message batch, invalid success tuples, and cross-owner drag retirement.
- A nearby selected frontend run passed 27 of 35 tests. Eight existing stale
  `GridItemComp.memoryParity` conditional-delete/rollback expectations remain outside this change.
- Java compile passed after the correction: 548 main sources.
- Frontend production build passed with existing warnings; source/resource mirror is 58/58 files and
  the source/destination SHA-256 for `main.16e24f7b.js` is
  `ED8C10BCA7B21661C19EA67613DE04884EC27CA5920C06D6A65A1E469A112004`.

### Frontend risks currently known

| Severity | Status | Risk |
|---|---|---|
| Critical | Fixed in `209d24d7` / `ce6a56f` / `fb87aa0` | Failed Memory `open` responses could be discarded when `workspaceEpoch` was absent. Exact failures settle only for the typed current request/context and supplied authority fields must match. |
| Critical | Fixed in `209d24d7` / `ce6a56f` / `fb87aa0` | Failed Memory `sync` responses had no pending request correlation. OPEN and SYNC use isolated typed pending records plus collision-resistant sequenced request IDs. |
| Critical | Fixed in `fb87aa0` / `b147de41` | Detached Memory commands and drag state are bound to the complete owner/workspace tuple, retired synchronously on retarget, and backend responses are sent only to the captured exact requester transport. |
| High | Deployment gate | The backend now requires exact static-source `workspaceEpoch`; old cached frontend assets must not be paired with the new backend. Source and backend resources are aligned in Git, but the backend has not been packaged/restarted. |
| Medium | Open verification | Real detached-window reload, takeover, retarget, deletion, same-ID reuse, and multi-page WebSocket behavior have not been live-verified. |
| Medium | Open verification | The complete frontend suite was not run. The affected-path suite passed 25/25; eight stale expectations in a nearby `GridItemComp.memoryParity` suite remain outside this change. |
| Low | Existing | The production build completes with existing repository lint/dependency/bundle-size warnings. |

## Original review summary (pre-remediation)

The following summary and finding descriptions preserve the reviewer’s original pre-fix evidence.
Their current remediation status is represented by the checked finding boxes and the 2026-08-08
checkpoint above.

Normal Page Mappings launch is blocked, valid owner context is never established, and the artifact and Apply paths can associate or commit data from the wrong capture or owner. Supported SQL Server deployments also fail the new migration; the passing focused tests and build do not exercise these end-to-end contracts.

## Review findings

- [x] **P1 — Register Page Mappings as a fixed presentation**

  File: `D:\Projects\AllinWeb\ar-web-selenium\src\main\java\com\allinweb\ch\socket\PageMappingsWorkspaceService.java:78-81`

  The normal Bot Job and Page Scanner launch paths always reach this call, but `PagesOpenWorkspaceService.openOrFocusDetachedWorkspace` rejects sessions absent from `FIXED_PRESENTATIONS`, which has no `PAGE_MAPPINGS_MANAGER` entry. The service therefore returns `ok=false`, so neither MAPPINGS button can open the page.

- [x] **P1 — Bind Page Mappings to a server-owned owner**

  File: `D:\Projects\AllinWeb\ar-web-selenium\src\main\java\com\allinweb\ch\socket\PageMappingsWorkspaceService.java:98-99`

  `ARWebSocketServer.detachedWorkspaceDesktopUrl` adds only `sourceBotJobId`, so a normally launched page sends `homeBankingId=0` here. The owner-filtered query returns no captures and Memory List Apply later has owner 0; accepting a manually supplied owner also makes the scope spoofable. Resolve both IDs from a server-side Bot Job/session binding.

- [x] **P1 — Retarget the reused Page Mappings window**

  File: `D:\Projects\AllinWeb\abr-react-ts-grid\src\components\PageMappingsPage.tsx:47-50`

  When a Page Mappings window is already open for job A and the user launches it from job B, `openOrFocusDetachedWorkspace` only focuses the existing session; these memoized URL values never change and there is no retarget handler. The window continues showing and staging A while the action came from B, so publish an authoritative retarget and reset/rebootstrap the page.

- [x] **P1 — Snapshot only artifacts from the current scan**

  File: `D:\Projects\AllinWeb\ar-web-selenium\src\main\java\com\allinweb\ch\facade\PageScanSnapshotStore.java:129-132`

  When the previous scan wrote `page-BJ*` files and the current scan is empty or diagnostics fail, `resolveNames` does not rewrite those global files, so this wildcard copy puts the previous screenshot and rectangles—potentially from another owner—into the current `READY` capture. Generate artifacts under a scan-specific identity instead of copying uncorrelated mutable globals.

- [x] **P1 — Serve rectangle bounds for capture overlays**

  File: `D:\Projects\AllinWeb\abr-react-ts-grid\src\components\PageMappingsPage.tsx:126-130`

  `PlaywrightElementScanner` emits `coordinates` as two values (`x,y`), while this parser rejects anything short of `x,y,width,height`; the copied `page-BJ-rects.json` is never returned by the capture endpoint. Consequently standard snapshots render zero overlays, so serve and match the rectangle artifact, including DPR, or persist full bounds.

- [x] **P1 — Drive artifacts from the selected capture**

  File: `D:\Projects\AllinWeb\abr-react-ts-grid\src\components\PageMappingsPage.tsx:260`

  `selectedScanId` is not tied to artifact state: bootstrap selects the newest capture without loading it, and a row click changes the ID while the previous elements and image remain; responses also carry no scan or request ID. Switching A to B can therefore stage A's locator with B's `captureId/pageKey`, or leave it indefinitely after failure, so clear state and accept only the matching response.

- [x] **P1 — Reload Page Mapping rows before Apply**

  File: `D:\Projects\AllinWeb\ar-web-selenium\src\main\java\com\allinweb\ch\socket\MemoryListWorkspaceService.java:674-681`

  For `PAGE_MAPPINGS` items, `isElementSource` routes the row here and directly commits the client-supplied `elementDTO`; `captureId`, `pageKey`, and `expectedLastScannedAt` are ignored, and no `scannedElementId` is supplied. Selecting an old capture after a rescan can save a stale locator, so reload and revision-check the owner/page-scoped registry row as specified in `specifications/performances/Page Mappins PLAN 2026-08-07.md:114-123`.

- [x] **P1 — Use indexable snapshot key types**

  File: `D:\Projects\AllinWeb\ar-web-selenium\src\main\java\com\allinweb\ch\db\migrations\M20260807_PageScanSnapshot.java:22-24`

  On supported SQL Server deployments, `scan_id VARCHAR(4000) PRIMARY KEY` exceeds the index key-width limit, and both secondary indexes also include `VARCHAR(4000)` fields. This startup migration fails before Page Mappings can run; use bounded dialect-specific types for UUID, page key, timestamp, and status, reserving long text for URL and path fields.

- [x] **P1 — Purge snapshots when deleting their Bot Job**

  File: `D:\Projects\AllinWeb\ar-web-selenium\src\main\java\com\allinweb\ch\db\migrations\M20260807_PageScanSnapshot.java:26`

  `BotJobDeleteTransaction` deletes `scanned_element` and other owned tables, but this new `bot_job_id` has neither a foreign-key cascade nor an entry in that cleanup, and no code deletes the corresponding artifact tree. Deleting a Bot Job therefore leaves snapshot rows and banking screenshots indefinitely; add database and safe filesystem lifecycle cleanup.

- [x] **P1 — Redact secrets from captured URLs**

  File: `D:\Projects\AllinWeb\ar-web-selenium\src\main\java\com\allinweb\ch\facade\PageScanSnapshotStore.java:71-72`

  When a banking URL contains session, OAuth, or account values in userinfo, query, or fragment, both `actualUrl` and `normalizedUrl` preserve them and these lines write them into durable capture metadata; the database value is also returned and rendered verbatim. Store and display a redacted URL while retaining only a one-way page key for identity, as required by `specifications/performances/Page Mappins PLAN 2026-08-07.md:238`.

- [x] **P2 — Delete finalized artifacts when DB insert fails**

  File: `D:\Projects\AllinWeb\ar-web-selenium\src\main\java\com\allinweb\ch\facade\PageScanSnapshotStore.java:89-92`

  If the database insert fails after this move, such as from a lock or schema error, the catch deletes only `staging`, which no longer exists, and leaves the finalized sensitive folder unindexed. Repeated failures accumulate orphan snapshots; delete `target` on failure or make finalization recoverable with the database write.

- [x] **P2 — Record staging-creation failures**

  File: `D:\Projects\AllinWeb\ar-web-selenium\src\main\java\com\allinweb\ch\facade\PageScanSnapshotStore.java:57-60`

  When directory creation fails because of permissions, an invalid path, or a full disk, it occurs before the `try`, so the method never executes the `FAILED` insert and the capture disappears from status history. Move staging creation into the guarded lifecycle so every attempted scan can be recorded as failed when the database remains available.

## Completion gates

- [ ] Every original root cause independently reproduced end to end; source tracing, focused
  regression evidence, and independent code review are complete, but live reproduction remains open.
- [x] Owner/session binding fixed and cross-owner requests rejected.
- [ ] Launch, focus, and retarget behavior verified end to end.
- [x] Immutable artifact correlation and rectangle data fixed in source and focused tests.
- [x] Memory List Apply reloads authoritative repository data.
- [ ] SQLite and SQL Server migration behavior verified.
- [x] Bot Job deletion removes database rows and safely removes owned artifacts in source/tests.
- [x] Sensitive URLs are redacted in storage, responses, UI, and logs in source/tests.
- [x] Failure cleanup and FAILED-history behavior verified by focused tests.
- [x] Focused Memory request/command/retarget/drag tests and the production frontend build pass.
- [ ] All nearby frontend suites are clean; `GridItemComp.memoryParity` still has eight stale conditional-delete/rollback expectations.
- [ ] Backend packaged/restarted only when explicitly authorized.
- [ ] Live behavior verified and evidence recorded.
