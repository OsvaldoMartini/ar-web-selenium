# React Instruction Migration Tracker

Date: 2026-07-11

## Objective

Finish the existing JavaFX-to-React instruction-authoring migration while preserving JCEF and the current Java backend. This tracker consolidates current progress from the command-capability and instruction-graph roadmaps.

## Working Rules

- Backend branch: `refactor/perform-actions-decomposition`
- Frontend branch: `VERSION-4.6`
- Do not create replacement branches.
- Commit and push each completed increment.
- Do not run Java or Maven on this machine.
- Keep legacy panes only until React parity is verified end to end.

## Completed

### React command authoring

- [x] Route normal grid command actions to the React floating panel.
- [x] Remove fallback/static command catalogue behavior.
- [x] Render command fields from backend metadata.
- [x] Hydrate command edits from backend drafts.
- [x] Use backend Web Field, variable, ELSEIF, edit, and split capabilities.
- [x] Send graph revisions and request IDs for command, variable, split, move, and delete mutations.

### Backend command and graph safety

- [x] Validate IF/ELSEIF/ELSE/ENDIF grammar.
- [x] Prevent loop boundaries from separating from parent instructions.
- [x] Validate exact split row partitions and later-block ordering.
- [x] Reject stale command, split, move, and delete revisions.
- [x] Make command, variable, split, move, and delete mutations idempotent.
- [x] Publish backend capabilities for command, split, Memory List, move, and delete actions.
- [x] Preserve dependent commands with their parent during cross-block movement.

### Persistence and feedback

- [x] Return move and delete results to React.
- [x] Confirm variable and instruction deletion in React.
- [x] Publish instruction deletion impact counts.
- [x] Delete instruction graphs atomically (`8c765135`).
- [x] Persist block creation, instruction reassignment, and block ordering in one split transaction (`9612518e`).
- [x] Add pure conditional and move validator tests (`57c1db54`).

## In Progress

- [ ] Central split-boundary validator.
  - Reject last-row splits instead of silently moving the boundary backward.
  - Reject splits inside nested conditional families.
  - Reject splits between LOOP/REFRESH_LOOP and their parent.
  - Add pure validator fixtures for positive and negative boundaries.

## Remaining Work

### P1 - Split preview and authoritative response

- [ ] Add `instructionGraph.previewSplit` response with affected rows and rejection reason.
- [ ] Return the authoritative refreshed block graph after an applied split.
- [ ] Make React show the actual backend split group before confirmation.

### P2 - Shared React drag controller

- [ ] Create `useInstructionDrag.ts` shared by `GridItem.tsx` and `GridItemComp.tsx`.
- [ ] Remove duplicated inline IF/loop/move checks after backend parity is confirmed.
- [ ] Restore original state on backend rejection without losing scroll or expanded blocks.
- [ ] Add explicit drag handles and backend-driven valid/invalid drop zones.
- [ ] Add reason tooltips, group preview, and keyboard movement.

### P3 - Command codec and rule completeness

- [ ] Complete round-trip fixtures for every legacy command operation format.
- [ ] Verify typed schemas for SET, GET, CK/PDF/CSV, GOTO, EXCEL GOTO, loops, waits, swipe, and independent commands.
- [ ] Default unknown row/command combinations to denied.
- [ ] Ensure React never constructs canonical legacy operation strings.

### P4 - Variable parity

- [ ] Verify variable create, edit, delete, usage lookup, and reference selection in both grids.
- [ ] Make rename/type updates and dependent-operation rewrites one transaction.
- [ ] Verify bot-job and component-session parity.

### P5 - Remove remaining JavaFX routes

- [ ] Audit all callers of `INSERT_BEFORE`, `INSERT_AFTER`, `INSERT_NEW`, and `EDIT_OPERATION`.
- [ ] Ensure Insert Empty Step before/after never opens `ARNewCommandScene`.
- [ ] Remove normal UI forwarding to `ARNewCommandPane` and `ARElementValuePane`.
- [ ] Remove the legacy panes/scenes only after end-to-end acceptance passes.

### P6 - End-to-end acceptance

- [ ] Add SQLite/Playwright coverage using organization `2` and bot job `19`.
- [ ] Scan instructions, add them to Memory List, create `test_block_N`, and Apply.
- [ ] Verify persisted block and instruction rows after refresh.
- [ ] Cover IF/ELSEIF/ELSE/ENDIF, loops, variables, move, delete, and split workflows.
- [ ] Verify component-grid parity and direct WebSocket rejection behavior.

## Current Next Step

Finish and commit the central split-boundary validator, then implement split preview before starting the shared React drag controller.
