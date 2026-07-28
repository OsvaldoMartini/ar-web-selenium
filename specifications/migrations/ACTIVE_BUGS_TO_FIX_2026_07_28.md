# Active Bugs to Fix — 2026-07-28

This is the shared CODEX/Claude coordination list for the current Bot Job Details,
Components, and Memory List stabilization work.

## File ownership rule

Before changing a bug, claim its files in this table. Work on a separate branch or
worktree when another terminal owns overlapping files. Do not build/deploy shared
frontend artifacts until the owning change has been reviewed and committed.

| ID | Priority | Area | Status | Owner |
|---|---|---|---|---|
| BUG-001 | Critical | Instruction deletion selects positional IF/LOOP body rows | Fixed; focused verification and deployment passed | CODEX |
| BUG-002 | Critical | GridItemComp still shares branching Bot Job orchestration instead of independent typed Component calls | Pending | Unclaimed |
| BUG-003 | Critical | Memory List drag/drop must remain isolated from GridItem and GridItemComp drag/drop controllers | Pending | Unclaimed |
| BUG-004 | High | Memory Apply and Bot Job Details refresh/realtime synchronization need end-to-end regression coverage | Pending | Unclaimed |
| BUG-005 | High | Variable producer/consumer and parent-reference repair need production-data audit coverage | Pending | Unclaimed |
| BUG-006 | High | Separate deletion-modal correction in another terminal | In progress | Claude terminal |

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

## Next execution order

1. Manually verify BUG-001 on a disposable Bot Job copy.
2. Separate GridItemComp orchestration and contracts (BUG-002).
3. Repair and isolate Memory List drag/drop (BUG-003).
4. Add refresh/realtime regression coverage (BUG-004).
5. Complete the production variable-relation audit (BUG-005).
