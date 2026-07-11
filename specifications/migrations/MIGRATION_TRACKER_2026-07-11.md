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
- [x] Centralize split-boundary validation and remove the silent last-row adjustment (`609ac355`).
- [x] Add nested conditional and loop split-boundary fixtures (`609ac355`).
- [x] Add `instructionGraph.previewSplit` with authoritative retained/moved row details.

## Safety Gaps Preserved From Migration Notes

- [ ] ELSEIF branch deletion is safely denied, but graph-aware branch deletion is not implemented.
- [ ] LOOP/REFRESH_LOOP deletion is safely denied, but loop-group deletion is not implemented.
- [ ] Normal drag/arrow movement must wait for backend confirmation or restore authoritative state on refusal.
- [ ] Delete previews must include affected row IDs and names, not only a count.
- [ ] Block deletion still needs graph revision validation, request idempotency, dependency impact analysis, a structured React response, and removal of Java dialogs.
- [ ] Variable mutation plus dependent command rewrites still needs one transaction.
- [ ] Java must enforce variable type compatibility for every command, including crafted requests.

## Remaining Work

### P1 - Split preview and authoritative response

- [x] Make React request `instructionGraph.previewSplit` before constructing the split mutation (`3200f42`).
- [x] Return authoritative refreshed blocks and instructions after an applied split.
- [x] Make React show the actual backend split group before confirmation (`3200f42`).

### P2 - Complete mutation transaction safety

- [x] Update move order, block, and database-derived parent block in one verified transaction.
- [x] Refuse EXCEL GOTO-only move layouts before mutation.
- [x] Delete empty blocks and normalize block order inside the verified move transaction.
- [x] Send move/delete results only after validation, persistence, and authoritative refresh complete.
- [x] Cache move/delete request IDs only after successful completion; keep failed requests retryable.
- [ ] Add rollback fixtures for failed movement updates.
- [ ] Wrap variable update and every dependent operation rewrite in one transaction.
- [ ] Add rollback fixtures for failed variable rewrites.

### P3 - Shared React drag controller

- [ ] Create `useInstructionDrag.ts` shared by `GridItem.tsx` and `GridItemComp.tsx`.
- [ ] Remove duplicated inline IF/loop/move checks after backend parity is confirmed.
- [ ] Restore original state on backend rejection without losing scroll or expanded blocks.
- [ ] Add explicit drag handles and backend-driven valid/invalid drop zones.
- [ ] Add reason tooltips, group preview, and keyboard movement.

### P4 - Graph-aware deletion completion

- [ ] Implement ELSEIF deletion as the selected boundary plus only its branch instructions.
- [ ] Preserve the surrounding IF/ELSEIF/ELSE/ENDIF family grammar.
- [ ] Implement explicit loop-group deletion without leaving a detached parent or boundary.
- [ ] Return exact affected row IDs, names, actions, and counts before confirmation.
- [ ] Add graph revision, request ID, impact analysis, and structured responses to block deletion.
- [ ] Remove Java confirmation/error dialogs from active block deletion paths.

### P5 - Command codec and rule completeness

- [ ] Complete round-trip fixtures for every legacy command operation format.
- [ ] Verify typed schemas for SET, GET, CK/PDF/CSV, GOTO, EXCEL GOTO, loops, waits, swipe, and independent commands.
- [ ] Default unknown row/command combinations to denied.
- [ ] Ensure React never constructs canonical legacy operation strings.
- [ ] Return structured warnings for malformed historical operations.
- [ ] Verify canonical aliases and every command family round trip.
- [ ] Publish command-variable type compatibility and revalidate submitted variable IDs in Java.

### P6 - Variable parity

- [ ] Verify variable create, edit, delete, usage lookup, and reference selection in both grids.
- [ ] Make rename/type updates and dependent-operation rewrites one transaction.
- [ ] Verify bot-job and component-session parity.

### P7 - Remove remaining JavaFX routes

- [ ] Audit all callers of `INSERT_BEFORE`, `INSERT_AFTER`, `INSERT_NEW`, and `EDIT_OPERATION`.
- [ ] Ensure Insert Empty Step before/after never opens `ARNewCommandScene`.
- [ ] Remove normal UI forwarding to `ARNewCommandPane` and `ARElementValuePane`.
- [ ] Remove the legacy panes/scenes only after end-to-end acceptance passes.
- [ ] Confirm no active command, variable, delete, move, Memory List, or split path opens JavaFX.

### P8 - Automated and end-to-end acceptance

- [ ] Add delete-impact, revision, idempotency, transaction rollback, and command-codec unit tests.
- [ ] Add SQLite/Playwright coverage using organization `2` and bot job `19`.
- [ ] Scan instructions, add them to Memory List, create `test_block_N`, and Apply.
- [ ] Verify persisted block and instruction rows after refresh.
- [ ] Cover IF/ELSEIF/ELSE/ENDIF, loops, variables, move, delete, and split workflows.
- [ ] Verify component-grid parity and direct WebSocket rejection behavior.

## Current Next Step

Implement split preview and authoritative split responses, then verify atomic movement persistence before starting the shared React drag controller.
