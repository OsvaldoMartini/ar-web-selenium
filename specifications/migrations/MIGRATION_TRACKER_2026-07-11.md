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

- [x] ELSEIF deletion resolves and atomically removes only the selected branch span.
- [x] LOOP/REFRESH_LOOP deletion resolves and atomically removes the parent-to-boundary span.
- [x] Normal drag/arrow movement restores authoritative state and shows the final backend refusal in both grids (`d1c622e`).
- [x] Delete previews include affected row IDs, names, actions, order, and count (`70e24eff`, `0b2f300`).
  - [x] Publish authoritative delete row IDs, names, actions, and order in capability responses.
  - [x] Render the detailed impact in both React confirmation flows.
- [x] Block deletion has graph revision validation, request idempotency, dependency impact analysis, atomic persistence, structured React responses, and no Java dialogs.
  - [x] Publish block instruction impact, external references, minimum-block protection, and disabled reasons.
  - [x] Consume block capabilities in React confirmation and request construction (`20ee7bb`).
  - [x] Validate revision/idempotency and persist block deletion atomically.
  - [x] Return structured results without Java dialogs.
- [x] Variable mutation and dependent command rewrites use one verified transaction.
- [x] Java publishes and enforces deny-by-default variable type compatibility, including crafted requests.
- [x] React filters and clears variable selections using Java `allowedVariableTypes` metadata (`bec986e`).

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
  - [x] Add SQLite mid-update rollback fixture for the extracted row mutation transaction.
  - [ ] Delegate the complete production move transaction, including block cleanup/order, to the tested service.
- [x] Wrap variable update and every dependent operation rewrite in one transaction.
- [ ] Add rollback fixtures for failed variable rewrites.

### P3 - Shared React drag controller

- [x] Create `useInstructionDrag.ts` and route primary drag/drop submissions from both grids through it (`fcb290e`).
- [x] Route arrow and Memory List move submissions through `useInstructionDrag.ts` (`6633ee3`).
- [ ] Remove duplicated inline IF/loop/move checks after backend parity is confirmed.
- [ ] Preserve scroll and expanded/collapsed block state during authoritative rejection refresh.
- [x] Add explicit capability-aware drag handles to both grids (`e621dcc`).
- [x] Add backend-driven valid/invalid drop zones (`e0b073dc`, `31aa275`).
  - [x] Publish authoritative `allowedBlockIds` for each row capability.
  - [x] Render valid/invalid block states while dragging.
  - [x] Disable invalid Droppable targets so refused destinations cannot be applied optimistically (`0b95945`).
- [ ] Complete group-move preview UX.
  - [x] Add capability reason tooltips and keyboard movement on dedicated handles (`3172806`).
  - [ ] Add connected-group move preview.

### P4 - Graph-aware deletion completion

- [x] Implement ELSEIF deletion as the selected boundary plus only its branch instructions.
- [x] Preserve the surrounding IF/ELSEIF/ELSE/ENDIF family grammar.
- [x] Implement explicit loop-group deletion without leaving a detached parent or boundary.
- [x] Return exact affected row IDs, names, actions, and counts before confirmation.
- [x] Add graph revision, request ID, impact analysis, and structured responses to block deletion.
- [x] Remove Java confirmation/error dialogs from active block deletion paths.

### P5 - Command codec and rule completeness

- [x] Complete canonical encode/decode fixtures for every registered command family.
- [x] Verify decode schemas for SET, GET, CK/PDF/CSV, GOTO, EXCEL GOTO, loops, waits, swipe, and independent commands.
- [x] Default unknown row/command combinations to denied.
- [x] Active React grids never construct canonical legacy operation strings; Java owns encoding.
- [x] Return structured warnings for malformed historical operations.
- [x] Render malformed historical operation warnings in the React command panel (`c0cbd98`).
- [x] Verify canonical aliases and every command family encode/decode parity.
- [x] Publish command-variable type compatibility and revalidate submitted variable IDs/types in Java.

### P6 - Variable parity

- [ ] Verify variable create, edit, delete, usage lookup, and reference selection in both grids.
  - [x] Add pure dependent-operation rewrite fixtures for every variable command family.
  - [x] Test React component-session variable creation and usage-protected deletion (`4ffe287`).
  - [x] Test React bot-job variable edit, reference selection, and confirmed unused deletion (`27d63c6`).
- [x] Make rename/type updates and dependent-operation rewrites one transaction (`ab80cd14`).
- [ ] Verify bot-job and component-session parity.
  - [x] Cover component-session bootstrap and variable mutation context in React tests (`4ffe287`).

### P7 - Remove remaining JavaFX routes

- [x] Audit all callers of `INSERT_BEFORE`, `INSERT_AFTER`, `INSERT_NEW`, and `EDIT_OPERATION`; remaining uses are pane-free internal mutation modes.
- [x] Insert before/after routes through the React command panel and never opens `ARNewCommandScene`.
- [x] No normal UI forwarding to `ARNewCommandPane` or `ARElementValuePane` remains.
- [x] Legacy JavaFX command/variable pane and scene classes are absent from `src/main/java`.
- [x] No active command, variable, delete, move, Memory List, or split path opens JavaFX.
- [x] Remove unreferenced `GridDrag2.tsx` and its unreachable legacy STOMP senders (`7549653`).

### P8 - Automated and end-to-end acceptance

- [ ] Add delete-impact, revision, idempotency, transaction rollback, and command-codec unit tests.
  - [x] Add React command panel coverage for codec warnings and backend variable-type filtering (`b2f9fe2`).
  - [x] Prove React Apply submits typed fields without an `operation` string (`5375075`).
- [ ] Add SQLite/Playwright coverage using organization `2` and bot job `19`.
- [ ] Scan instructions, add them to Memory List, create `test_block_N`, and Apply.
- [ ] Verify persisted block and instruction rows after refresh.
- [ ] Cover IF/ELSEIF/ELSE/ENDIF, loops, variables, move, delete, and split workflows.
- [ ] Verify component-grid parity and direct WebSocket rejection behavior.

## Current Next Step

Enforce command-variable type compatibility in Java and capability metadata, then complete command codec fixtures and JavaFX route auditing.
