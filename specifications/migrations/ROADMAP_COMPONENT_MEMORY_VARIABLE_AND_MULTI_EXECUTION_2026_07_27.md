# Component Memory, Variables, and Multi-Execution Roadmap

Date: 2026-07-27
Status: active; integrity fixes first, multi-launch last

## Purpose

This roadmap separates three related work streams:

1. preserve a complete reusable Component graph when it is staged through the Memory List;
2. make variable ownership, dependency ordering, copy, and deletion explicit and safe;
3. extend the Main Dashboard for multi-selection and metadata editing without weakening the
   single-session Playwright rules.

The first two streams protect persisted Bot Job graphs and must be completed before multi-launch.

## Production incident and immediate correction

The delete failure was caused by Java unboxing a nullable parent:

```java
int ifRootId = actions.equalsIgnoreCase("IF")
        ? instructionId
        : splitDTO.getParentId();
```

`component_instruction.parent_id` and `instruction.parent_id` are legitimately null for root
instructions. Null is not evidence of damaged data.

Completed in backend commit `c3422325`:

- use authoritative stored instruction metadata instead of client metadata;
- never unbox a nullable parent;
- calculate the transitive dependent set for an ordinary parent instruction;
- present the same authoritative delete set to the confirmation UI;
- delete variables, references, dependents, and the selected instruction through the existing
  atomic graph-delete transaction;
- reject only structurally invalid conditional commands such as an `ELSE`/`ENDIF` without its IF
  root, with a structured error instead of an exception.

## Canonical investigation fixture: Check payment

The Components page displays `Check payment` as block order **18**. Its database identity is
`component_block.id = 36`; database block ID 18 is a different component.

| Order | Component instruction | Action | Parent / variable relationship |
|---:|---:|---|---|
| 1 | 42 | PAUSE | root; null parent is valid |
| 2 | 43 | C | root; null parent is valid |
| 3 | 44 | Web Field | root producer/owner |
| 4 | 45 | H | parent 44, variable 1 |
| 5 | 46 | IF | self-parent 46 |
| 6 | 47 | GET | parent 44, variable 1 |
| 7 | 48 | CK | parent 44, variable 1 |
| 8 | 49 | E | parent 44, variable 1 |
| 9 | 50 | ELSE | IF root 46 |
| 10-13 | 51-54 | Web steps | conditional contents |
| 14 | 55 | LOOP | parent 44 |
| 15 | 56 | ENDIF | IF root 46 |

The block also contains:

- two `component_variable` rows owned by component instruction 44;
- 25 `component_reference` rows owned by instructions 43, 44, 51, 52, and 54;
- a required GET-before-E sequence;
- conditional and loop spans that cannot be reconstructed safely from a filtered subset.

This complete 15-row aggregate is the canonical future integration fixture.

## Actual Components-to-Memory defect

Before this correction, the Components block-header `+` reused row-level `canAdd` filtering and
created several `INSTRUCTION` Memory items. For Check payment, protection of conditional and loop
relationships made instructions 44-56 ineligible. The UI therefore staged only 42 and 43 and
silently lost the Web Field, variables, references, GET/CK/E, IF/ELSE/ENDIF, and LOOP.

The backend already has the correct whole-block path in `ComponentMemoryApplyService`:

- typed payload `kind: "BLOCK"`;
- authoritative source reload by component block ID and revision;
- one database connection and one transaction;
- generated-ID remapping for blocks, instructions, variables, references, parents, and GOTO blocks;
- rollback on an incomplete or stale graph.

The chosen frontend correction keeps the removed blue arrow removed. The existing Components
block-header `+` stages one typed whole-block Memory item and does not apply row-level filtering.

## Required aggregate mapping

| Component source | Bot Job destination |
|---|---|
| `component_block.id` | generated `block.id` |
| `component_instruction.id` | generated `instruction.id` |
| `component_variable.id` | generated `variable.id` |
| instruction `block_id` | generated block ID |
| instruction `parent_id` | generated instruction ID, or null for a root |
| instruction `parent_block_id` | generated block ID |
| instruction `variable_id` | generated variable ID |
| `component_reference.instruction_id` | generated instruction ID |

### Non-negotiable invariants

- Null parent is valid only where the action model permits a root.
- A whole Component block is one aggregate; it is never copied as independently filtered rows.
- Every non-null generated relation must resolve inside the selected aggregate or an explicitly
  allowed destination.
- GET/producer rows execute before E, SET, CK, and other consumers of their variable.
- IF/ELSE/ENDIF and LOOP spans remain complete and ordered.
- Copy and delete are atomic. A partial graph is never committed.
- The authoritative database result is published after commit; an optimistic grid is not treated
  as persisted truth.

## Component and variable work phases

### C0 - Nullable deletion safety (complete)

- Backend commit `c3422325`.
- Restart the backend before production verification.

### C1 - Whole-block staging from the existing `+` (complete)

- Restore the pure `componentBlockMemoryItem` producer.
- Route the Components block-header `+` to one typed `BLOCK` Memory item.
- Keep row `+` available for deliberate single-instruction selection.
- Keep the obsolete blue arrow removed.
- Frontend commit: `c8ca242`.
- Built and deployed the React bundle (`main.5fdca76c.js`, `main.73f5e771.css`).

### C2 - Shared dependency-closure service

- Use one service for preview, Memory selection, copy, move, and cascade deletion.
- Return reasons and exact dependent IDs to React.
- Include transitive parent, conditional, loop, variable-producer, and GOTO dependencies.
- Do not let each UI surface invent a different closure.

### C3 - Full graph validation

- Validate the resulting graph immediately before commit.
- Validate parent existence/order, conditional spans, loop spans, GET-before-consumer ordering,
  variable ownership, and GOTO block existence.
- Validate an aggregate revision that includes block metadata, instructions, variables, and
  references—not instructions alone.

### C4 - Atomic Component authoring

- Refactor Save Component so block, instructions, variables, rewrites, and references share one
  transaction.
- Add durable provenance from generated Bot Job rows back to source Component/revision.
- Correct the SQLite `component_instruction.parent_block_id` relationship to the Component block
  table and enable/verify foreign-key enforcement through migration-safe checks.

### C5 - Variables as a first-class aggregate

- [x] Phase 0A: prevent new duplicate declarations through the Variable Editor/create-variable
  application path without modifying legacy production rows.
- [x] Phase 0A: bind declaration updates to variable ID + selected Web Field + workspace owner.
- [x] Phase 0A: bind dependent command loads/rewrites to the selected variable ID.
- [x] Phase 0A: atomically reject copied consumers that omit their matching GET producer.
- [ ] Phase 0B: cover component copy/import bypass paths and enforce the invariant with database
  unique indexes after deterministic repair.
- [x] Phase 0A: centralize generated names and runtime producer/consumer action semantics.
- [x] Phase 0A: validate GET ordering before E/CK/PDF CHECK/CSV CHECK on touched row moves.
- [ ] Phase 0B: repair the one known duplicate owner, stale Wait binding, missing cloned parents,
  and missing parent-block metadata before adding database uniqueness.
- Enforce one producer variable per instruction after the repair report confirms there are no
  ambiguous conflicts.
- GET is the producer; E/CK/PDF CHECK/CSV CHECK are current consumers. SET becomes a consumer only
  in its future `VARIABLE_SOURCE` mode.
- Compatibility note: current SET is a literal writer, so GET-before-SET is deferred until an
  explicit SET source mode is implemented in the execution engine.
- Add `validateVariableOrder` to every move/copy path.
- Build the detached Variables page for declared variables.
- Add execution-time initial/current value streaming and pause/edit/resume only after the Engine
  exposes a safe run-scoped API.

## Main Dashboard work stream

### D1 - Execution selection column (easiest dashboard change)

- Add `Execution` immediately before `Name`.
- Store selected Bot Job IDs in a dedicated `Set<number>`; do not reuse the currently highlighted
  row.
- Stop checkbox events from selecting/opening a row.
- Prune IDs after refresh when a job is deleted or no longer visible.
- Selection does not launch anything in this phase.

### D2 - Reusable searchable selector

Create a generic `SearchableSelect` based on the AI Manager combobox design shown in
`specifications/migrations/searchable_tool.png`. The closest source is
`origin/feature/ai-manager-fixed-rules:src/components/ai/studio/BotIdentityCombobox.tsx` in
`MultiTraderAI-Launcher`.

Preserve:

- opens on focus/click;
- token-based filtering and a result count;
- keyboard navigation and ARIA combobox semantics;
- portal rendering with automatic above/below placement;
- compact option metadata.

### D3 - Application Type editing

- Use the searchable selector in each dashboard row.
- Preserve persisted types: `Web App`, `Android`, `iOS`, and `Rest Api`.
- Show the standard React confirmation before saving.
- Add an owner-scoped backend operation with an authoritative response.
- Do not optimistically keep a type that the backend refused.

### D4 - Name and Description editing

- Use a focused editor and the same confirmation/result-message pattern.
- Add separate owner-scoped SQL updates for summary fields.
- Publish the authoritative metadata change to the Main Dashboard and any open Bot Job Details
  workspace so one page cannot overwrite another with stale metadata.

### D5 - Multi-launch, headed/headless (explicitly last)

Implementation and tests are deferred until D1-D4 and component/variable integrity are complete.
The current Launch button is not a reusable multi-run coordinator and must not be called in a
simple loop.

The future coordinator requires:

- a run ID and immutable selected Bot Job IDs;
- explicit `HEADED` or `HEADLESS` mode;
- Engine support for the mode instead of a UI-only flag;
- bounded concurrency and isolation rules compatible with the unique Playwright session;
- per-job state/result/error reporting;
- cancellation and cleanup that do not close unrelated workspaces;
- deterministic ownership and active-job validation.

## Deferred focused verification

The user requested that broad tests not run during the current delivery window. When verification
is resumed, begin with:

1. stage Check payment once and assert one `COMPONENT/BLOCK` Memory item;
2. apply once and assert all 15 instructions, two variables, and 25 references exist;
3. assert all generated parent/block/variable/reference IDs point to generated rows;
4. assert GET remains before E and conditional/loop spans remain complete;
5. delete a root with a null parent and assert its dependent graph is removed atomically;
6. force a mid-copy failure and assert no partial block, row, variable, or reference remains;
7. confirm dashboard selection survives sorting/filtering and is pruned after refresh;
8. leave headed/headless multi-launch tests until the final multi-launch phase.

## Recommended next implementation

The smallest safe integrity improvement was C1, because the transactional backend path already
existed. After runtime verification of C1, D1 (Execution checkboxes only) is the easiest independent
UI task. C2 should precede any expansion of individual component-row selection.
