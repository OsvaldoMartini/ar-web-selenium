# Phase 6 — `useInstructionGrid` State-Hook Decomposition Roadmap — 2026-07-24

Status: **PLANNED (not started).** This is the highest-risk, zero-visible-payoff refactor: move
GridItem's ~80 `useState` + handlers + effects out of the render component into a controller hook
(and focused sub-hooks). Behavior must not change. Do it **incrementally**, one medium piece at a
time, each with its own test and its own commit, so any step can be reverted independently.

> Sister roadmap: [`BOT_JOB_DETAILS_COMPONENT_DECOMPOSITION_2026_07_24.md`](BOT_JOB_DETAILS_COMPONENT_DECOMPOSITION_2026_07_24.md)
> (Phases 1–5 + 7 + 8 — the component/drag work, all done). This file is Phase 6 only.

## Backup / rollback (created before starting)

| Repo | Backup branch | Commit |
|---|---|---|
| `abr-react-ts-grid` | `backup/pre-phase6-2026-07-24` | `de7607f` (from `VERSION-4.6`) |
| `ar-web-selenium` | `backup/pre-phase6-2026-07-24` | `88079b6f` (from `refactor/perform-actions-decomposition`) |

**If a step goes wrong:** revert just that step's commit (`git revert <sha>`), or reset the file
(`git checkout backup/pre-phase6-2026-07-24 -- src/components/GridItem.tsx …`), or in the worst case
`git reset --hard backup/pre-phase6-2026-07-24`. Because every step is its own commit + its own hook
file, a bad extraction never forces losing the good ones.

## Goal & why

- **`GridItem.tsx` (~3,000 lines) → a thin orchestrator** (a few hundred lines) that renders and wires
  props, with all state/logic in `useInstructionGrid` (composed of focused sub-hooks).
- **Testable state logic** in isolation (via `renderHook`), not only through full-component render.
- **DRY across the twins:** `GridItemComp` (Components workspace) is a near-copy of `GridItem`. Once
  the hook exists, both grids can share it (or most of it), collapsing thousands of duplicated lines.
- **No functional change.** This is purely internal; the UI and messages must behave identically.

## Non-negotiable process (per sub-hook)

1. **Extract** one logical state group into `bot-job-details/grid/hooks/<useX>.ts`, returning a typed
   object of state + actions. Move the related `useState`/`useRef`/handlers verbatim.
2. **GridItem consumes it**: `const x = useX(deps)` and replace inline usages with `x.field` /
   `x.action(...)`. No behavior change.
3. **Test the hook** with `renderHook` (from `@testing-library/react`) — assert initial state + each
   action's effect. This is the "test per medium piece" gate.
4. **Verify**: `npx tsc --noEmit` clean; existing grid + `GridItem.dragMessages` regression tests still
   green; surgical GridItem diff (watch the CRLF gotcha — restore CRLF after any `sed`).
5. **Commit** `CLAUDE: phase6 — extract <useX>` (one hook per commit). Build/deploy only at phase end.

## State inventory (grouped) — from GridItem.tsx

- **UI/dropdown:** `openDropdown`, `dropdownRef`, `dropdownPosition`, `mockData`, `isDataReordered`
- **Editing:** `editingInstructionId`, `instructionName`, `instructionRef`, `editingBlockId`,
  `blockName`, `blockRef`
- **Alerts:** `errorFlag`, `alertImage`, `alertClass`, `alertMessageHeader/Body/Footer`,
  `alertDismissed`, `pendingDeleteBlockId`, `alertOnConfirm`
- **Execution:** `executionId`, `executionState`
- **Find:** `findText` (+ `instructionMatchesFind`, `renderHighlighted`)
- **Collapse:** `collapsedBlocks` (+ `toggleBlockCollapsed`)
- **Excel export:** `excelExportContext`, `excelExportDirectory`, `choosingExcelExportDirectory` + refs
- **Save component:** `saveComponentContext`
- **Memory list:** `memorySteps`, `memoryTargetBlockId`, `memoryBlockOptions`, `createBlockOpen`,
  `memoryCapabilities`, `pendingMemoryMove`, `memoryMoveStatus`, `memoryListOpenVersion` + refs +
  `requestMemoryListOpen`, `handleAddToMemory`, `handleAddBlockToMemory`, `handleRemoveFromMemory`,
  `handleApplyMemory`
- **Drag:** `activeDraggedInstructionId`, `pendingDragPreview`, `moveGraphRevision`,
  `submitInstructionMove`, `dragSourceRef`, `dragBlockRef`, `commitInstructionDrag`,
  `commitBlockReorder`, `onDragEnd`, `applyDragMove`, `sortedBlockIndex`, handlers
- **Core data:** `groupedData`, `instructionsData`, `updatedBlocks`, the WebSocket message-handling
  effect, and the block/instruction mutation handlers

## Extraction order (lowest-risk first → riskiest last)

Each `[ ]` = extract hook + `renderHook` test + `tsc`/regression green + commit.

### Step 1 — `useInstructionFind` (pure, isolated — safest first)
- [ ] `findText` + `setFindText`; expose `instructionMatchesFind(instruction, query)` and
      `renderHighlighted(text, query)`. No side effects. Easiest to test; proves the pattern.

### Step 2 — `useBlockCollapse`
- [ ] `collapsedBlocks` Set + `toggleBlockCollapsed(blockId)` + `isCollapsed(blockId)`.

### Step 3 — `useGridAlerts`
- [ ] All alert fields + `showAlert({...})`, `dismiss()`, `confirm()`, delete-confirmation flow
      (`pendingDeleteBlockId`, `alertOnConfirm`). Pure UI state.

### Step 4 — `useExecutionState`
- [ ] `executionId`/`executionState` + the setter used by the execution WS frames. Small.

### Step 5 — `useInstructionMemory` (medium)
- [ ] `memorySteps`, `memoryCapabilities`, `memoryTargetBlockId`, `memoryBlockOptions`,
      `createBlockOpen`, `pendingMemoryMove`, `memoryMoveStatus`, memory-list-open refs/version +
      `requestMemoryListOpen`, `handleAddToMemory`, `handleAddBlockToMemory`, `handleRemoveFromMemory`,
      `handleApplyMemory`. Test add/dedupe/remove/apply against a fake capability map.

### Step 6 — `useExcelExport` (medium)
- [ ] Excel export context/dir/choosing + pending refs + the export handlers.

### Step 7 — `useInstructionDragController` (medium)
- [ ] `activeDraggedInstructionId`, `pendingDragPreview`, `moveGraphRevision`, `dragSourceRef`,
      `dragBlockRef`, `commitInstructionDrag`, `commitBlockReorder`, `sortedBlockIndex`, `onDragEnd`,
      `applyDragMove`, and the native drag handlers. Reuses `useInstructionDrag` (already a hook).
      Test: `__gridReorder`/`__blockReorder` equivalents synthesize the right `previewMove`/`BLOCK_MOVE`.

### Step 8 — `useBlockMutations` (medium-large)
- [ ] Block CRUD: create/rename/delete/status/excel-file/create-component/rollback + `blockName`,
      `editingBlockId`, `blockDeleteCapabilities`. Depends on core data + WS send.

### Step 9 — `useInstructionMutations` (medium)
- [ ] Instruction CRUD: save/remove/status + `editingInstructionId`, `instructionName`, `handleMoveRowUp/Down`.

### Step 10 — `useGridData` (CORE — largest, riskiest, LAST)
- [ ] `instructionsData`, `groupedData`, `updatedBlocks`, and **the big WebSocket message-handling
      effect** (capability responses, previews, block/instruction refresh). This is the heart; extract
      only after everything above is stable. Heavy `renderHook` + message-injection tests.

### Step 11 — `useInstructionGrid` (compose)
- [ ] Top-level hook that wires steps 1–10 together and returns the full view-model GridItem needs.
      `GridItem` becomes a thin renderer consuming `useInstructionGrid(props)`.
- [ ] `instructionGrid.types.ts` — shared row/block/prop/view-model types used by the hooks + components.

### Step 12 — (optional) share with `GridItemComp`
- [ ] Point the Components workspace grid at the same hook (parameterized by table `component_block` /
      session `componentTasks`), collapsing the twin. Big DRY win; do only after `GridItem` is proven.

## Acceptance criteria

- **Behavior identical** at every step (UI + WebSocket messages). The `GridItem.dragMessages`
  regression + all `bot-job-details/grid/*` tests stay green.
- **Each hook has a `renderHook` test** covering initial state + each action.
- `npx tsc --noEmit` clean; surgical GridItem diffs (CRLF preserved).
- `GridItem.tsx` shrinks materially each step; final `GridItem` is a thin renderer.
- One hook per commit; build/deploy once at phase end.

## Saved landscape (context for this decision, 2026-07-24)

The user asked to record the full "what's left" review that led here:

- **Done (Phases 1–5, 7, 8):** 16 `grid/` components, native instruction + block drag & drop,
  react-beautiful-dnd fully removed, unified `BLOCK_MOVE` (full ordered list; backend
  `updateSwiftBlockOrderNumber` already iterates any-size list — no backend change).
- **"Resolved-NA"** = planned items that need **no work**: `InsertStepDropdown` (dead/legacy — the
  Insert menu is commented out; `setOpenDropdown` only ever clears) and `InstructionActionMenu` (folded
  into `InstructionRow` as the micro-components). `EmptyBlocksPlaceholder` is *deferred* (low value),
  not NA.
- **Remaining big refactors:** (1) **Phase 6** — this document (high risk, no visible payoff, but the
  correct architecture finish and the key to sharing logic with `GridItemComp`). (2) **`GridItemComp`
  block drag + unified `BLOCK_MOVE`** — bounded, medium risk, makes both grids match.
- **Recommendation at the time:** do `GridItemComp` block drag first (bounded win), tackle Phase 6 as
  its own careful incremental effort (this roadmap). The user chose to start Phase 6.

## Review ledger

| Reviewer | Status | Notes |
|---|---|---|
| Claude | Roadmap authored + backups created | 12 steps, lowest-risk-first, per-hook tests, rollback plan. Awaiting go to start Step 1. |
| CODEX | Pending | Validate the state groupings + extraction order; flag any cross-group coupling (esp. the WS effect in Step 10). |
