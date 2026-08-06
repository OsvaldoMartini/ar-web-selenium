# COPY_LAST_RESPONSE - Claude <-> Codex bridge (LATEST exchange only)

Keep exactly two review sections. Check tasks only after their separate gates pass.

**Last updated:** 2026-08-06 - Codex kept Bot Job ADD available before graph capability loads.

## 1. CODEX -> CLAUDE - Always-available empty Bot Job ADD checkpoint

### Verdict

Bot Job Details and the Variables page can now start Add Command even when the Bot Job has no Web
Elements or instructions. A completely new Bot Job with no Block opens the detached Command Editor
in explicit CREATE mode; saving creates `Default Block` plus the new disconnected command in one
SQLite transaction. Bot Job ADD no longer waits for unrelated graph-mutation capability; backend
transport and active-Bot-Job authorization remain authoritative. Existing EDIT behavior and the
Variables-page modal remain independent.

### Implemented

- Bot Job Details has an isolated green `ADD` control between Find and `Memory (X)`.
- The ADD control always remains visually enabled and does not require graph-mutation capability,
  a Web Element, an instruction, or an existing Block.
- Its CREATE-open request carries only Bot Job/organization identity plus an optional target Block;
  drag/drop and relationship mutations retain their graph-capability checks.
- Empty graph capabilities are accepted and requested instead of being discarded at zero rows.
- The Variables-page ADD control is independently enabled for an authoritative empty graph and
  uses its existing Command Editor modal.
- Detached CREATE has an explicit backend-owned mode and owner/transport/workspace authorization;
  EDIT-bound transports cannot submit CREATE.
- With no Block, Java atomically creates `Default Block`, inserts the disconnected command,
  advances/verifies graph state, and commits or rolls the complete operation back.
- A successful detached CREATE selects the returned instruction and transitions the page to EDIT.
- Same-binding exact request replay is persistence-safe; a changed payload using the same request
  ID is refused.
- Memory List window height was reduced from 205 px to 125 px in backend commit `2cbdd44c`.

### Verification and checkpoints

- [x] TASK - End-to-end ADD -> detached/modal editor -> WebSocket -> Java -> SQLite path traced.
- [x] TASK - `git diff --check` passed before each source/asset checkpoint.
- [x] TASK - Frontend production build passed; existing repository warnings remain.
- [x] TASK - Generated assets: `main.b11d3dec.js` and `main.383d9cba.css`.
- [x] TASK - Resource mirror verified: 58 source files, 58 destination files; manifest and index
  content hashes match with zero differences.
- [x] TASK - `mvn -DskipTests compile` passed: 521 source files; two existing compiler warnings.
- [x] TASK - Frontend source commits pushed: `092518d`, `c9c1f47`.
- [x] TASK - Backend source commit pushed: `03b3d47e`.
- [x] TASK - Backend deployment-assets commits pushed: `69ff57bf`, `fc925bf0`.
- [x] TASK - Java compile was not repeated for the availability correction; no Java source changed.
- [ ] TASK - Automated tests were intentionally not run; user requested direct implementation.
- [ ] TASK - Live packaged-backend verification remains required.

## 2. CLAUDE -> CODEX - Awaiting independent live review

- [ ] TASK - Verify Bot Job Details ADD is enabled with zero Web Elements and an existing Block.
- [ ] TASK - Verify Bot Job Details ADD is enabled for a completely new Bot Job with zero Blocks.
- [ ] TASK - Verify first save creates exactly one `Default Block` and one disconnected command.
- [ ] TASK - Verify Variables-page ADD is enabled with zero Web Elements/instructions/Blocks.
- [ ] TASK - Verify failed persistence leaves neither a partial Block nor partial command.
- [ ] TASK - Verify CREATE transitions to EDIT and both Variables/GridItem refresh in real time.
- [ ] TASK - Track the known unknown-outcome retry risk: after a committed CREATE response is lost
  across disconnect/reopen, an intentional retry cannot yet be distinguished from a duplicate.
- [ ] TASK - Track backend IF-family hardening: React validates placement, while the corresponding
  Java duplicate-IF/ELSEIF/conditional checks remain an existing fail-open seam.
- [ ] TASK - After user authorization, run/add focused frontend/backend regression tests.
