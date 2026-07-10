# Instruction Graph And Drag-And-Drop Roadmap

Date: 2026-07-10

## Objective

Make drag-and-drop easier while preventing invalid IF, loop, parent, variable, and cross-block relationships. Replace the large duplicated React rule block with one Java graph validator and a small shared React drag controller.

## Target Model

Create `InstructionGraphService` with nodes and explicit edges:

- `IF_FAMILY`: IF root to ELSEIF/ELSE/ENDIF
- `LOOP_BOUNDARY`: element parent to LOOP/REFRESH_LOOP
- `VARIABLE_REFERENCE`: command to variable
- `ELEMENT_REFERENCE`: command to native element
- `BLOCK_REFERENCE`: GOTO/EXCEL GOTO to block
- `DEPENDENT_OPERATION`: parent instruction to attached command

The graph must be built from database-authoritative rows, never only from the browser's current array.

## Backend API

| Message | Purpose |
|---|---|
| `instructionGraph.capabilities` | Return move/split/insert/edit/delete capabilities and reasons |
| `instructionGraph.previewMove` | Validate source, destination block/index, and return affected group preview |
| `instructionGraph.applyMove` | Revalidate and persist one atomic move |
| `instructionGraph.previewSplit` | Return split legality and affected rows |
| `instructionGraph.applySplit` | Revalidate and persist split atomically |

Responses must include `revision`, `requestId`, stable ids, normalized orders, and authoritative refreshed blocks.

## Drag UX

- Show a visible drag handle instead of making the whole row ambiguous.
- Highlight only valid drop zones returned by capabilities.
- Keep invalid zones visible but disabled with a short reason tooltip.
- Drag connected structures as a group where safe:
  - complete IF family plus contained rows;
  - loop parent plus its boundary;
- Reject partial graph moves.
- Show a compact preview before a cross-block group move.
- On backend rejection, restore the original layout without a full-page jump.
- Preserve scroll and expanded/collapsed block state after refresh.

## Phases

### Phase 1 - Graph Fixtures

Create fixtures for simple/nested IF, ELSEIF chains, loops, nested loop/IF combinations, dependent commands, empty blocks, and cross-block references.

### Phase 2 - Pure Java Graph Builder

Implement graph construction and integrity diagnostics with no JavaFX dependencies.

### Phase 3 - Central Move Validator

Move all restrictions from `GridItem.tsx` and `GridItemComp.tsx` into Java. Validate same-block reorder, cross-block movement, graph-group movement, block deletion, and order normalization.

### Phase 4 - Atomic Persistence

Persist move/split operations transactionally. Reject stale `revision` values and duplicate `requestId` values.

### Phase 5 - Shared React Controller

Create `useInstructionDrag.ts` and use it in both grids. Remove inline IF/loop checks and duplicated alerts after parity tests pass.

### Phase 6 - Easier Drag UI

Add handles, valid-zone highlighting, group previews, keyboard move controls, and accessible reason tooltips.

### Phase 7 - Split Consolidation

Move split rules into the graph service. Remove the silent "last row means previous row" adjustment.

## Acceptance Criteria

- Nested IF and loop structures cannot be corrupted by drag/drop.
- React and direct WebSocket callers receive identical validation.
- Both grids use the same controller and backend validator.
- Connected structures move together or are explicitly rejected.
- No optimistic state survives a backend rejection.
- Orders and parent ids are correct in SQLite and the refreshed grid.

