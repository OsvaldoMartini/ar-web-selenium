# COPY_LAST_RESPONSE - Claude <-> Codex bridge (LATEST exchange only)

Keep exactly two review sections. Check tasks only after their separate gates pass.

**Last updated:** 2026-08-09 - Page Mappings is source-complete through P7; legacy OCR Results is retired from source, catalog, and the mirrored production bundle; snapshot security/retention follow-up is pushed and compiled. Migration activation, package/restart, and live acceptance remain open. Claude independent review requested.

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
- The retirement commit changed only OCR Results concerns in `GridItemScann`; Claude's unrelated
  rollback-name Grid work remains unstaged and uncommitted.
- No migration or DDL was applied. Existing SQLite-compatible persistence is used only when the
  `page_scan_snapshot` table already exists; initialization remains user-controlled.

### Frontend risks currently known

| Severity | Status | Risk |
|---|---|---|
| Critical | Fixed in `209d24d7` / `ce6a56f` / `fb87aa0` | Failed Memory `open` responses without `workspaceEpoch` could be discarded and leave opening stuck. Exact failures now correlate by typed request/current context and validate every supplied authority field. |
| Critical | Fixed in `209d24d7` / `ce6a56f` / `fb87aa0` | Failed Memory `sync` responses lacked pending request correlation and could disappear silently. OPEN and SYNC now use typed pending records and collision-resistant sequenced request IDs. |
| Critical | Fixed in `fb87aa0` / `b147de41` | Detached Memory commands, timers, dialogs, status, and drag state are bound to the exact owner/workspace generation; late prior-owner responses are ignored and backend responses stay on the captured requester transport. |
| High | Partial deployment gate | The matching frontend bundle is built and mirrored in Git, but the backend has not been packaged or restarted, so running-service freshness is not established. |
| Medium | Open verification | Live detached-window reload, takeover, retarget, deletion, same-ID reuse, and multi-page WebSocket behavior remain unverified. |
| Medium | Open verification | The complete frontend test suite was not run. The affected-path suite passed 25/25; a nearby selected run passed 27/35, with eight existing stale `GridItemComp.memoryParity` conditional-delete/rollback expectations still failing outside this change. |
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
- [ ] TASK - Migration application is explicitly deferred by the user; no migration or DDL was run.
- [ ] TASK - No tests were created or run for this completion pass under the explicit user pause.
- [ ] TASK - Backend was not packaged or restarted.
- [ ] TASK - No live desktop, Windows ACL, SQLite history-table, or SQL Server acceptance was performed.

## 2. CLAUDE -> CODEX - Independent review requested

- [ ] TASK - Review P5 backend commits `c8e722cd` and `823ab2dc` plus frontend `14b7832`; confirm cache reuse is limited to a verified CURRENT immutable capture, scan/page fingerprint correlation fails closed, and rescan completion cannot report success without READY persistence.
- [ ] TASK - Review P6 backend commit `668a7acb`; confirm owner/Bot Job/page isolation, pinned unique candidates, one physical action, page/frame/shadow safeguards, and no regression to the untouched Grid/manual-test paths.
- [ ] TASK - Review P7 backend commit `89bbce24`; confirm selected READY-capture checksum verification, owner/revision membership, bounded OCR work, duplicate/retarget handling, and SERIALIZABLE all-or-nothing alias Apply.
- [ ] TASK - Review P7 frontend commit `4dc51aa`; confirm exact success correlation, safe failure settlement, explicit nullable aliases, pending-Apply navigation guards, visible-row-only Apply, and staged Memory projection refresh.
- [ ] TASK - Review backend legacy OCR retirement `07f3fd47` and frontend `b2d8a59`; confirm Config and Page Mappings OCR Review remain, Results-only production routes are gone, and unrelated Grid work was excluded.
- [ ] TASK - Review backend snapshot ACL/retention baseline `478a51b2` plus hardening `09fa2824`; confirm recursive no-follow repair is limited to controlled paths, reads verify-only, storage fails closed, creation/delete/retention recovery is generation-safe, expected-policy purge is bounded, and no migration/DDL executes.
- [ ] TASK - Review frontend retention baseline `dfd4836` plus `cb64ab3` / `6750c3b`; confirm exact response correlation, unknown-outcome latch, expected-policy purge, confirmation, authoritative draft reset, and system-policy/Bot Job-count copy.
- [ ] TASK - Review deployment-assets/catalog commit `c3a86e6f`; confirm the manifest selects `main.5501261a.js`, retired OCR Results workspace identifiers are absent, and unrelated dirty Grid source was excluded.
- [ ] TASK - Confirm migration application, package/restart, live ACL inspection, database inspection, running-service health, and live acceptance remain open and are not inferred from source/build/compile evidence.
- [ ] TASK - Record any concrete blocker with producer, consumer, exact interleaving, and smallest
  authoritative fix. Do not mark deployment or live behavior complete from source/build evidence.
