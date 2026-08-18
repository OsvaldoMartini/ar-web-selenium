# Variable-Centric Instruction Graph Roadmap

Date: 2026-07-29
Status: active; P0 through P3 complete, P4 diagnostics/VOID runtime implementation is in progress
Scope: Bot Job Details first, Components second, Variables workspace, Java persistence, and
execution safety
Canonical source notes:

- `CODEX_VARIABLES_IDEAS_2026_07_29.md`
- `CLAUDE_VARIABLES_IDEAS_2026_07_29.md`
- `../VARIABLE_SYSTEM_REDESIGN.md`
- `ROADMAP_COMPONENT_MEMORY_VARIABLE_AND_MULTI_EXECUTION_2026_07_27.md`
- `ACTIVE_BUGS_TO_FIX_2026_07_28.md`
- `P2_REACT_RELATIONSHIP_CLASSIFIER_2026_07_29.md`
- `P3_RELATIONSHIP_DETAILS_2026_07_29.md`

This document merges the Codex and Claude investigations into one delivery plan. It supersedes
their suggested implementation order where this roadmap explicitly makes a decision. It does not
mark any new feature as complete.

## 1. Purpose

The current editor protects instruction relationships by grouping rows and refusing movements
that might split a parent, LOOP, IF family, or variable sequence. That model has prevented some
invalid graphs, but it also makes normal authoring difficult and keeps semantic move logic in both
React and Java.

The target model is:

1. move one instruction at a time by default;
2. treat relationships as explicit data, not hidden drag constraints;
3. keep variables as durable reusable memory, including when their Web Element owner is removed;
4. show broken or out-of-order relationships in the grid instead of hiding rows or details;
5. let the client explicitly reconnect a relationship;
6. let React calculate the exact mutation intent;
7. let Java validate the structural envelope and persist that exact intent atomically;
8. never refuse Test Run or Launch because of variable health; represent a missing runtime producer
   as typed `VOID`, bypass only its dependent operation, and continue execution with diagnostics;
9. leave the working `+` Memory List selection and copy behavior unchanged.

The work is deliberately split into small phases. Every behavior-changing phase has a feature
gate, focused verification, and a rollback that does not require reverting unrelated work.

## 2. Current production baseline

The roadmap starts from the following facts in the current repositories.

### React

- `GridItemComp.tsx` renders the shared `GridItem` with Component workspace policy.
- `instructionMove.ts` currently expands a selected row into connected drag groups and rejects
  newly introduced semantic errors.
- `instructionDelete.ts` already calculates exact delete IDs and parent repairs in React, but
  currently exposes one delete policy.
- `variablesGraph.ts` derives variable owners, GET producers, consumers, ordering diagnostics, and
  health from backend `RAW_FACTS_V1`.
- `GridItem.tsx` currently returns `null` for several invalid relationships, which can make
  important relationship details disappear.
- The detached Variables page is read-only apart from navigation, filtering, and refresh.
- The `+` Memory List dependency closure and Component/Bot Job copy paths are working behavior and
  must be frozen during this redesign.

### Java and database

- `InstructionMoveValidator` and `InstructionMoveGroupService` still contain legacy conditional,
  LOOP, parent, and variable-order inference. The current post-`d64430c2` row-move services no
  longer call that semantic validator, but the code must be quarantined so it cannot return as a
  hidden authoring authority.
- `InstructionMoveTransaction` accepts layout version 2, requires the complete owner layout, and
  does not allow a submitted `parentId` to change.
- `InstructionDeleteContractValidator` version 2 validates exact React-selected IDs and supports
  clearing `parentId` on surviving rows.
- `PerformDataBase.deleteInstructionGraphAtomic` currently deletes variables owned by deleted
  instructions. This conflicts with the new durable-memory rule unless the variable owner is
  detached first.
- `variable.instruction_id` and `component_variable.instruction_id` are already nullable, which can
  represent a legitimate ownerless memory variable.
- `instruction.variable_id` and `component_instruction.variable_id` are not protected by a
  database foreign key in the current shared schema.
- `VariableRelationshipService` now emits raw facts only. React owns the variable graph
  classification.
- Graph revisions already include instruction relationships and variable ownership.
- The current hash/revision check is not a cross-process compare-and-set. Two writers can read the
  same revision before one commits. Version 3 needs a database-owned graph version/CAS.
- `RAW_FACTS_V1` realtime currently serves the Bot Job Variables workspace. Components do not yet
  have an equivalent Component-variable realtime contract.

### Existing stabilization dependencies

The new redesign must not hide these active stabilization items:

- BUG-001 requires manual verification on a disposable Bot Job copy.
- BUG-002 requires independent typed Component orchestration.
- BUG-003 already has separate hooks (`useInstructionDrag`, `useComponentInstructionDrag`, and
  `useMemoryListDrag`); it still requires manual acceptance and regression proof that one
  workspace cannot reset or complete another workspace's drag.
- BUG-004 requires Memory Apply and realtime refresh regression coverage.
- BUG-005 requires the production variable-relation audit.

Phase 0 records these as prerequisites. The redesign must not be used to bypass them.

## 3. Authoritative decisions

These decisions are the working constitution for implementation.

### D-001 - Variables are durable memory

Once created, a variable survives row movement, owner movement, and owner deletion. An ownerless
variable is a valid `MEMORY_ONLY` state. A variable is deleted only through an explicit
variable-delete action and confirmation; it is never garbage-collected as a side effect.

Durability is scoped to the owning Bot Job or Component workspace. Explicit deletion of the entire
Bot Job/Component owner may cascade its variables after normal whole-owner confirmation/backup
rules.

### D-002 - New scanned Web Elements receive a variable

A newly persisted scanned Web Element will eventually receive exactly one owner-scoped variable
in the same transaction. Existing data is not destructively backfilled until the audit and repair
phase proves that each repair is unambiguous.

### D-003 - Variable binding is primary only for variable-capable commands

GET, SET, E, CK, PDF CHECK, and CSV CHECK use `variableId`. Neutral commands such as Wait, Pause,
Screenshot, IF boundaries, and GOTO are not assigned a variable merely to satisfy a universal
rule.

For variable-capable commands, `parentId` remains an Engine compatibility projection while the
Engine still needs a Web Element target. React calculates any required projection patch; Java does
not silently infer it.

### D-004 - Existing command semantics remain unchanged until deliberately redesigned

The drag redesign must not redefine execution:

- GET writes its Web Element value into runtime memory.
- SET writes its configured literal to the Web Element and the same runtime-memory key. E and CK
  can therefore consume a preceding GET or SET value.
- E and CK consume runtime memory.
- PDF CHECK and CSV CHECK do not consume the ordinary variable-memory value. Their current Scanner
  path uses `fieldsToValidate` and OUTPUT-derived runtime keys; the configured external Engine
  validation context is defective and must be repaired before parity can be claimed.
- LOOP and REFRESH_LOOP retain an explicit Web Element anchor. The anchor identity is stored in
  `parentId`; `parentBlockId`, when carried as a compatibility projection, must agree with the
  resolved anchor's containing Block and never substitutes for `parentId`.
- GOTO and EXCEL GOTO retain an explicit destination Block stored in `parentBlockId`. That
  destination must resolve inside the same owner and must not equal the GOTO row's containing
  `blockId`.

Variable owner `instruction_id` is not consulted by the audited runtimes. It remains an
authoring/integrity relationship, while each command's actual element/variable/block requirements
remain independently classified. The full evidence is frozen in
`P1_ENGINE_EXECUTION_SEMANTICS_2026_07_29.md`.

### D-005 - Relationships are typed

React classifies these relationship kinds:

- `ELEMENT_TARGET`
- `VARIABLE_BINDING`
- `LOOP_ANCHOR`
- `CONDITIONAL_ROOT`
- `BLOCK_TARGET`
- `VARIABLE_OWNER`
- `VARIABLE_ORDER`
- `POSITIONAL_SCOPE`

`POSITIONAL_SCOPE` describes rows between IF/LOOP boundaries. It is not ownership and never causes
automatic move or delete cascades.

### D-006 - No silent relinking

Dropping a row next to another row does not automatically choose that neighbor as a parent,
variable owner, conditional root, LOOP anchor, or block target.

The UI may offer compatible candidates, but the user must explicitly choose the target through a
reconnect action. A relationship that remains valid is preserved unchanged. A relationship that
cannot remain valid requires an explicit `MOVE + DISCONNECT` or `MOVE + RECONNECT`; React never
silently clears it and never derives a replacement from row proximity.

For GOTO and EXCEL GOTO specifically, `parentBlockId` is preserved during movement only while it
remains different from the row's final containing `blockId`. Moving a GOTO row into its current
destination Block cannot preserve that destination: the user must explicitly disconnect it or
reconnect it to another compatible Block. The source Block, drop Block, row above, and nearest
Block are never automatic targets.

### D-007 - Clean unresolved state; no dangling IDs

When the user deliberately detaches a relationship, the current target becomes `null`. The
application does not persist an ID that no longer resolves inside the exact owner.

Relationship status is derived in React from current facts. Previous-target labels may be kept in
an audit/event record later, but are not required in the core instruction table for the first
release.

### D-008 - Single-row movement is introduced in safe stages

- Same-Block ordinary rows and relationship-bearing commands become single-row moves first.
- The first single-row phase accepts only moves that preserve every required relation. Persisting
  a newly broken/detached relation activates only after reconnect and complete diagnostic UX are
  available.
- IF/ELSEIF/ELSE/ENDIF boundary movement stays constrained in the first release; ordinary rows
  can move into or out of the positional body.
- Cross-Block movement comes only after reconnect and exact mutation/diagnostic contracts are
  accepted.
- Cross-Block movement is an exact single-row mutation: it never carries parents, children,
  positional body rows, variable producers/consumers, LOOP anchors, or GOTO targets merely because
  they are related. React preserves relations that remain valid and requires an explicit
  disconnect or reconnect patch for each relation invalidated by the move.
- Individual conditional-boundary movement is a later opt-in phase, not part of the first free
  drag release.

### D-009 - Delete operates on explicit edges only

The delete UI supports:

1. `DELETE SELECTED ONLY`
2. `DELETE + DIRECT ATTACHMENTS`
3. advanced `DELETE FULL EXPLICIT GRAPH`
4. `CANCEL`

Deleting never includes rows only because they are positioned between IF/ENDIF or a Web
Element/LOOP pair. Surviving rows receive explicit repairs and visible reconnect states.

Traversal follows instruction-to-instruction attachment edges in the selected direction.
`VARIABLE_OWNER` and `VARIABLE_BINDING` are impact/repair edges, not permission to delete a
variable or every unrelated command using it. Variable deletion is always a separate explicit
action. Deleting an owner detaches and preserves its variable by default once delete-v3 is active.

### D-010 - Variable health is diagnostic and never an execution permission gate

An unresolved draft may be saved. Variable health never refuses, pauses, cancels, or terminates a
Test Run or Launch. Missing, dangling, incompatible, inactive, out-of-order, out-of-scope,
duplicate, or ambiguous variable bindings/owners/producers are diagnostics. A variable-command
Web Element target problem is also a variable diagnostic for execution disposition.

`VOID` is typed run-scoped state, not the text `"VOID"` and not an empty string:

- a variable with no successful runtime producer is `VOID(NO_PRODUCER_YET)`;
- a successful writer produces `VALUE(value)`;
- `VALUE("")` is a legitimate empty Web value and remains available to equality/empty checks;
- a failed or absent producer leaves the variable `VOID`;
- a consumer of `VOID` records a bounded non-modal diagnostic, bypasses only its
  variable-dependent calculation/comparison/export, and execution continues;
- a later successful writer may replace `VOID`, so later consumers execute normally;
- `VOID` is never written to a Web Element, file, database variable value, or report as user data.

The user-owned Active flag remains unchanged. The application reports the exact rows and offers a
focus/reconnect action; it does not silently deactivate them. Structural start failures such as an
invalid owner/scope, failed definition load, missing selected Block, or duplicate/ambiguous Block
or instruction identity remain separate from variable health.

### D-011 - React owns authoring semantics

React is the sole authoring planner. It calculates:

- the exact row layout;
- relationship classification;
- candidate compatibility;
- whether each existing relation is preserved, explicitly disconnected, or explicitly
  reconnected;
- relationship patches;
- variable-owner patches;
- delete IDs and surviving-row repairs;
- user-facing impact previews.

Java retains only the structural, atomicity, security/ownership, concurrency, and runtime-safety
boundary. For authoring mutations it validates:

- request version and shape;
- session and owner scope;
- graph revision and database compare-and-set version;
- unique and complete IDs;
- expected old values;
- block ownership and order permutation;
- transaction success and committed-state verification.

Java persists exactly the submitted intent and broadcasts the authoritative result. It does not
expand a group, plan a disconnect, choose a relationship target, or silently repair/relink an
authoring graph.

### D-012 - Runtime diagnostics remain defense in depth

React supplies the full diagnostic UX. Java execution entry points observe a current authoritative
graph snapshot immediately before invoking the Engine, but variable diagnostics never become an
execution permission decision. This is not permission for Java to reintroduce drag grouping or
semantic authoring rules.

Runtime preflight records diagnostics and may separately validate the structural start envelope.
It must not plan/rewrite an authoring mutation, and it must not convert variable health into a
Test Run/Launch refusal.

The final preflight transport design is locked by the completed Phase 1 audit and is implemented
and activated in Phase 4.

### D-013 - Owner identity is compound

No relationship is resolved by numeric instruction or variable ID alone. Every request and
candidate set is scoped by:

- workspace kind;
- `homeBankingId`;
- `botJobId` for Bot Job instructions;
- the authoritative Component owner for Component instructions;
- workspace epoch/session;
- graph revision.

The authenticated WebSocket/session binding is authoritative. Owner fields in a client payload are
diagnostic assertions that must match the server-derived owner; they are never trusted to choose a
table or owner.

### D-014 - Bot Job and Component transports stay independent

Pure, stateless TypeScript graph functions may be shared. Drag state, pending requests, rollback
snapshots, WebSocket verbs, and Java persistence services remain private to Bot Job Details and
Components.

### D-015 - Memory List behavior is frozen

The following behaviors remain unchanged throughout the initial phases:

- row `+` FULL and DIRECT selection;
- block-header `+`;
- fresh-ID copy rather than move;
- parent/variable/reference remapping;
- detached Memory List reorder;
- one-click apply and realtime refresh.

The user-facing selection/copy semantics are frozen. Ownerless-variable compatibility may require
small, separately reviewed changes to `instructionDependency.ts`, Memory apply/remapping, or their
tests after P8; those changes may accept durable memory but must not alter which rows `+` selects,
turn copy into move, or change ordering. `useInstructionMemory.ts`, `memoryList.groups.ts`, and
`MemoryList.tsx` otherwise remain out of scope unless a regression is proven and separately
approved.

### D-016 - The grid never disappears on an edit error

The last authoritative grid remains rendered during pending requests, stale revisions, refused
mutations, WebSocket loss, and relationship errors. Errors are overlays/messages; they do not
replace the client data with a blank page.

### D-017 - Reconnect UX starts with a modal

The first reconnect UX is a clickable relationship/reconnect chip in the instruction details.
Clicking the chip opens a bounded modal that shows the relation kind, current target, compatible
same-owner candidates, explicit disconnect/reconnect choices, and the exact patch preview.

Animated relationship arrows are a later visualization enhancement. They are not part of the
first reconnect implementation, are not an activation dependency for P7 or P11, and must never
become a hidden authoring planner.

## 4. Command and relationship matrix

This is the target/post-phase matrix. Before P9/P14, a historical Web Element may own zero, one,
or (in legacy data) more than one variable. After P9, every newly materialized Web Element owns
exactly one variable; historical rows reach that invariant only after audited P14 repair.

| Action/row | Variable | Web Element target | Other relation | Initial free-move policy | Execution requirement |
|---|---|---|---|---|---|
| Persisted Web Element | Owns one variable after P9 | Conceptual anchor only; do not persist `parentId=self` | None | Single row | Locator remains valid for commands that use it |
| GET | Required | Required | Runtime-memory write ordering | Single row | Target and variable resolve; value is written before a reader |
| SET (current mode) | Required metadata/runtime key | Required writable element | Literal and runtime-memory assignment | Single row | Target and variable resolve; configured literal is written |
| E | Required | Preserve current required target | Runtime-memory read ordering | Single row | Target context exists and GET or SET has populated the key |
| CK | Required | Preserve current required target | Runtime-memory read ordering | Single row | Target context exists and GET or SET has populated the key |
| PDF CHECK/CSV CHECK | Persisted today, not actual-value source | Persisted command context | OUTPUT/validation-field source | Single row | Validate when an OUTPUT source exists; otherwise report VOID and continue |
| LOOP/REFRESH_LOOP | Not newly required | Required anchor in `parentId` | Positional body | Single LOOP row after Phase 6 | `parentId` anchor exists and precedes LOOP |
| IF/ELSEIF/ELSE/ENDIF | None | None | Conditional root | Boundary constrained initially | Structurally valid family |
| GOTO/EXCEL GOTO | None | None | Destination Block in `parentBlockId` | Single row | Destination Block exists in the same owner and differs from containing `blockId` |
| Wait/Pause/Screenshot/Refresh/swipe | None | None | None | Single row | Action-specific fields only |

This table is intentionally conservative. Phase 1 may document future modes, but no mode changes
as a side effect of the drag work.

### Parent/variable invariant lock required in P1

Before P5 implementation, P1 must publish the exact rule for each variable-capable action:

| Action | Must `parentId == variable.ownerInstructionId` when owned? | Ownerless variable record valid? | Active instruction executable ownerless today? |
|---|---|---|---|
| GET | Runtime does not compare owner; authoring expects parent target and owner to agree when owned | Yes | Runtime can execute if `parentId` target and `variableId` resolve |
| SET literal | Runtime does not compare owner; authoring expects parent target and owner to agree when owned | Yes | Runtime can execute if writable `parentId` target and `variableId` resolve |
| E | Runtime does not compare owner; `parentId` context and populated variable key are required | Yes | Runtime can execute after GET or SET if target context and key resolve |
| CK | Runtime does not compare owner; `parentId` context and populated variable key are required | Yes | Runtime can execute after GET or SET if target context and key resolve |
| PDF CHECK/CSV CHECK | Owner is not the actual-value source; current validation context is not execution-safe | Yes | Missing OUTPUT/validation data is warning-only VOID; valid sourced checks retain normal assertion semantics |
| LOOP/REFRESH_LOOP | Not a variable-owner relation; uses `LOOP_ANCHOR` | Not applicable | No without anchor |

No v3 relationship patch may silently make variable ownership an Engine requirement or remove an
existing target requirement. P4 may enforce a stricter explicit authoring policy, but it must label
that policy separately from the audited runtime facts.

## 5. Target relationship states

Relationship health is per typed edge, not one enum per instruction. One row may simultaneously
need an element target, a variable, and producer-order repair, so the details area may render more
than one issue/chip. `MEMORY_ONLY` is a variable lifecycle state. `SAVING` and `REFUSED` are
mutation states.

| State | Meaning | UI |
|---|---|---|
| `CONNECTED` | Required target exists and order is valid | Keep current colored relationship text |
| `MEMORY_ONLY` | Variable record exists without an owner; this is valid durable data | Neutral memory chip; current active commands still follow P1 execution rules |
| `RECONNECT_PARENT` | Required element target is absent | Purple/red reconnect chip |
| `RECONNECT_VARIABLE` | Variable binding is missing or incompatible | Purple/red reconnect chip |
| `RECONNECT_LOOP` | LOOP anchor is absent or invalid | Purple/red reconnect chip |
| `REPAIR_CONDITIONAL` | Conditional root/boundary is invalid | Purple/red repair chip |
| `RECONNECT_BLOCK` | GOTO target Block is absent | Purple/red reconnect chip |
| `FIX_ORDER` | Target exists but required producer/parent order is invalid | Amber chip |
| `SAVING` | A correlated mutation is pending | Non-clickable pending chip |
| `REFUSED` | Mutation failed; last valid graph remains | Error message plus retry/focus |

Broken relationships must never cause `GridItem` to return `null` for the row details.

## 6. Target mutation contracts

Phase 5 locks a dedicated DTO; it does not overload nullable `SplitDTO` fields. Bot Job and
Component mutations use separate WebSocket verbs/services even when they share immutable DTO
types.

### Bot Job graph mutation, version 3

The example is abbreviated for readability. A real request contains one `layoutRows` entry for
every instruction owned by the authenticated Bot Job.

```json
{
  "type": "INSTRUCTION_GRAPH_MUTATION",
  "contractVersion": 3,
  "mutationKind": "ROW_MOVE",
  "requestId": "unique-request-id",
  "baseGraphVersion": 42,
  "graphRevision": "expected-content-hash",
  "workspaceEpoch": 12,
  "ownerAssertion": {
    "workspaceKind": "BOT_JOB",
    "homeBankingId": 2,
    "botJobId": 5
  },
  "draggedInstructionId": 917,
  "layoutRows": [
    {
      "instructionId": 917,
      "blockId": 125,
      "blockOrderNumber": 14,
      "instructionOrderNumber": 3
    },
    {
      "instructionId": 918,
      "blockId": 125,
      "blockOrderNumber": 14,
      "instructionOrderNumber": 4
    }
  ],
  "instructionRelationPatches": [
    {
      "instructionId": 918,
      "relationKind": "LOOP_ANCHOR",
      "operation": "CLEAR",
      "expectedParentId": 917,
      "newParentId": null,
      "expectedParentBlockId": 125,
      "newParentBlockId": null
    }
  ],
  "variableBindingPatches": [
    {
      "instructionId": 920,
      "operation": "SET",
      "expectedVariableId": 44,
      "newVariableId": 45
    }
  ],
  "variableOwnerPatches": [
    {
      "variableId": 44,
      "operation": "DETACH",
      "expectedInstructionId": 917,
      "newInstructionId": null
    }
  ]
}
```

Rules:

- `layoutRows` is always the complete final owner layout. `draggedInstructionId` records the user
  gesture but does not define persistence scope.
- `baseGraphVersion` is the database compare-and-set token.
- `graphRevision` is a content/evidence hash, not the cross-process CAS token.
- The server derives workspace/table/owner from the authenticated session and rejects a mismatched
  `ownerAssertion`.
- Patch operations are explicit. Parent-like patches carry both `parent_id` and
  `parent_block_id`; Block-target patches carry `parent_block_id`; variable-binding patches carry
  `instruction.variable_id`; variable-owner patches carry `variable.instruction_id`.
- A LOOP/REFRESH_LOOP anchor patch identifies its anchor through `parent_id`. A supplied
  `parent_block_id` is a checked projection of the resolved anchor's Block, not an alternative
  anchor identity.
- A GOTO/EXCEL GOTO Block-target patch reads and writes `parent_block_id`. The validator rejects
  `newParentBlockId == final layoutRows[instructionId].blockId`. If a move places the row into its
  existing destination Block, React must submit an explicit `CLEAR` or an explicit `SET` to a
  different compatible Block; omission is not an instruction to auto-target another Block.
- `KEEP`, `SET`, `CLEAR`, `DETACH`, `REASSIGN`, and explicit `DELETE` cannot be inferred from
  omitted or JSON-null fields.
- Move and relationship patches commit or roll back together.
- A relationship-only reconnect uses the same contract with
  `mutationKind: "RELATIONSHIP_UPDATE"`, `draggedInstructionId: null`, an unchanged complete
  layout, and explicit patches.
- Components use a separate `COMPONENT_GRAPH_MUTATION` route, authenticated Component owner, and
  Component transaction.

### Delete, version 3

```json
{
  "type": "DELETE_INSTRUCTION",
  "deleteContractVersion": 3,
  "requestId": "unique-request-id",
  "baseGraphVersion": 42,
  "graphRevision": "expected-content-hash",
  "workspaceEpoch": 12,
  "ownerAssertion": {
    "workspaceKind": "BOT_JOB",
    "homeBankingId": 2,
    "botJobId": 5
  },
  "deleteMode": "SELECTED_ONLY",
  "selectedInstructionId": 917,
  "deleteInstructionIds": [917],
  "instructionRelationPatches": [
    {
      "instructionId": 918,
      "relationKind": "LOOP_ANCHOR",
      "operation": "CLEAR",
      "expectedParentId": 917,
      "newParentId": null,
      "expectedParentBlockId": 125,
      "newParentBlockId": null
    }
  ],
  "variableOwnerPatches": [
    {
      "variableId": 44,
      "operation": "DETACH",
      "expectedInstructionId": 917,
      "newInstructionId": null
    }
  ],
  "preserveVariableIds": [44]
}
```

The backend validates and persists these exact lists. It never adds positional body rows or
variable-linked commands that React did not submit.

### Transaction order

One database connection and transaction must:

1. compare-and-set `baseGraphVersion`;
2. validate the complete owner envelope and expected old values;
3. apply layout, instruction relationships, variable bindings, variable owners, and deletes;
4. verify the complete final state inside the still-open transaction;
5. increment/store the committed graph version;
6. commit;
7. reload the authoritative committed snapshots;
8. acknowledge and publish.

Verification after commit is observability, not transaction protection.

Every graph writer must advance the same owner version in its transaction: v2/v3 move/delete,
Block and instruction CRUD, Command Editor, scanner materialization, Memory Apply, variable
create/update/delete/owner change, Component apply, and legacy writes that remain enabled.
Otherwise v2/v3 coexistence is unsafe.

`instruction_graph_state` uses a dialect-safe compound key for workspace kind and authoritative
owner, atomic initial-row creation, and an explicit cleanup/tombstone rule when the whole owner is
deleted.

### Idempotency decision

Version 3 guarantees no duplicate mutation through request correlation plus graph-version CAS.
After a JVM restart, retrying a previously committed request may receive `STALE_VERSION` rather
than the original response; the client must perform authoritative resync. Durable same-response
idempotency requires a mutation journal and is deferred unless P5 review makes it mandatory.

### Authoritative response

Successful mutations publish:

- correlation/request ID;
- `committedGraphVersion`;
- committed content `graphRevision`;
- complete authoritative Block/instruction snapshot;
- raw variable facts;
- Variables workspace snapshot/refresh event;
- mutation result message.

A stale, failed, or uncorrelated response cannot replace the last valid client graph.

## 7. Repository impact map

