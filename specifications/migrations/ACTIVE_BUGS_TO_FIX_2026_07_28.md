# Active Bugs to Fix — 2026-07-28

This is the shared CODEX/Claude coordination list for the current Bot Job Details,
Components, and Memory List stabilization work.

## File ownership rule

Before changing a bug, claim its files in this table. Work on a separate branch or
worktree when another terminal owns overlapping files. Do not build/deploy shared
frontend artifacts until the owning change has been reviewed and committed.

| ID | Priority | Area | Status | Owner |
|---|---|---|---|---|
| BUG-001 | Critical | Instruction deletion selects positional IF/LOOP body rows | Automated/deployment verification passed; manual disposable-copy acceptance pending | CODEX |
| BUG-002 | Critical | GridItemComp still shares branching Bot Job orchestration instead of independent typed Component calls | Pending | Unclaimed |
| BUG-003 | Critical | Memory List drag/drop must remain isolated from GridItem and GridItemComp drag/drop controllers | Implemented; automated regression passed; three-window manual acceptance pending | CODEX verification only |
| BUG-004 | High | Memory Apply and Bot Job Details refresh/realtime synchronization need end-to-end regression coverage | Partially covered; complete end-to-end regression pending | Unclaimed |
| BUG-005 | High | Variable producer/consumer and parent-reference repair need production-data audit coverage | P0 sanitized audit captured; repair coverage remains pending | CODEX P0 documentation only |
| BUG-006 | High | Separate deletion-modal correction in another terminal | In progress | Claude terminal |
| BUG-007 | Critical | Configured external Engine PDF/CSV CHECK reads an uninitialized validation context | P1 verified; runtime correction pending | Unclaimed after CODEX audit |
| BUG-008 | Critical | GET/SET bypass the mandatory Playwright-first/one-attempt path in Scanner and Engine | P1 verified; runtime correction pending | Unclaimed after CODEX audit |
| BUG-009 | Critical | Scanner and configured Engine implement forward GOTO differently | P1 verified; parity correction pending | Unclaimed after CODEX audit |
| BUG-010 | High | Classic Pre-Launch can continue after definition-load failure | P1 verified; P4 execution-gate correction pending | Variable roadmap P4 |

## P0 file ownership claim — 2026-07-29

This claim covers baseline documentation and deterministic test fixtures only. It does not claim
runtime implementation for BUG-002, BUG-004, or BUG-006.

| Work | Owner | Files | Runtime behavior |
|---|---|---|---|
| Variable-graph P0 baseline | CODEX | `ROADMAP_VARIABLE_CENTRIC_INSTRUCTION_GRAPH_2026_07_29.md`, `../VARIABLE_SYSTEM_REDESIGN.md`, `P0_VARIABLE_GRAPH_BASELINE_2026_07_29.md` | None |
| Sanitized audit and fixture | CODEX | `../../scripts/p0/ReadOnlyInstructionGraphAudit.java`, `../../src/test/resources/fixtures/instruction-graph/*`, `../../src/test/java/com/allinweb/ch/testsupport/GoldenInstructionGraphFixtureTest.java` | None |
| BUG-003 acceptance record | CODEX verification only | This document and the P0 baseline | None unless acceptance exposes a separately claimed defect |

## P1 file ownership claim — 2026-07-29

P1 is a no-runtime-change execution audit. It claims only its specification and focused
characterization test; it does not claim implementation of BUG-007, BUG-008, or BUG-009 in the
external Engine repository.

| Work | Owner | Files | Runtime behavior |
|---|---|---|---|
| Execution semantics and entry-point audit | CODEX | `ROADMAP_VARIABLE_CENTRIC_INSTRUCTION_GRAPH_2026_07_29.md`, `P1_ENGINE_EXECUTION_SEMANTICS_2026_07_29.md` | None |
| Persisted command characterization | CODEX | `../../src/test/java/com/allinweb/ch/facade/ExecutionCommandSemanticsCharacterizationTest.java` | None |

## BUG-001 — Exact React-owned instruction deletion

### Required behavior

- React/TypeScript calculates the complete delete plan from the graph currently
  rendered to the client.
- An instruction is never selected merely because its order lies between IF/ELSE/
  ENDIF or a LOOP anchor/boundary.
- Parent relationships are block-local.
- Variable producer-to-consumer relationships may cross Blocks inside the same
  owner.
- GOTO and EXCEL GOTO references never become ownership cascades.
- The modal rows, WebSocket `deleteInstructionIds`, and database-deleted IDs are
  exactly the same ordered set.
- React explicitly sends surviving `parentId` repairs.
- Java validates request/revision/owner integrity and persists the exact submitted
  IDs and repairs. Java does not infer or expand the semantic group.
- No hidden EXCEL GOTO instruction deletion is permitted.

### Verification

- [x] React pure planner tests cover conditional boundaries, LOOP relationships,
      ordinary descendants, navigation isolation, cross-Block variable consumers,
      and parent repairs.
- [x] React integration test proves modal IDs equal WebSocket IDs.
- [x] Java contract tests cover exact ordering, owner isolation, duplicates,
      selected-ID inclusion, version enforcement, and explicit repairs.
- [x] Instruction-delete confirmations show detailed rows only up to five;
      larger exact plans show a count while preserving the complete v2 request.
- [x] Shared confirmation content is viewport-bounded and scrollable while its
      footer and Confirm/Cancel controls remain outside the scrolling region.
- [x] Java no longer emits instruction-level `canDelete`, `deleteCount`, or
      `deleteRows` UI planning fields. React derives delete readiness from
      synchronized graph coverage; Java retains v2 validation and persistence.
- [x] Dead legacy single-row deletion entry points were removed so the strict
      exact-plan transaction is the only active instruction-deletion path.
- [ ] Manual production verification on a disposable Bot Job copy.

## BUG-003 — Three private drag boundaries

The implementation has three separately named boundaries:

1. Bot Job transport: `useInstructionDrag` -> `ROW_MOVE` / `botJobTasks`.
2. Component transport: `useComponentInstructionDrag` -> `COMPONENT_ROW_MOVE` /
   `componentTasks`.
3. Detached Memory List controller: `useMemoryListDrag` -> one
   `memoryList.command` / `REORDER`.

The first two are private transport submitters. Their native row sensors, optimistic state, and
confirmation state live in separately mounted `useGridData` instances. The Memory List hook owns
its complete native drag lifecycle.

### Manual acceptance still required

- [ ] Open Bot Job Details, Components, and Memory List against a disposable owner.
- [ ] Leave a Bot Job move confirmation pending, reorder Memory List, and prove neither Components
      nor Memory List completes or resets the Bot Job move.
- [ ] Repeat with a Component move confirmation and prove only one
      `COMPONENT_ROW_MOVE` / `componentTasks` payload is emitted.
- [ ] Reorder Memory List and prove exactly one `memoryList.command` / `REORDER` payload contains
      the current owner epoch and complete stable item-key order.
- [ ] Prove each window exposes only its own diagnostic (`__gridReorder`,
      `__componentGridReorder`, or `__mlReorder`).
- [ ] Record all three before/after orders and outbound payloads without customer row data.

## Next execution order

1. Manually verify BUG-001 on a disposable Bot Job copy.
2. Complete the BUG-003 three-window manual isolation acceptance.
3. Claim and separate GridItemComp orchestration and contracts (BUG-002).
4. Claim and add the missing full Memory Apply/realtime repaint regression (BUG-004).
5. Use the P0 audit as the baseline for BUG-005 repair coverage; do not repair production rows
   before the variable-centric roadmap reaches its gated data phases.
