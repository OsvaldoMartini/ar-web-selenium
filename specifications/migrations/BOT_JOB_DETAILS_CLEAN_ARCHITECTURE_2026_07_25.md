# Bot Job Details Grid — Clean Architecture Blueprint — 2026-07-25

Senior-architect target for the Bot Job Details instruction grid (`GridItem.tsx` and its twin
`GridItemComp.tsx`). **No product behavior changes** — this is purely separation of concerns,
modularity, decoupling, and scalability. It is the architecture that Phase 6 (`useInstructionGrid`
state-hook decomposition) is executing; see
[`PHASE6_USE_INSTRUCTION_GRID_ROADMAP_2026_07_24.md`](PHASE6_USE_INSTRUCTION_GRID_ROADMAP_2026_07_24.md).

## The problem (before)

`GridItem.tsx` was a ~3,700-line "god component": presentation, ~80 `useState`, business rules,
WebSocket message parsing/sending, and DTO shaping all interleaved. `GridItemComp.tsx` is a ~3,000-line
near-duplicate. Tight coupling → untestable logic, high change-risk, and no path to scale.

## The four layers (dependency rule points inward)

```
┌───────────────────────────────────────────────────────────────────────┐
│  PRESENTATION  (React components — dumb, props in / callbacks out)      │
│  bot-job-details/grid/*.tsx : FindBar, BlockCard, BlockHeader,          │
│  InstructionRow, InstructionList, InstructionTypeBadge, … (16 done)     │
│  GridItem.tsx / GridItemComp.tsx → thin shells that render + wire.      │
└───────────────▲───────────────────────────────────────────────────────┘
                │ consumes (state + actions)
┌───────────────┴───────────────────────────────────────────────────────┐
│  APPLICATION / STATE  (React hooks — orchestration, no JSX, no SQL/WS   │
│  parsing beyond calling messaging)                                      │
│  bot-job-details/grid/hooks/* : useInstructionFind, useBlockCollapse,   │
│  useGridAlerts, useExecutionState, useInstructionMemory, useExcelExport,│
│  useInstructionDragController, useBlockMutations, useInstructionMutations│
│  useGridData → composed by useInstructionGrid(props)                    │
└───────────────▲───────────────────────────────────────────────────────┘
                │ calls (pure logic + transport)
┌───────────────┴────────────────────┐   ┌──────────────────────────────┐
│  DOMAIN  (pure TS — no React/IO)    │   │  INFRASTRUCTURE  (transport)  │
│  grid/domain/* : grouping, ordering,│   │  grid/messaging/* : WS message│
│  reorder math, find predicate,      │   │  builders + parsers for the   │
│  capability rules                   │   │  grid verbs; useWebSocket is  │
│  Deterministic, 100% unit-testable  │   │  the transport boundary       │
└─────────────────────────────────────┘   └──────────────────────────────┘
```

**Dependency rule:** Presentation → Application → (Domain + Infrastructure). Domain depends on nothing
(no React, no WS, no DTO-transport). Nothing depends on Presentation. This is what makes each layer
independently testable and swappable.

## Target folder structure

```
src/components/bot-job-details/grid/
  components/                 # PRESENTATION (move the 16 *.tsx + *.module.scss here)
    FindBar.tsx  BlockCard.tsx  BlockHeader.tsx  InstructionRow.tsx
    InstructionList.tsx  InstructionTypeBadge.tsx  … (+ .module.scss + .test.tsx)
  hooks/                      # APPLICATION / STATE
    useInstructionFind.tsx  useBlockCollapse.ts  useGridAlerts.ts
    useExecutionState.ts  useInstructionMemory.ts  useExcelExport.ts
    useInstructionDragController.ts  useBlockMutations.ts  useInstructionMutations.ts
    useGridData.ts          # the big WS message effect + core data
    useInstructionGrid.ts   # composition root — returns the whole view-model
  domain/                    # PURE LOGIC (no React/IO) — new layer
    find.ts                 # instructionMatchesFind, highlight ranges
    grouping.ts             # groupByBlock, reassignOrderNumbers, sortedBlockIndex
    reorder.ts              # instruction reorder + block reorder math (pure)
    capabilities.ts         # canMove/canAdd/canDelete/allowedBlockIds predicates
  messaging/                 # INFRASTRUCTURE (WS wire format) — new layer
    gridMessages.ts         # build/parse: BLOCK_MOVE, ROW_MOVE, previewMove,
                            #   memory apply/open, capability responses
  types/
    instructionGrid.types.ts # shared view-model + prop + DTO-view types
  GridItem.tsx  GridItemComp.tsx  # thin shells (later: one shell, parameterized)
```

Migration is **incremental** (one move per commit, tests green each time) — never a big-bang move.
Today the components + `hooks/` exist; `domain/` and `messaging/` are extracted as their logic is
lifted out of the hooks during Phase 6.

## Clean-architecture breakdown per concern

| Concern | Layer | Home |
|---|---|---|
| Render a row / block / find bar | Presentation | `grid/components/*` |
| "which blocks are collapsed", "what's in Find" | Application | `hooks/useBlockCollapse`, `useInstructionFind` |
| Alert/confirm modal state | Application | `hooks/useGridAlerts` |
| Memory-list picking + apply | Application | `hooks/useInstructionMemory` |
| Drag state + synthesize reorder | Application | `hooks/useInstructionDragController` |
| Group instructions by block; renumber | Domain | `domain/grouping.ts` |
| Compute a reorder (instruction/block) | Domain | `domain/reorder.ts` |
| Does an instruction match a query | Domain | `domain/find.ts` |
| Move-allowed / delete-allowed rules | Domain | `domain/capabilities.ts` |
| Build/parse a `BLOCK_MOVE` / `previewMove` | Infrastructure | `messaging/gridMessages.ts` |
| Open/send/receive over the socket | Infrastructure | `useWebSocket` (existing boundary) |

## Architectural improvements (what this buys)

1. **Separation of concerns** — UI, state, business rules, and wire format live in distinct layers with
   a one-way dependency rule; a change to the WS protocol touches only `messaging/`, a rule change only
   `domain/`, a layout change only `components/`.
2. **Testability** — `domain/` is pure and 100% unit-testable with no mocks; hooks test via `renderHook`;
   components test via render. (Phase 6 adds a `renderHook` test per hook; drag/reorder already have the
   `GridItem.dragMessages` regression.)
3. **Reduced coupling** — components receive props + callbacks only; they never touch the socket or DTOs.
   The god-component's ~80 `useState` become cohesive hooks, each ownable/reviewable in isolation.
4. **Scalability / DRY** — `GridItem` and `GridItemComp` are near-duplicate twins. Once the logic is in
   `hooks/` + `domain/` (parameterized by table `block`/`component_block` and session
   `botJobTasks`/`componentTasks`), **both grids share one implementation** — collapsing ~3,000 duplicated
   lines and guaranteeing they can't drift.
5. **Maintainability** — `GridItem.tsx` shrinks from ~3,700 lines to a thin shell that composes
   `useInstructionGrid(props)` and renders `components/*`. New features attach to the right layer instead
   of growing the god component.
6. **Behavior preserved** — every step is a verbatim lift (same state, same effects, same messages);
   the regression test + per-hook tests + `tsc` gate each commit. Backups on both repos
   (`backup/pre-phase6-2026-07-24`) allow instant rollback.

## Status (executing this now)

Phase 6 is delivering the **refactored production-grade code** layer by layer, one hook per commit:
- ✅ Application slices done: `useInstructionFind`, `useBlockCollapse`, `useGridAlerts`, `useExecutionState`.
- ⏳ Next: `useInstructionMemory` → `useExcelExport` → `useInstructionDragController` → block/instruction
  mutations → `useGridData` (core WS effect) → `useInstructionGrid` composition → extract `domain/` +
  `messaging/` → share the hook with `GridItemComp`.

Each slice moves logic *out of* the god component into the correct layer with a test, no behavior change.
