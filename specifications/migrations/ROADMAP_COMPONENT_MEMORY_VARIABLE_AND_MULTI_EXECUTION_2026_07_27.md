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
- Keep row `+` available for graph-aware instruction selection.
- Keep the obsolete blue arrow removed.
- Frontend commit: `c8ca242`.
- Built and deployed the React bundle (`main.5fdca76c.js`, `main.73f5e771.css`).

### C2 - Shared dependency-closure service (complete)

- [x] Resolve one fixed-point dependency closure for Memory preview and transactional apply.
- [x] Return structured refusal reasons, the exact ordered instruction group, required GOTO
  blocks, and a stable group key to React.
- [x] Include transitive parent/child, conditional, loop, variable owner/user, and recursive
  Component GOTO dependencies.
- [x] Stage the complete group atomically from the row `+`; stale, missing, duplicate, or partial
  UI groups are refused before Memory state changes.
- [x] Revalidate the same complete group inside the database transaction before copying or moving.
- [x] Generate fresh Bot Job block, instruction, variable, and reference IDs for Component copies,
  then remap every parent, parent-block, variable, and reference relationship.
- [x] Roll back the complete operation when any source relation cannot be remapped; source
  Component IDs are never silently persisted as destination relationships.
- [x] Keep Bot Job GOTO destinations in place during a same-job move while requiring Component
  GOTO destination blocks during a cross-owner copy.

Focused verification on 2026-07-27:

- Backend dependency closure, transactional copy/remapping, move validation, and Memory List
  group-order suites: 56 tests passed.
- React Memory resolution, atomic row/block staging/removal/reorder, stale-confirmation guards,
  Component revision refresh, GridItemComp parity, lifecycle, and drag-message suites:
  47 tests passed.
- A Component block selection now stages its complete external dependency union, while Memory List
  removal and drag/drop treat every connected dependency group as one indivisible unit.

### C2A - Runtime acceptance and Components-first continuation (active, 2026-07-27)

The user manually accepted the completed **Bot Job Details** Memory Apply path: staging an instruction
or connected group and applying it creates fresh Bot Job rows, preserves the source rows, and keeps
the remapped relationships. That working behavior is now the reference contract for
`GridItemComp`/Components. Work proceeds in this order:

1. finish Components copy/memory parity;
2. finish the Components instruction drag controller and runtime verification;
3. restore the detached Memory List drag controller;
4. only then return to the Bot Job Details connected-group destination-index follow-up.

Completed backend Component-copy parity in this continuation:

- [x] A generated Component command with a valid `parent_id` and legacy null
  `parent_block_id` receives the generated parent block; the reusable
  `component_instruction` source is not repaired or mutated as a side effect.
- [x] Generated Component relationship updates are scoped by both generated instruction ID and
  destination `bot_job_id`, with exactly-one-row verification.
- [x] `GridItemComp` uses the dedicated `useComponentInstructionDrag` transport and
  `COMPONENT_ROW_MOVE`; the Bot Job sender is not reused for Component commits.
- [x] Connected Component rows preserve authoritative family order, drop on their own family is a
  no-op, and same-block downward placement is calculated after removing the complete family.
- [x] Component and Bot Job diagnostic drag entry points are namespaced independently, preventing
  one open workspace from replacing the other workspace's callback.
- [ ] User runtime acceptance of this completed Components continuation remains pending.

#### Drag-controller isolation directive

Drag state must not be shared across the three interactive surfaces. Each owns a private controller
and lifecycle:

- `GridItem` / Bot Job Details owns the Bot Job instruction/block drag controller;
- `GridItemComp` / Components owns the Component instruction/block drag controller;
- detached Memory List owns the Memory List item/group drag controller.

Shared code is limited to immutable DTO types and pure, stateless graph/reorder policies. Sensor
state, dragged IDs, source/destination indices, pending previews, confirmations, rollback snapshots,
and WebSocket request correlation must remain private to the owning page. A reconnect, refresh, or
drag in one surface must never reset or complete a drag in another.

#### Memory List drag facts

- [x] Backend `MemoryListReorder.resolveGrouped` validates a complete permutation and refuses a
  split or internally reordered dependency group.
- [x] Backend removal resolves every member of a connected group.
- [x] The detached Memory List now owns `useMemoryListDrag`, carries stable item keys instead of
  render indices, displays refused movements, and moves a parent/child family as one contiguous
  unit while preserving internal order.
- [x] Whole Bot Job block staging now computes the smallest transitive dependency families instead
  of assigning one artificial dependency key to every row in the block. Independent instructions
  remain independently draggable.
- [x] A workspace-level regression verifies that one connected drag emits exactly one correlated
  `memoryList.command/REORDER` with the complete ordered key list.
- [ ] User runtime acceptance of detached Memory List drag remains pending.

Focused verification on 2026-07-28:

- React Components/Memory List suites: 45 tests passed.
- Java Component copy plus Memory List reorder suites: 41 tests passed.
- React production build completed and was mirrored exactly into
  `src/main/resources/build` (`robocopy /MIR /L` reported no differences).

#### Explicit deferred Bot Job follow-up

- [ ] After Components and Memory List runtime acceptance, revisit Bot Job Details same-block
  connected-group destination-index calculation. The drop index must be calculated after removing
  the complete moving group, then the group must be inserted once with relative order preserved.
  This later task must not be mixed into the Components-first delivery.

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
- [x] Phase P2: build one detached, draggable Variables page for the active Bot Job's declared
  variables, owners, GET producers, readers/checks, literal SET assignments, inactive links, and
  relationship diagnostics.
- [x] Phase P2: keep the Variables binding backend-owned, retarget the singleton page when the
  active Bot Job changes, and publish persisted graph mutations in real time without publishing
  per-step execution-status noise.
- [x] Phase P2: use a collapsible/searchable tree plus an explicit relationship flow. Literal SET
  is rendered as `SET -> declaration Web Field`, not as a false variable consumer.
- [x] Phase P2: preserve the last valid graph on refresh/transport failure and reject partial,
  stale, uncorrelated, or older-workspace responses.
- [ ] Phase P3: add execution-time initial/current value streaming and pause/edit/resume only after the Engine
  exposes a safe run-scoped API.

#### Shared detached-window launch-token follow-up

The Variables workspace currently uses the same fixed-session launch boundary as Configuration,
Memory List, Command Editor, and the other fixed detached pages:

- the desktop HTTP/WebSocket listener is loopback-only;
- the backend owns the active Bot Job and workspace epoch;
- bootstrap accepts only the exact transport registered for that fixed session;
- Pages Open focus uses an opaque page ID and a one-use native window-title focus token.

The fixed desktop launch URL itself does not yet carry a one-use nonce. This is a shared platform
gap, not a Variables-specific exception: a same-host page that knows a fixed session name could
attempt transport takeover before the workspace service rejects its requests. A platform phase
must add one-use, short-lived launch nonces to every fixed detached page, validate and consume the
nonce before `WebSocketSessionManager.takeOverSession`, and preserve the existing opaque focus
token. Variables must migrate with the other fixed pages; it must not introduce an incompatible
one-off launch protocol.

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
