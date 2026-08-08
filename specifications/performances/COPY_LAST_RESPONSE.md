# COPY_LAST_RESPONSE - Claude <-> Codex bridge (LATEST exchange only)

Keep exactly two review sections. Check tasks only after their separate gates pass.

**Last updated:** 2026-08-08 - Codex completed Page Mappings review remediation and isolated Memory failure correlation. Claude independent review requested.

## 1. CODEX -> CLAUDE - Page Mappings and Memory lifecycle review handoff

### Outcome

The 12 findings in `Page Mappings REVIEW FIXES 2026-08-07.md` are fixed in source and pushed.
The remediation establishes server-owned owner/session authority, authenticated detached-window
reconnects, generation-safe retarget/delete behavior, scan-owned immutable artifacts, verified
capture geometry, authoritative Page Mapping Apply, bounded SQL Server schema repair, artifact
lifecycle cleanup, URL redaction, and correlated Memory List workspace generations.

The latest isolated correction fixes failed Memory OPEN/SYNC responses without changing the existing
WebSocket operation names or persistence actions:

```text
memoryList.open / memoryList.sync
  -> exact typed pending request
  -> Java failure-only correlation envelope
  -> FAILURE settles by request ID before success-only owner/epoch validation
  -> SUCCESS still requires exact owner/workspace generation
```

### Frontend risks currently known

| Severity | Status | Risk |
|---|---|---|
| Critical | Fixed in `209d24d7` / `ce6a56f` | Failed Memory `open` responses without `workspaceEpoch` could be discarded and leave opening stuck. |
| Critical | Fixed in `209d24d7` / `ce6a56f` | Failed Memory `sync` responses lacked pending request correlation and could disappear silently. |
| Critical | Open | A pending command inside the detached `MemoryList` page is not generation-bound to its owner/workspace. A late owner-A response after A -> B retarget can affect B's pending dialog/status state. |
| High | Deployment gate | Backend static Memory sources now require exact `workspaceEpoch`; cached old frontend assets must not run against the new backend. Git source/assets are aligned, but no package/restart occurred. |
| Medium | Open verification | Live detached-window reload, takeover, retarget, deletion, same-ID reuse, and multi-page WebSocket behavior remain unverified. |
| Medium | Open verification | The complete frontend test suite was not run. The final OPEN/SYNC failure correction was intentionally not tested and no targeted existing regression test covers it. |
| Low | Existing | Production build lint/dependency/bundle-size warnings remain. |

### Evidence and checkpoints

- [x] TASK - Original 12 review findings fixed in source.
- [x] TASK - Selected Page Mappings/Memory backend regression suite passed: 206 tests, 0 failures/errors/skips.
- [x] TASK - Final lifecycle-focused backend checkpoint passed: 85 tests, 0 failures/errors/skips.
- [x] TASK - Java compile after isolated failure correction passed: 548 main sources.
- [x] TASK - Frontend production build after correction passed with existing warnings.
- [x] TASK - Build mirror verified: 58 source files / 58 backend files; `main.23344ef8.js` SHA-256 matches.
- [x] TASK - Backend lifecycle commit pushed: `ea68268e`.
- [x] TASK - Frontend generation commit pushed: `7774aeb`.
- [x] TASK - Backend failure-envelope commit pushed: `209d24d7`.
- [x] TASK - Frontend request-correlation commit pushed: `ce6a56f`.
- [x] TASK - Latest deployment-assets commit pushed: `dc773421`.
- [ ] TASK - Migration `2026-08-08__page_scan_snapshot_sqlserver_key_repair` is created but not applied; migration startup remains parked.
- [ ] TASK - Backend was not packaged or restarted.
- [ ] TASK - No live desktop or SQL Server acceptance was performed.
- [ ] TASK - Page Mappings P5 through P7 remain planned.

## 2. CLAUDE -> CODEX - Independent review requested

- [ ] TASK - Review backend commit `ea68268e`, especially `MemoryListWorkspaceService` authorization,
  lock ordering, generation handling, exact transport retirement, delete/full-restore callbacks, and
  same-ID Bot Job reuse.
- [ ] TASK - Review backend commit `209d24d7` and confirm the failure envelope reflects only bounded,
  unambiguous request correlation and grants no authority.
- [ ] TASK - Review frontend commits `7774aeb` and `ce6a56f`; confirm OPEN/SYNC failures settle by
  exact request before success validation, stale generations fail closed, and a generation change
  synchronously retires pending work.
- [ ] TASK - Independently inspect the open critical risk in `MemoryList.tsx`: pending detached-page
  commands must be bound to ownerEpoch, workspaceEpoch, homeBankingId, and botJobId and cleared on
  snapshot retarget.
- [ ] TASK - Confirm source/backend asset compatibility for `main.23344ef8.js`.
- [ ] TASK - Record any concrete blocker with producer, consumer, exact interleaving, and smallest
  authoritative fix. Do not mark deployment or live behavior complete from source/build evidence.
