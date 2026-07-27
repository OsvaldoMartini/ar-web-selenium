# Bot Job Details — Component Decomposition Roadmap (GridItem.tsx) — 2026-07-24

Status: **IN PROGRESS — Phase 1 is 4/5 complete.** Claude authored the initial roadmap and extracted
`FindBar` plus `BlockCollapseToggle`; CODEX reviewed/corrected those leaves and extracted
`BlockStatusToggle` plus `ExecutionStateOverlay`. This is a shared CLAUDE ⇆ CODEX roadmap — update
this file in place rather than forking a parallel plan.

## Goal

Break the monolithic **`abr-react-ts-grid/src/components/GridItem.tsx`** (the Bot Job Details
instruction grid) into many **small, single-responsibility React/TypeScript components**, each in its
own file with its **own `.module.scss`**, styles preserved exactly. The Block becomes a component
that accepts params; rows, headers, menus, editors, and the find bar each become their own
component. Once every piece is isolated, implement a **high-context native drag & drop** (the final
phase) — easier and safer to reason about when each unit is small and owns its own handlers.

Guiding principle from the requester: *"never is late to refactor."* Extract medium-sized code into
individual components; separate as much as possible; one `.module.scss` per component; preserve all
styles; drag & drop is the **last** task.

## Premise / current state

| Fact | Value |
|---|---|
| `GridItem.tsx` | **3,705 lines** — Bot Job Details instruction grid (blocks + instructions) |
| `GridItem.module.scss` | **1,570 lines**, **209 classes** |
| State hooks in `GridItem` | ~80 `useState` (blocks, instructions, memory, drag, find, collapse, execution, editing, excel, alerts) |
| Drag today | react-beautiful-dnd (`DragDropContext`/`Droppable` per block/`Draggable` per instruction) → `onDragEnd(result)` |
| Reorder logic | Already **decoupled** from rbd: Up/Down buttons build the same `result` and call `onDragEnd` (`GridItem.tsx:2146–2165`) |
| Existing extraction pattern | `src/components/bot-job-details/` already holds ~15 components + `.module.scss` + tests (Chrome, Header, MetadataPanel, ExecutionControls, FileActions, DataActions, ComponentWorkspaceHeader, controller hook…) — **build on this** |
| Memory List | Already decomposed + native drag done (see `CLAUDE_vs_CODEX_MIGRATION_CHECKS_2026_07_12.md`) — the template for this work |

`GridItem.tsx` is the largest remaining un-decomposed screen. Everything else in Bot Job Details
(header, metadata, toolbar, file/data actions) was already extracted by CODEX into `bot-job-details/`.

## Non-negotiable principles

1. **One component per file, one `.module.scss` per component.** No shared mega-scss. When a class
   moves out of `GridItem.module.scss`, copy the exact rules (and any nested selectors it depends on)
   into the new component's module — **byte-for-byte style preservation**. Visual parity is a
   release gate.
2. **Params-driven, presentational-first.** Extracted components receive data + callbacks via props;
   they hold no WebSocket/business logic. State stays in `GridItem` (or a controller hook) and is
   passed down. This keeps each extraction a pure, reviewable move.
3. **Leaf-first extraction order.** Extract the innermost, lowest-risk pieces first (buttons, badges,
   editors), then composites (row → list → block), then hoist state into a hook. Never extract a
   parent before its children.
4. **No behavior change per step.** Each phase is a mechanical move: same DOM, same classes, same
   handlers wired the same way. `npx tsc --noEmit` clean + visual parity after every component.
5. **Do not block other parts / minimal diff.** Only `GridItem` and the new files change per step.
   Memory List, `bot-job-details/*`, and unrelated screens are untouched. rbd stays in
   `package.json` (other files may still use it) until the last drag phase removes it from this tree.
6. **Drag & drop is the FINAL phase.** Until then, the existing rbd drag keeps working unchanged.
7. **Build discipline.** React only: after edits run `npm run build`, wipe
   `ar-web-selenium/src/main/resources/build`, copy the fresh build in. **Do not build the Java
   jar** (user owns that). Commit React with a `CLAUDE…`/`CODEX…` prefix per author; deployed build
   with an `FE:` prefix; keep them separate commits.

## Target folder structure

```
abr-react-ts-grid/src/components/bot-job-details/grid/
  InstructionGrid.tsx / .module.scss        # thin orchestrator (was GridItem's render)
  BlockCard.tsx / .module.scss              # ONE block, accepts params
  BlockHeader.tsx / .module.scss            # active toggle, collapse, order#, name, up/down, delete, excel
  BlockNameEditor.tsx / .module.scss        # inline block-name edit
  BlockStatusToggle.tsx / .module.scss      # active/inactive image button
  BlockCollapseToggle.tsx / .module.scss    # collapse badge (wraps existing CollapseToggleIcon)
  InstructionList.tsx / .module.scss        # per-block list container (droppable in final phase)
  InstructionRow.tsx / .module.scss         # ONE instruction row
  InstructionDragHandle.tsx / .module.scss  # ≡ handle + Alt+Arrow keyboard move
  InstructionNameEditor.tsx / .module.scss  # inline instruction-name edit
  InstructionActionMenu.tsx / .module.scss  # per-row menu (edit, delete, add-to-memory, command editor, status)
  InsertStepDropdown.tsx / .module.scss     # "Insert New Step" dropdown
  ExecutionStateOverlay.tsx / .module.scss  # execution background overlay
  FindBar.tsx / .module.scss                # find/search input + Memory-list reopen action
  EmptyBlocksPlaceholder.tsx / .module.scss # "No blocks were created yet"
  useInstructionGrid.ts                      # state + handlers controller hook (final hoist)
  instructionGrid.types.ts                   # shared row/block prop types
  __tests__/ …                               # one focused test per component
```

`GridItem.tsx` shrinks to a thin shell that mounts `<InstructionGrid …/>` (or is renamed), so
existing imports keep working during the migration.

## Phased task list

Each `[ ]` = create the component **+ its own `.module.scss` (styles preserved) + a focused test**,
`tsc` clean, visual parity, commit `CLAUDE…`/`CODEX…`. Build+deploy at the end of each phase.

### Phase 1 — Leaf presentational (no state, props only) — lowest risk
- [x] `FindBar` — find input, clear, and the existing Memory-count reopen action. The detached
      Memory List remains responsible for move status; no result count existed in the original row.
- [x] `BlockStatusToggle` — active/inactive image button (`handleBlockStatus` via prop).
- [x] `BlockCollapseToggle` — collapse badge wrapping existing `CollapseToggleIcon`.
- [x] `ExecutionStateOverlay` — execution background div keyed by `executionState`.
- [~] `EmptyBlocksPlaceholder` — **DEFERRED to Phase 5** (Claude, 2026-07-24). It reuses 9 shared
      block-chrome classes (`.block`, `.blockHeader`, `.blockName`, `.blockOrderNumber`,
      `.instructionsList`, `.dropdownMenu`, `.dropdownAbove`, `.instructionItem`, `.noDataMessage`)
      plus `dropdownRef`/`dropdownPosition`. Extracting now would duplicate block chrome (DRY + parity
      risk). Do it after `BlockCard`/`BlockHeader` own those classes. Phase 1 leaves are otherwise done.

### Phase 2 — Inline editors (controlled inputs)
- [x] `InlineNameEditor` **(replaces both `BlockNameEditor` + `InstructionNameEditor`)** — Claude,
      2026-07-24. The two edit branches were byte-identical (input + save), so one shared control
      serves both call sites. Owns `.editContainer`→`.container`, `.editTextbox`→`.input`, and the
      effective `.saveButton` cascade →`.save`. `.editContainer`/`.editTextbox` removed from
      `Griditem.module.scss`; `.saveButton` kept there (a non-editor save at GridItem.tsx:3410 still
      uses it). Enter-in-input and save-icon-click both fire `onSave`. tsc clean; grid tests 19/19.

  **Architectural note (for CODEX):** the block **name/count VIEW** styling is descendant-scoped
  (`.blockHeader .blockName`, `.blockHeader .blockCount`) and shared with the deferred
  `EmptyBlocksPlaceholder`, so those views must be extracted **with `BlockHeader`** (Phase 5), not
  piecemeal — a standalone `BlockNameEditor` would break the descendant selector. Instruction name
  view uses a standalone `.instructionName`, so it can move with `InstructionRow` (Phase 4). Only the
  shared EDIT control was cleanly extractable now; hence `InlineNameEditor` instead of two editors.

### Phase 3 — Menus / dropdowns (re-scoped after investigation, Claude 2026-07-24)
- [x] `CommandEditorButton` — the per-row command-editor arrow (`.dropdownColumn`/`.dropdownArrow`
      → `.column`/`.arrow`). Only genuinely-standalone menu-ish leaf in this area. Both classes
      removed from `Griditem.module.scss`; `menuDownImage` import removed from GridItem. tsc clean;
      grid tests 21/21.
- [~] `InsertStepDropdown` — **NO EXTRACTION: dead/legacy code.** `setOpenDropdown` is only ever
      *cleared* (GridItem.tsx:746, 1209, 1659, 2045, 2269); the "Insert New Step" menu lives only in
      commented-out / empty-placeholder markup. The `.dropdownMenu` class is dormant. Nothing to
      extract; leave as-is (or delete the dead markup in a separate cleanup task).
- [~] `InstructionActionMenu` — **FOLDED INTO Phase 4 (`InstructionRow`).** The row actions
      (`renderMoveButtons`, `renderTestClick`, memory-add `+`, delete `crossButton`, active/inactive
      toggle, command-editor arrow) are inline/helper-rendered and coupled to the row; they extract
      *with* `InstructionRow`, not as a standalone menu.

### Phase 4 — Instruction row
- [x] `InstructionDragHandle` — the `≡` button + Alt+Arrow keyboard move. Claude, 2026-07-24. Owns
      row-exclusive `.dragHandle` (+ states) → `.handle`, removed from `Griditem.module.scss`. Accepts
      the current drag lib's `dragHandleProps` (rbd today; Phase 7 swaps the source). tsc clean; grid 24/24.
- [x] `InstructionRow` — DONE (see the completed entry lower in this Phase-4 block). The shared-styles
      "blocker" was resolved by importing GridItem's CSS module. (This bullet superseded.)

  **Shared-styling blocker (Claude, 2026-07-24) — needs a decision before `InstructionRow`/`BlockHeader`:**
  The row and the block header **share many classes and a bundled button-group selector**, so neither
  can own its full `.module.scss` in isolation:
  - Shared with the block header: `.memoryAddButton` (block "+" at GridItem.tsx:3358 and row `+`),
    `.activeButton`/`.inactiveButton`, `.moveButtons`, `.crossButton`, `.optionsColumn`.
  - One selector bundles ~14 buttons: `.moveButton, .rollbackButton, .garbageButton, .editButton,
    .excelButton, .crossButton, .saveButton, .pickButton, .warningButton, .testButton, .activeButton,
    .inactiveButton, .arrowLeftButton, .brickButton { … &:hover { scale(1.4) } }`. Splitting one button
    out means duplicating or breaking that rule.
  - The row also pulls in 6 GridItem helper renderers (`getInstructionTypeElement`, `renderOperations`,
    `renderDeviceOptionsRow`, `renderEditButton`, `renderMoveButtons`, `renderTestClick`).

  **RESOLUTION (user directive, 2026-07-24): duplication is fine — prefer MICRO-COMPONENTS over
  DRY, never break the design.** So instead of a risky GridItem-wide `gridShared` rewrite, extract the
  row's children as small components that each duplicate the few styles they need into their own
  `.module.scss`. The `.instructionItem` 5-col CSS grid + its descendant selectors stay in GridItem
  (untouched → no layout risk) until the whole row/`BlockHeader` move out. Micro-components landed so
  far:
  - [x] Row active/inactive toggle now **reuses `BlockStatusToggle`** (identical images/styles) —
        removed unused `activeImage`/`inactiveImage` imports. Claude.
  - [x] **`MemoryAddButton`** (shared `+`) — used by BOTH the block header and instruction rows;
        `.memoryAddButton` removed from `Griditem.module.scss`. Claude. tsc clean; grid 26/26.
  - [x] **`DeleteButton`** (shared `✕`) — used by ALL THREE delete sites (block header, instruction
        row, Excel GOTO row); removed unused `crossImage` import. `.crossButton` left as harmless
        dead style to avoid touching the shared button-group selector. Claude. tsc clean; grid 28/28.
  - [x] **`InstructionTypeBadge`** (`getInstructionTypeElement`, ~194 lines) — Claude. Owns its own
        24 image imports + duplicated `.operations`/`@extend` image classes + `.instructionType`;
        imports `instructionDisplayLabel` directly; find-highlighting via a `renderHighlighted` prop.
        Removed the function + 20 now-unused image imports from GridItem (net −209 lines). tsc clean;
        grid 31/31. **EOL LESSON:** `sed -i` flipped the CRLF file to LF (full-file 3360+/3569− churn);
        fixed by restoring CRLF (`sed 's/\r$//; s/$/\r/'`) → real diff 6+/215−. For big line-range
        deletions on this CRLF file, restore CRLF before committing (or use a CRLF-safe editor).
  - [x] **`InstructionRow`** container — Claude. The whole `<Draggable>` body composes all the row
        sub-components; GridItem now renders `<InstructionRow/>` passing the 5 helper outputs
        (operations/device/edit/move/test) as `ReactNode` props + bound handlers. To preserve the
        `.instructionItem` 5-col grid + descendant rules (incl. `.instructionItem .instructionDetails`
        on the helper nodes), InstructionRow **imports GridItem's CSS module** (same scoped names →
        design identical, no duplication). Removed 6 now-unused GridItem imports. Surgical
        (GridItem 27+/100−, net −73). tsc clean; grid 34/34. **→ Phase 4 COMPLETE.**

  **CODEX review addressed:** the FindBar EOL-only churn note is fixed — subsequent commits keep
  surgical diffs (this batch: GridItem.tsx 11+/29−, no EOL explosion). `gridShared` is now OPTIONAL
  (superseded by the duplication directive); revisit only if duplication becomes unwieldy.

### Phase 5 — List + Block composites
- [x] `InstructionList` — Claude. Per-block rbd Droppable: owns the drop-zone highlight, the row-level
      find-hide filter, the EXCEL-GOTO skip, the Draggable wrapper, and the placeholder. GridItem
      passes a `renderRow` render-prop that still builds each `InstructionRow` (keeps the ~24
      state-derived props in GridItem). Imports GridItem's CSS module for `.instructionsList`/valid
      -invalid drop-zone. Removed `Droppable`/`Draggable` from GridItem's rbd import (only
      `DragDropContext` remains). Surgical (GridItem 39+/72−). tsc clean; grid 37/37.
- [x] `BlockHeader` — Claude. The full header row (status/collapse toggles, order#, name view/edit,
      count, add-to-memory `+`, export-file label, and the move/edit/excel/save/delete controls).
      Composes the sub-components; imports GridItem's CSS module to preserve the `.blockHeader`
      descendant rules (`.blockName`/`.blockCount`/`.moveButtons`). Excel-GOTO badge + export-file
      passed as node props (logic stays in GridItem). Removed 7 now-unused GridItem imports. Surgical
      (GridItem 51+/123−, net −72). tsc clean; grid 40/40.
- [x] `BlockCard` — Claude (done once it earned its keep). Wraps the `.block` div + `header` node +
      collapsed `list` node, AND is the unit for the **new block-level drag & drop**: the header is a
      native block-drag source, the card is the block drop target. GridItem's `commitBlockReorder`
      reassigns every block's `blockOrderNumber` and sends the existing `BLOCK_MOVE` with the full new
      order; `window.__blockReorder(from,to)` + `[Block][drag]` logs. Block vs instruction drags are
      kept independent (block drop ignores non-block drags). tsc clean; grid 46/46. **NEW FEATURE —
      needs user runtime verification** (BLOCK_MOVE round-trip). Bundle `main.4a8cc6f7.js`.

### Phase 6 — State controller hook (hoist logic out of the render)
- [ ] `useInstructionGrid.ts` — move the ~80 `useState` + handlers into a controller hook returning a
      typed view-model. `InstructionGrid`/`GridItem` become thin. WebSocket wiring stays here,
      unchanged. Split into sub-hooks if it helps (`useBlockMutations`, `useInstructionMemory`,
      `useInstructionFind`).
- [ ] `instructionGrid.types.ts` — shared prop/row/block types consumed by all `grid/*` components.

### Phase 7 — High-context native drag & drop (FINAL) — Claude, 2026-07-24
Swapped rbd for native HTML5 drag at the isolated `InstructionList`/`InstructionRow` seam (the
Memory List pattern), reusing the existing `onDragEnd(result)` and its preview-move flow verbatim.
- [x] `InstructionRow` — whole-row native `draggable` + `onDragStart`/`onDragOver`/`onDrop`/`onDragEnd`;
      dropped the rbd `provided`. `InstructionList` — plain native drop-zone div (no Droppable/Draggable),
      keeps drop-zone highlight + find-hide filter + EXCEL-GOTO skip. On drop, GridItem synthesizes the
      **exact rbd result shape** `{ draggableId, source:{droppableId,index}, destination:{droppableId,index} }`
      and calls the **unchanged `onDragEnd`** → `instructionGraph.previewMove` backend round-trip intact.
- [x] Move validation preserved: `draggable={!dragDisabled}` (findText/`canMove`), and `onDragEnd`'s
      existing `allowedBlockIds` check + not-allowed alert are untouched; valid/invalid drop-zone
      highlight preserved via `activeDraggedInstructionId`.
- [x] Added `window.__gridReorder(instructionId, destBlock, destIndex)` (looks up the source) +
      `[Grid][drag]` GRABBED/DROP/RELEASED logs.
- [x] Removed `DragDropContext` + the rbd import from GridItem. tsc clean; grid 40/40. Built/deployed
      (`main.3821ed98.js`).
- [ ] **USER: runtime-verify in the app** (rebuild jar) — drag within a block and across blocks;
      confirm the reorder persists and disallowed drops still show the alert. The preview-move flow
      can't be exercised without the backend. If the drop index is off-by-one, the `[Grid][drag]` logs
      + `__gridReorder` pinpoint it and it's a one-line tweak in `commitInstructionDrag`.
- [x] **react-beautiful-dnd FULLY REMOVED (2026-07-24).** Converted the last importers to native:
      `GridItemComp` (Components workspace), `MemoryDragDemo`, `InstructionDragHandle` (rbd type dropped);
      deleted 3 dead rbd files (BlockList, BlockInstructionsDnd, MyComponent); removed the dep from
      `package.json`. `GridItem.dragMessages` regression drives native drag and passes for BOTH grids.
      (node_modules still physically holds rbd — npm ERESOLVE blocked the uninstall; harmless, pruned by
      a future `npm install`.) Build succeeds; grid + dragMessages 46/46.

### Phase 8 — Block-level drag & drop + unified BLOCK_MOVE (2026-07-24)
- [x] `BlockCard` block drag & drop (see Phase-5 entry): drag a block header, drop on another block.
- [x] **Unified block reordering to ONE code path.** `handleMoveBlockUp`/`handleMoveBlockDown` (the
      up/down buttons) now delegate to `commitBlockReorder` just like drag & drop, so EVERY block move
      sends a single `BLOCK_MOVE` with the **full ordered block list** (`blockOrderNumber` reassigned
      1..N), never a 2-block swap. `window.__blockReorder(from,to)` tests it. tsc clean; tests green.
- [x] **Backend `BLOCK_MOVE` confirmed compatible — NO change needed.** `SimpleWebSocketServer`
      BLOCK_MOVE → `PerformDataBase.updateSwiftBlockOrderNumber` iterates the whole `updatedBlocks`
      list (any size) with a batched `UPDATE block SET block_order_number = ? WHERE id = ?`
      (+ `updateMemorySwiftBlockOrder` for the in-memory list). It never assumed a 2-item swap; the
      full ordered list is a drop-in.
- [ ] **USER: runtime-verify** block reorder (buttons + drag) in the app after a jar rebuild.
- [ ] Optional: apply the same block drag + unified BLOCK_MOVE to `GridItemComp` (Components workspace).

## Acceptance criteria (per phase and overall)

- `npx tsc --noEmit` clean for all touched files (pre-existing `node_modules/i18next` TS errors are
  unrelated and ignored).
- **Visual parity**: the screen looks identical after each extraction (styles moved, not changed).
- No behavior change until Phase 7; drag keeps working via the untouched `onDragEnd` throughout.
- Each new component has a focused test (render + key interaction).
- After Phase 7: drag/reorder within and across blocks works with a real mouse in the foreground
  window; `window.__gridReorder` reorders in any tab state; capability validation still blocks
  disallowed moves.
- `GridItem.module.scss` shrinks toward empty as classes migrate; no orphaned/duplicated rules.

## Review ledger (fill on each pass)

| Reviewer | Status | Notes |
|---|---|---|
| Claude | Phase-1 leaves submitted | `FindBar` in `d0b82a2`/`0780216`; `BlockCollapseToggle` in `35184e4`. |
| CODEX | Reviewed; approved after corrections | Restored the collapsed-state class contract, corrected FindBar's compiled-style parity, moved its existing Memory action, and completed the next two leaves in `eb7b4db`. Claude's FindBar commit contains avoidable EOL-only churn; future extractions must keep surgical diffs. |

## Phase 1 evidence — 2026-07-24

- Frontend commits reviewed: `d0b82a2`, `0780216`, `35184e4`.
- CODEX corrections and next two tasks: `eb7b4db`.
- Focused React verification only: 4 suites, 15 tests passed
  (`FindBar`, `BlockCollapseToggle`, `BlockStatusToggle`, `ExecutionStateOverlay`).
- `npm run build`: successful with pre-existing repository lint warnings.
- Deployed resources: `main.d40f66d4.js`, `main.f87986ad.css`; source and destination each contain
  45 files with identical relative-path SHA-256 sets.
- Maven/Java compilation was intentionally not run under the repository standing rule.

## Open questions for CODEX

- Should `grid/` live under `bot-job-details/` or as a sibling `instruction-grid/` folder?
- Is a controller hook (Phase 6) preferred over a small context provider for the ~80 state values?
- Any block/instruction rendering paths (nested IF/LOOP, EXCEL GOTO hidden rows) that need their own
  component rather than living inside `InstructionRow`?
- Should the scss split introduce a shared `grid/_tokens.scss` for colors/spacing, or keep each
  module fully standalone (current principle #1)?

## Components page (GridItemComp) status — 2026-07-26 (shared CLAUDE/CODEX note)

**GridItemComp was NOT decomposed.** It remains a monolith that only ADOPTED shared hooks:
`useExcelExport`, `useGridAlerts`, `useInstructionFind`, `useBlockReorder` (generic, `targetSessionId:
'componentTasks'`). GridItem (Bot Job Details) is the fully decomposed, 100%-working reference.

### Instruction move/drag bug — root cause + fix (CLOSED, pending runtime confirm)
- Line-by-line comparison of the full instruction-move chain (capabilities request → response handler →
  `moveGraphRevision`/`moveCapabilities` → draggable gate → `onDragEnd` → `previewMove` →
  `previewMoveResponse` → `applyDragMove` → `useInstructionDrag` ROW_MOVE) confirmed GridItemComp is
  IDENTICAL to the working useGridData path — except one divergence:
- **Root cause:** a defensive `homeBankingId > 0` guard (added 2026-07-26, bundle `main.7d2e0df4.js`)
  on GridItemComp's `instructionEditor.memoryCapabilities` request. The Components workspace is
  bank-agnostic and legitimately runs with `homeBankingId = 0`, so the guard suppressed the request
  permanently → empty `moveCapabilities` + empty `moveGraphRevision` → row up/down disabled AND rows
  not draggable (block reorder needs neither → kept working).
- **Fix:** guard removed (commit `34c5531`); bundle back to `main.420900ed.js` — byte-identical to the
  pre-guard bundle on which drag verifiably reached the backend (user saw the validator's "Move
  Instruction Refused", since fixed in Java by #7 Fix A/B: family moves together + touched-family gate).
- **Verify at runtime:** rebuild jar (embeds `main.420900ed.js`) → Components page → row up/down enabled
  and drag works, cross-block moves of web-field families prompt "Move N connected instructions?".

### Contingency (user-approved fallback if runtime test still fails): COMPONENTS_2
If GridItemComp still misbehaves after the fix, STOP patching the monolith. Instead:
1. Back up the current Components page (`GridItemComp.tsx` + its scss usage) untouched.
2. Create **COMPONENTS_2**: a 100% copy of the decomposed, working GridItem stack
   (`useInstructionGrid`/`useGridData` composition + `InstructionRow`/`InstructionList`/`BlockCard`),
   with its own `.module.scss`, then change ONLY the context lines: DTO `ComponentsInstructionsDTO`,
   session/target `componentTasks`, refresh verb `componentsUpdate` (vs `updateInstructions`),
   command editor `commandEditor.apply`/`insertElseIf` (vs `workspaceOpen`), Comp-only features
   (`COMPONENT_INJECT` → targets 'botJobTasks'; `ACTIONS_UPDATE`), and NO Memory-List/BLOCK_CREATE/
   save-as-component (Bot-Job-only features). See BOTJOB_VS_COMPONENT_CONTEXT_MAP.md for the full
   difference table. GridItem itself must NOT be modified.

## 2026-07-26 — STATUS: CLAUDE FAILED to fix GridItemComp instruction drag & drop (honest record)

**Outcome:** despite multiple attempts across the day, the user reports GridItemComp (Components page)
instruction move (row up/down) and drag & drop are STILL NOT WORKING at runtime. Recording the failure
for the shared CLAUDE/CODEX ledger so the next session (or CODEX) starts from facts, not claims.

### What was attempted (all committed & pushed, latest bundle main.adba306f.js in resources/build)
1. Backend #7: web-field family grouping fix (InstructionMoveGroupService) + touched-family gate
   (InstructionMoveValidator) — addresses "must remain in their parent block" refusals.
2. FE: removed CLAUDE's own bad homeBankingId>0 guard that suppressed the memoryCapabilities request
   (a self-introduced regression, found and owned); line-by-line parity check vs the working GridItem
   (all 9 links of the move chain structurally identical).
3. Diagnostics: [CompGrid] permanent console trace across the whole FE move chain; new
   ar_web_scanner_backend.log capturing BOTH directions (REQ incoming verbs + RES outgoing sends).
4. Full FE→BE pipeline separation per user directive: new COMPONENT_ROW_MOVE verb +
   useComponentInstructionDrag (FE) + ComponentRowMoveService / BotJobRowMoveService (BE, one method
   per concern, own idempotency registries); legacy ROW_MOVE from a component session routes safely to
   the component service. Dead code removed (processedRowMoves, updateMemoryRowMove+applyUpdates).
5. Tests: ComponentRowMoveRealDbTest (real-data pipeline on a temp DB copy, the user's 2 scenarios),
   RowMoveServicesTest (request-id gates). NOT yet run by the user at the time of writing.

### Diagnosis state (unproven at runtime)
The FE pipeline is structurally identical to the 100%-working GridItem; prime suspect remains the
componentTasks capabilities round-trip / runtime data (moveCapabilities/moveGraphRevision empty), but
NO runtime [CompGrid] console output or ar_web_scanner_backend.log has been captured yet to confirm.
The runaway ~19 req/s DB-reload loop is also still unidentified (backend log will name it).

### Next session — agreed path
1. User rebuilds jar; captures [CompGrid] console lines + ar_web_scanner_backend.log while trying a
   row move in Components; runs mvn -Dtest=ComponentRowMoveRealDbTest test.
2. If the evidence does not produce an immediate fix: EXECUTE THE COMPONENTS_2 FALLBACK (approved by
   the user, spec'd above): back up the Components page and build a 100% copy of the decomposed,
   working GridItem stack with its own .module.scss, changing only the context lines
   (ComponentsInstructionsDTO, componentTasks, componentsUpdate, commandEditor.apply/insertElseIf,
   COMPONENT_INJECT, no Memory-List/BLOCK_CREATE). GridItem must not be modified.
