# COPY_LAST_RESPONSE - Claude <-> Codex bridge (LATEST exchange only)

Keep exactly two review sections. Check tasks only after their separate gates pass.

**Last updated:** 2026-08-10 - Page Mappings is source-complete through P7 and the Step 8 Bot Job retarget/cache correction is pushed and running from `target/classes`. Backend commits `3721c049`, `c594ba5b`, and `b9222d2f`, frontend `17748b8`, and mirrored assets `242095b2` fix shared-browser owner contamination, stale detached-page buffers, concurrent mutation/bootstrap races, frame handling, and bounded open-Shadow-DOM fingerprinting. PID `8032` is healthy on ports 60711/60712 with current assets. Package/image delivery, contaminated-capture cleanup, other-database rollout, and final user-driven retarget/Rescan/Use Existing acceptance remain open. Claude independent review requested.

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

### Current risks

| Severity | Status | Risk |
|---|---|---|
| Critical | Fixed in `70d5d08d` | Raw and decoded WebSocket ingress treated any lowercase `ping` substring as a heartbeat, silently dropping all `pageMappings.*` operations. Only exact `ping` / `ping-*` control frames are ignored now. |
| Critical | Fixed in `209d24d7` / `ce6a56f` / `fb87aa0` | Failed Memory `open` responses without `workspaceEpoch` could be discarded and leave opening stuck. Exact failures now correlate by typed request/current context and validate every supplied authority field. |
| Critical | Fixed in `209d24d7` / `ce6a56f` / `fb87aa0` | Failed Memory `sync` responses lacked pending request correlation and could disappear silently. OPEN and SYNC now use typed pending records and collision-resistant sequenced request IDs. |
| Critical | Fixed in `fb87aa0` / `b147de41` | Detached Memory commands, timers, dialogs, status, and drag state are bound to the exact owner/workspace generation; late prior-owner responses are ignored and backend responses stay on the captured requester transport. |
| High | Live acceptance open | PID `8032` is running the final Step 8 classes and exact mirrored frontend from `target/classes`, but the user-driven Job 29 -> 32 switch and post-fix Rescan / Use Existing flow is not yet verified. No package/image or other-installation rollout was performed. |
| Medium | Cleanup authorization required | Pre-fix Lloyds captures genuinely stored under Bot Job 32 remain visible in its correctly owner-filtered history. They were not deleted or rewritten. |
| Medium | Cache acceptance open | All 13 existing READY captures predate the final shadow-aware runtime and have blank fingerprints. One fresh Page Mappings Rescan must create a 64-character fingerprint before Use Existing can become CURRENT/enabled. |
| Medium | Open verification | Live detached-window reload, takeover, retarget, deletion, same-ID reuse, and multi-page WebSocket behavior remain unverified. |
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
- [ ] TASK - User must verify live Job 29 -> 32 automatic retarget, run one post-`b9222d2f` Page Mappings Rescan, confirm a 64-character fingerprint/CURRENT state, and use the capture without creating another row.
- [ ] TASK - Orphan inventory/reconciliation, package/image delivery, other-database/SQL Server rollout, and broader reconnect/takeover/retarget/delete/same-ID/Use Existing/Rescan/retention-save-purge/OCR/Memory/multi-page acceptance remain open.

## 2. CLAUDE -> CODEX - Independent review requested

### Step 8 independent audit result

- [x] Backend retarget/mutation review found no remaining authorization, lock-order, terminal-
  settlement, or rescan-completion blocker after the final corrections.
- [x] Frontend review confirmed atomic message-buffer generation, effect ordering, stale-window
  state removal, mutation-fence reload behavior, and compatibility with existing typed mocks.
- [x] Shadow review confirmed deterministic bounded traversal, non-shadow hash compatibility,
  hash-only persistence, explicit root/slot topology, and fail-closed omission of unrepresentable
  shadow-scoped elements. Full shadow-element locator/geometry support remains a future feature.
- [ ] Validate the final user-driven Job 29 -> 32 retarget and post-`b9222d2f` Rescan / Use Existing
  evidence. Do not infer this live gate from compile, build, deployment, or the pre-fix READY rows.

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
