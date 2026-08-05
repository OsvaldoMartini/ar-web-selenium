### Verification and deployment

- [x] Focused backend result: 22 tests passed with zero failures/errors.
- [x] Focused frontend result: 4 suites, 29 tests passed with zero failures.
- [x] `npm run build` completed successfully with existing repository warnings.
- [x] Produced `main.f69dd91d.js` and `main.3451644a.css`.
- [x] Mirrored the React build to backend resources: 45 source files, 45 target files, and zero
      relative-path/SHA-256 differences.
- [x] Production database rows were not modified.
- [ ] P3 remains deferred: execution initial/current value streaming, pause-time value editing,
      and Resume require a safe Engine run-scoped variable API.
- [ ] Organization-wide variable expansion is deferred until the Bot Job-scoped execution model
      is runtime-accepted.
- [ ] Shared hardening follow-up: all fixed detached pages should receive one-use launch nonces
      before session takeover. Variables currently follows the same loopback-only fixed-session
      boundary as the existing fixed pages.

### Decisions

- D-037: Variables P2 is scoped to one authoritative active Bot Job. Organization aggregation is
  a later expansion and cannot mix execution memories across Bot Jobs.
- D-038: The declared relationship graph and live execution values are separate contracts.
  P2 exposes declarations/relationships; P3 owns run-scoped initial/current values.
- D-039: Current literal SET is an assignment into the declaration Web Field, not a variable
  consumer. The UI and edge direction must preserve actual Engine behavior.
- D-040: A failed or stale refresh never clears the last valid Variables graph.
- D-041: Realtime Variables publication is mutation-driven and revision-deduplicated; execution
  paint/status events are not graph mutations.

## CODEX - React-owned exact instruction deletion (2026-07-28)

- [x] Replaced Java positional IF/LOOP delete expansion with a strict versioned exact-ID contract.
- [x] React calculates the delete set from the rendered graph and uses the same immutable plan for
      the confirmation modal and WebSocket request.
- [x] Conditional deletion includes linked boundary rows only; positional body rows are preserved.
- [x] LOOP deletion includes its explicit anchor family only; positional body rows are preserved.
- [x] Parent ownership remains Block-local, while explicit variable producer/consumer dependencies
      may cross Blocks inside the same owner.
- [x] React supplies explicit surviving-parent repairs; Java persists those repairs and the exact
      confirmed IDs in one database transaction.
- [x] Removed the legacy `InstructionDeleteImpactService` and hidden sole-EXCEL-GOTO deletion path.
- [x] Focused verification: 44 React tests and 7 Java tests passed.
- [ ] Manual verification remains on a disposable production-shaped Bot Job copy.
- [x] Added the shared active tracker:
      `specifications/migrations/ACTIVE_BUGS_TO_FIX_2026_07_28.md`.

### Decision

- D-042: DELETE_INSTRUCTION semantics belong to the React/TypeScript rendered-graph planner.
  Java accepts contract version 2 only, validates request/revision/owner integrity, and persists
  exactly the submitted instruction IDs and parent repairs without semantic group expansion.
