# Variable-Centric Instruction Graph Roadmap

Date: 2026-07-29
Status: active; P0, P1, and P2 complete, P3 is the next implementation phase
Scope: Bot Job Details first, Components second, Variables workspace, Java persistence, and
execution safety
Canonical source notes:

- `CODEX_VARIABLES_IDEAS_2026_07_29.md`
- `CLAUDE_VARIABLES_IDEAS_2026_07_29.md`
- `../VARIABLE_SYSTEM_REDESIGN.md`
- `ROADMAP_COMPONENT_MEMORY_VARIABLE_AND_MULTI_EXECUTION_2026_07_27.md`
- `ACTIVE_BUGS_TO_FIX_2026_07_28.md`
- `P2_REACT_RELATIONSHIP_CLASSIFIER_2026_07_29.md`

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
8. refuse Test Run and Launch while active executable rows have unresolved required relationships;
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
- LOOP and REFRESH_LOOP retain an explicit Web Element anchor.
- GOTO and EXCEL GOTO retain an explicit destination Block.

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

The UI may offer a compatible suggestion, but the user must choose `MOVE + RECONNECT`. A normal
gap drop means `MOVE ONLY`.

### D-007 - Clean unresolved state; no dangling IDs

When the user deliberately detaches a relationship, the current target becomes `null`. The
application does not persist an ID that no longer resolves inside the exact owner.

Relationship status is derived in React from current facts. Previous-target labels may be kept in
an audit/event record later, but are not required in the core instruction table for the first
release.

### D-008 - Single-row movement is introduced in safe stages

- Same-Block ordinary rows and relationship-bearing commands become single-row moves first.
- The first single-row phase accepts only moves that preserve every required relation. Persisting
  a newly broken/detached relation activates only after reconnect is available and the execution
  gate is hard-enabled.
- IF/ELSEIF/ELSE/ENDIF boundary movement stays constrained in the first release; ordinary rows
  can move into or out of the positional body.
- Cross-Block movement comes only after reconnect and execution gates are accepted.
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

### D-010 - Execution is strict even when authoring is flexible

An unresolved draft may be saved. Test Run and Launch must refuse to start when an active
instruction has a required unresolved relationship or invalid execution order.

The user-owned Active flag remains unchanged. The application reports the exact rows and offers a
focus action; it does not silently deactivate them.

The execution gate is a mandatory dependency after broken drafts are permitted. It cannot be
disabled until all broken-authoring flags are disabled and an authoritative audit proves that no
active unresolved row remains.

### D-011 - React owns authoring semantics

React calculates:

- the exact row layout;
- relationship classification;
- candidate compatibility;
- relationship patches;
- variable-owner patches;
- delete IDs and surviving-row repairs;
- user-facing impact previews.

Java validates:

- request version and shape;
- session and owner scope;
- graph revision;
- unique and complete IDs;
- expected old values;
- block ownership and order permutation;
- transaction success and committed-state verification.

Java persists exactly the submitted intent and broadcasts the authoritative result. It does not
expand a group or choose a relationship target.

### D-012 - Runtime safety remains defense in depth

React supplies the full diagnostic UX. Java execution entry points still require a current graph
revision and a valid execution-preflight result before invoking the Engine. This is not permission
for Java to reintroduce drag grouping; it prevents stale or non-React callers from bypassing the
execution gate.

The final preflight transport design is locked in Phase 1 after all Test Run and Launch entry
points are mapped.

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
| PDF CHECK/CSV CHECK | Persisted today, not actual-value source | Persisted command context | OUTPUT/validation-field source | Single row | Block until the audited Scanner/Engine validation defects are resolved |
| LOOP/REFRESH_LOOP | Not newly required | Required anchor | Positional body | Single LOOP row after Phase 6 | Anchor exists and precedes LOOP |
| IF/ELSEIF/ELSE/ENDIF | None | None | Conditional root | Boundary constrained initially | Structurally valid family |
| GOTO/EXCEL GOTO | None | None | Destination Block | Single row | Destination Block exists |
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
| PDF CHECK/CSV CHECK | Owner is not the actual-value source; current validation context is not execution-safe | Yes | Not eligible until Scanner/Engine validation behavior is repaired and tested |
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

### React frontend

Expected files to change or be added over the roadmap:

- `src/components/GridItem.tsx`
- `src/components/GridItemComp.tsx` (P12 only; shared `GridItem` changes must be Bot Job-gated
  before then)
- `src/components/instructionsMockData.tsx`
- `src/components/SearchBox.tsx` (reuse, not redesign)
- `src/components/variablesGraph.ts`
- `src/components/variablesWorkspace.contract.ts`
- `src/components/VariablesPage.tsx`
- `src/components/VariablesPage.module.scss`
- `src/components/InstructionCommandPanel.tsx`
- `src/components/CommandEditorPage.tsx`
- `src/components/GridItemScann.tsx`
- `src/components/useInstructionDrag.ts`
- `src/components/useComponentInstructionDrag.ts`
- `src/components/bot-job-details/grid/InstructionRow.tsx`
- `src/components/bot-job-details/grid/domain/instructionMove.ts`
- `src/components/bot-job-details/grid/domain/instructionDelete.ts`
- `src/components/bot-job-details/grid/domain/instructionGraphRevision.ts`
- `src/components/bot-job-details/grid/hooks/useGridData.ts`
- `src/components/bot-job-details/useBotJobDetailsController.ts`
- `src/components/bot-job-details/BotJobDetailsHeader.tsx`
- `src/components/MainDashboard.tsx`
- `src/components/scanner/useScannerController.ts`
- `src/components/scanner/PageScannerExecutionControls.tsx`
- `src/components/scanner/ScannerExecutionPanel.tsx`

Proposed new pure/UI modules:

- `instructionRelationshipPolicy.ts`
- `instructionRelationshipGraph.ts`
- `instructionFreeMove.ts`
- `InstructionRelationshipDetails.tsx`
- `InstructionRelationshipDetails.module.scss`
- `ReconnectRelationshipDialog.tsx`
- `ReconnectRelationshipDialog.module.scss`
- `src/components/bot-job-details/grid/domain/executionRelationshipPreflight.ts`
- `ExecutionPreflightDialog.tsx`
- `ExecutionPreflightDialog.module.scss`

`AlertModal.tsx` currently hardcodes the alternate action `GET ALL BETWEEN`; reconnect, delete
choices, and preflight must use a dedicated dialog or first make the dialog actions fully generic.

Files frozen during initial relationship phases:

- `instructionDependency.ts`
- `useInstructionMemory.ts`
- `memoryList.groups.ts`
- `MemoryList.tsx`

### Java backend

Expected files/services to change or be added:

- `InstructionMoveTransaction`
- `BotJobRowMoveService`
- `ComponentRowMoveService`
- `InstructionMoveValidator` (legacy v2 only, then retirement)
- `InstructionDeleteContractValidator`
- `PerformDataBase.deleteInstructionGraphAtomic`
- `InstructionGraphRevisionService`
- `VariableRelationshipService`
- `VariablesWorkspaceService`
- `VariableUpdateTransaction`
- `CommandEditorService`
- `CommandRegistry`
- `VariableDefinitionPolicy`
- `SimpleWebSocketServer` routing
- Bot Job Details Test Run/Launch entry points
- Main Dashboard Launch entry point
- scanner/prelaunch execution entry points

Proposed focused services:

- `InstructionGraphMutationDTO` (new DTO; do not overload nullable `SplitDTO` fields)
- `InstructionGraphMutationContractValidator`
- `InstructionGraphMutationTransaction`
- `InstructionGraphVersionService`
- `VariableOwnerDetachTransaction` (connection-scoped helper; never independently commits)
- `ExecutionGraphPreflightService` or a revision-bound preflight verifier

### Schema and migrations

The first behavior phases do not require schema changes. Later data phases may add:

- `instruction_graph_state` with a dialect-safe compound key for workspace kind and canonical
  owner fields, plus `version`, for atomic graph-version CAS;
- owner-scoped unique indexes for one variable per non-null owner instruction;
- repair/audit tables or columns only if durable previous-target labels are approved;
- stronger owner-scoped variable-link integrity after production repair;
- fresh-install schema parity after versioned migrations exist.

Never edit only `V001__init_schema.sql` for an existing installation. Add a dated/versioned
migration first, verify SQLite/PostgreSQL/Access behavior, then update fresh-install definitions.
Production startup uses `com.allinweb.ch.db.MigrationRunner`; a production migration must be a
registered class under `src/main/java/com/allinweb/ch/db/migrations/`, not only a resource SQL
file.

P0 must inspect the actual production metadata for variable-owner nullability and FK delete
behavior. Repository fresh schemas currently intend nullable owners, but an upgraded historical
database may differ. Capability activation is refused when required tables/columns/constraints are
missing. Fresh SQLite/Access startup ordering must also be tested because migrations may run before
base-table initialization; a migration cannot silently skip and leave a fresh database without the
invariant.

## 8. Delivery and rollback rules

1. One phase per reviewable commit; do not mix schema, drag behavior, delete behavior, and visual
   redesign in one commit.
2. One terminal owns overlapping files. Claim files in `ACTIVE_BUGS_TO_FIX_2026_07_28.md` before
   implementation.
3. Backend capability/contract lands first. Frontend support lands second. Feature activation is a
   separate small change.
4. Keep version 2 move and delete paths available until version 3 passes runtime acceptance.
5. Gate Bot Job and Component behavior independently.
   Shared `GridItem` rendering/planners must check server-advertised workspace-specific
   capabilities so Bot Job phases cannot activate Component behavior early.
6. Take a database backup before any owner-detach/backfill/index phase.
7. Never test destructive migration or delete behavior first against the live Bot Job. Use a
   disposable database copy.
8. A failed optimistic update restores the exact previous React snapshot.
9. A failed transaction commits no row, relationship, variable-owner, order, or revision change.
10. Frontend deployment happens only after focused verification and source review for that phase.
11. Broad tests are deferred until the phase requests them; every phase still requires its listed
    focused tests.
12. Remove a feature flag only after at least one later release has proven rollback is no longer
    required.
13. Capabilities are server-advertised and bound to workspace epoch. If a capability changes while
    a request is in flight, ignore the late response and force authoritative refresh.
14. Once any broken draft is persisted, the backend execution readiness gate remains mandatory
    until broken-authoring capabilities are disabled and a full authoritative audit is clean.

Recommended independent capability flags:

- `relationshipChipsV1`
- `executionRelationshipGateV1`
- `rowMoveContractV3`
- `freeSameBlockMoveV1`
- `relationshipReconnectV1`
- `durableMemoryVariablesV1`
- `deleteContractV3`
- `freeCrossBlockMoveV1`
- `componentRowMoveContractV3`
- `automaticVariableCreationV1`
- `variablesWorkspaceActionsV1`
- `conditionalDraftMoveV1`

## 9. Phased implementation roadmap

| Phase | Deliverable | Data risk | Activation |
|---|---|---:|---|
| P0 | Baseline, fixtures, backups, ownership, and contract freeze | None | Documentation/tests only |
| P1 | Engine and entry-point semantic audit | None | No behavior change |
| P2 | Pure React relationship classifier | None | No UI change |
| P3 | Relationship details and read-only chips | None | Frontend flag |
| P4 | Execution preflight gate | Low | Shadow/warn, then mandatory |
| P5 | Additive version-3 mutation contract | Low | Capability off |
| P6 | Bot Job same-Block single-row drag that preserves relationships | Medium | Bot Job valid-only flag |
| P7 | Explicit reconnect plus broken-draft activation | Medium | Reconnect + mandatory gate |
| P8 | Durable ownerless memory variables | High | Data flag + backup |
| P9 | Owner uniqueness, then automatic variable creation for new Web Elements | High | Migration + creation flag |
| P10 | Delete selected/direct/full explicit modes | High | Delete v3 flag |
| P11 | Cross-Block and later conditional freedom | High | Separate flags |
| P12 | Independent Components parity | High | Component-only flag |
| P13 | Interactive Variables page | Medium | Variables actions flag |
| P14 | Historical repair and database constraints | High | Migration gate |
| P15 | Legacy semantic-code retirement | Medium | Remove only after acceptance |
| P16 | Runtime values and pause/edit/resume | Separate Engine project | Deferred |

## 10. Detailed phase checklists

### P0 - Baseline and rollback preparation

Goal: capture current truth before changing semantics.

Tasks:

- [x] Record frontend/backend branch, commit, deployed bundle hashes, and database engine/version.
- [x] Record actual production column nullability, foreign keys, and delete actions for
  `variable`, `component_variable`, `instruction`, and `component_instruction`.
- [x] Claim planned files in the shared active-bug document.
- [x] Confirm BUG-001 disposable-copy verification status.
- [x] Confirm BUG-002, BUG-003, and BUG-004 ownership so work does not overlap.
- [ ] Manually accept the existing three private drag hooks and prove no cross-workspace drag
  completion/reset before marking BUG-003 complete.
- [x] Export a read-only graph audit for Bot Job and Component owners.
- [x] Create sanitized fixtures for:
  - a simple parent/child pair;
  - GET plus multiple consumers;
  - Web Element plus LOOP and positional body;
  - IF/ELSE/ENDIF plus ordinary body rows;
  - GOTO and EXCEL GOTO;
  - an ownerless variable;
  - Components `Check payment` aggregate.
- [x] Capture current Memory List `+`, apply, and reorder behavior as golden tests.
- [x] Back up the production database before any later data phase.
- [x] Update `../VARIABLE_SYSTEM_REDESIGN.md` in a separate documentation commit so its old
  cascade/refusal rules no longer claim to be the future design.

Automated P0 evidence is recorded in `P0_VARIABLE_GRAPH_BASELINE_2026_07_29.md`. The unchecked
three-window item is an explicit manual acceptance gate; it is not silently treated as complete.

Acceptance:

- No application behavior or production row changed.
- Every future phase has an executable fixture and a known rollback commit.

Rollback:

- Documentation/test-fixture revert only.

### P1 - Engine and execution-path audit

Goal: remove speculation before changing command semantics or allowing unresolved drafts.

Tasks:

- [x] Trace GET, SET, E, CK, PDF CHECK, CSV CHECK, LOOP, REFRESH_LOOP, GOTO, and EXCEL GOTO from
  persisted fields through local `executeJob()` and the external Engine contract.
- [x] Record whether each action reads `parentId`, `variableId`, variable owner
  `instruction_id`, `parentBlockId`, or derived Excel fields.
- [x] Map every Test Run and Launch entry point:
  - Bot Job Details;
  - Main Dashboard;
  - Page Scanner/prelaunch;
  - external Engine launch;
  - any legacy/direct caller.
- [x] Record that `ScannerPreLaunchExecutionGate` currently controls execution concurrency, not
  graph readiness; do not mistake it for the new relationship gate.
- [x] Identify the exact readiness insertion points in `ScannerPreLaunchStarter` and
  `ScannerTestRunPreparationFlow`, before browser/Engine work starts.
- [x] Audit `ScannerPreLaunchPreparation.loadAndFixExcelGoto`; strict execution must not silently
  repair an invalid EXCEL GOTO graph at runtime.
- [x] Decide the revision-bound execution-preflight request/response.
- [x] Add characterization tests for current SET literal behavior and all consumer actions.
- [x] Lock the command capability matrix in the shared command registry/specification.

Evidence and the locked capability/entry-point matrices are recorded in
`P1_ENGINE_EXECUTION_SEMANTICS_2026_07_29.md`.

Acceptance:

- No action semantics in the roadmap depend on an unverified assumption.
- The execution gate covers every start path, not only one React button.

Rollback:

- Tests and documentation only.

### P2 - Pure React relationship classifier

Goal: calculate relationship health without changing rendering or persistence.

Tasks:

- [x] Add `instructionRelationshipPolicy.ts`.
- [x] Add `instructionRelationshipGraph.ts`.
- [x] Type normalized `parentId`, `parentBlockId`, and `variableId` facts as `number | null`;
  widen the shared transport DTO to accept explicit `null`.
- [x] Add an explicit relationship role (`WEB_ELEMENT`, variable command, structural boundary,
  navigation, neutral command) from a stable action policy rather than label text.
- [x] Do not claim scanned-vs-authored provenance: the current schema has no reliable
  `source_kind`. If product behavior later depends on provenance, add it through a separate
  migration rather than guessing from names/actions.
- [x] Classify typed relationships and candidate compatibility from the current rendered graph.
- [x] Reclassify ownerless memory separately from truly invalid command relationships.
- [x] Keep current drag/delete/Memory behavior unchanged.

Focused tests:

- valid/missing/out-of-order element target;
- LOOP valid/missing/late anchor;
- conditional root and boundary errors;
- GOTO target treated as a Block relation;
- valid/invalid variable binding;
- GET producer order;
- strict compound-owner candidate filtering;
- current SET compatibility.

Acceptance:

- The pure classifier is deterministic and has no WebSocket, React state, or database access.
- Existing Memory tests are unchanged and green.

Rollback:

- Remove the new pure modules and type additions; no protocol/data rollback.

### P3 - Relationship details and read-only repair chips

Goal: stop hiding relationship problems before allowing mutation.

Tasks:

- [ ] Extract `InstructionRelationshipDetails`.
- [ ] Replace invalid `renderOperations -> null` paths.
- [ ] Render all states from Section 5.
- [ ] Keep reconnect chips read-only in this phase.
- [ ] Preserve the last valid grid during refresh/error.
- [ ] Gate shared `GridItem` rendering by server-advertised workspace capability and add a
  regression proving Components remain unchanged before P12.

Acceptance:

- No relationship error blanks a row or grid.
- Existing valid colored relationship details remain visually unchanged.
- No persistence request is introduced.

Rollback:

- Turn off `relationshipChipsV1` or revert only the extracted renderer.

### P4 - Execution relationship gate

Goal: make flexible authoring safe before free movement can persist a broken graph.

Tasks:

- [ ] Add pure TypeScript execution eligibility output with exact row IDs and messages.
- [ ] Display a bounded modal/list with row focus actions.
- [ ] Start in shadow/warn mode and record which existing jobs would be blocked.
- [ ] After repair is available or P0 proves no blocking legacy issue, block all mapped Test
  Run/Launch entry points for active unresolved rows.
- [ ] Preserve user Active flags.
- [ ] Bind preflight to exact authoritative owner, database graph version, content revision, and
  the actual requested run scope. Workspace epoch is additionally required for detached-workspace
  callers, not invented for Main Dashboard/legacy callers.
- [ ] Recompute readiness in Java from the exact server-loaded execution snapshot (or execute an
  immutable validated snapshot). React remains the user-facing diagnostic planner.
- [ ] Atomically recheck graph version immediately before Engine start so preflight cannot race a
  mutation.
- [ ] Validate only the reachable run plan: ONE/single-Block Test Run validates that selected
  active scope; full/from-selected runs validate their actual active reachable scope.
- [ ] Cover current runtime facts: GET and SET need compatible Web Elements; E currently needs its
  persisted target plus variable/producer; CK/PDF/CSV need their current target/variable contract;
  LOOP needs its anchor; IF needs valid structure; GOTO needs its target Block.
- [ ] Make active/inactive policy explicit and identical for Bot Job Details Test Run, full Launch,
  Main Dashboard Launch, and scanner/prelaunch.
- [ ] Disable silent runtime `fixExcelGoto`; move legacy repair to an explicit migration/action.
- [ ] Make full Launch return immediately when definition load fails instead of reporting an error
  and continuing.

Acceptance:

- No active unresolved required relationship can reach execution.
- An ownerless variable alone is not an error; the command role determines eligibility.
- Inactive rows remain visible but do not block execution unless current execution policy says
  otherwise.

Rollback:

- Before broken drafts exist, shadow/UI behavior can be disabled without stored-data conversion.
- After P7 enables broken drafts, the backend gate cannot be disabled until all broken-authoring
  flags are off and an authoritative audit proves zero unresolved active rows.
- Do not enable broken-draft movement until hard gate and reconnect are both accepted.

### P5 - Additive version-3 mutation contract

Goal: land transport and transaction support without changing user behavior.

Tasks:

- [ ] Define typed React DTOs for owner, layout, relationship patches, variable-owner patches, and
  expected old values.
- [ ] Add a dedicated `InstructionGraphMutationDTO`; do not overload `SplitDTO` with ambiguous
  nullable fields.
- [ ] Add version-3 structural contract validator in Java.
- [ ] Add a database-owned graph-state row with a compound workspace/owner primary key,
  atomic first-row creation, owner-deletion cleanup/tombstone policy, and compare-and-set version.
  Register the production migration in
  `com.allinweb.ch.db.MigrationRunner`.
- [ ] Make fresh SQLite/Access initialization and migration ordering self-healing; capability
  activation fails closed if graph-state support is absent.
- [ ] Make every remaining v2 and v3 graph writer bump the same version in its own transaction.
- [ ] Add one transaction that applies layout and patches atomically.
- [ ] Verify the complete final state inside the open transaction, commit, then reload the
  authoritative state before acknowledgement/publication.
- [ ] Broadcast correlated grid and Variables snapshots after commit.
- [ ] Advertise backend capability/version to React.
- [ ] Derive workspace/table/owner from the authenticated server session and compare any client
  owner assertion against it.
- [ ] Keep version 2 as the active default.
- [ ] Define the content-hash scope separately from graph-version CAS. Either expand the hash to
  all execution-relevant Block/instruction/variable/reference facts or treat graph version as the
  authoritative concurrency token.

Focused tests:

- forged/cross-owner ID refusal;
- duplicate/missing full-layout IDs;
- stale revision refusal;
- expected-old-target mismatch;
- mid-transaction rollback after layout, relationship, and variable-owner stages;
- idempotent repeated request ID;
- correlated realtime response;
- two concurrent writers using the same base version: exactly one commit;
- v2 writer followed by v3 writer proves the shared version cannot become stale;
- fresh SQLite/Access/PostgreSQL database capability checks.

Acceptance:

- Version 3 can reproduce a version-2 no-relation-change move exactly.
- Capability off leaves production on version 2.

Rollback:

- Disable `rowMoveContractV3`; retain additive graph-state/version support and keep every v2 writer
  bumping it. Do not revert to a v2 path that bypasses CAS.

### P6 - Bot Job same-Block single-row drag

Goal: move one Bot Job instruction without carrying positional or explicit families, but activate
only moves that preserve every required relationship in this phase.

Tasks:

- [ ] Add `instructionFreeMove.ts`.
- [ ] Make a gap drop move exactly the selected row.
- [ ] Preserve valid relationships unchanged.
- [ ] If a proposed move would create a new broken/detached relationship, preview the issue and
  refuse activation until P7 provides repair. Do not persist unresolved drafts in P6.
- [ ] Keep IF-family boundaries constrained in this first release.
- [ ] Keep Block movement and cross-Block row movement unchanged.
- [ ] Use private Bot Job drag state and one correlated request.
- [ ] Roll back the optimistic layout on refusal/timeout.

Acceptance:

- Wait/Pause/ordinary rows move freely.
- GET/SET/E/CK/CHECK rows move individually when all audited required relations remain valid.
- Moving inside a LOOP body does not drag the positional body.
- LOOP itself moves only when its anchor outcome is explicit.
- GOTO destination remains unchanged.
- The grid never disappears on refusal.

Rollback:

- Disable `freeSameBlockMoveV1`; valid P6 rows remain compatible with the current group planner.

### P7 - Explicit reconnect actions

Goal: repair one typed relationship without implicit choices.

Deliver in separate commits:

#### P7A - Element and LOOP targets

- [ ] Add `ReconnectRelationshipDialog`.
- [ ] Reuse `SearchBox` with exact-owner compatible candidates.
- [ ] Support `RECONNECT_PARENT` and `RECONNECT_LOOP`.
- [ ] Preview expected old/new target and impacted rows.

#### P7B - Block and conditional targets

- [ ] Support `RECONNECT_BLOCK`.
- [ ] Support `REPAIR_CONDITIONAL` without moving positional body rows.

#### P7C - Variable binding and owner transfer

- [ ] Support selecting an existing compatible variable.
- [ ] Build the preview/contract for leaving a variable ownerless as memory, but keep `DETACH`
  disabled until P8 has cross-dialect persistence and backup/restore support.
- [ ] Show every impacted command before transferring variable ownership.
- [ ] React submits the exact parent compatibility projection with the owner patch.
- [ ] Never transfer ownership based only on physical proximity.

#### P7D - Broken-draft activation

- [ ] Hard-enable the authoritative execution gate.
- [ ] Enable `MOVE ONLY` with explicit clean `CLEAR`/`DETACH` patches.
- [ ] Enable `MOVE + RECONNECT` with one atomic unchanged/full-layout plus relation transaction.
- [ ] Ensure Bot Job capability checks prevent shared `GridItem` code from activating Component
  behavior before P12.

Acceptance:

- Unambiguous suggestions are explicit one-click choices.
- Ambiguous targets require selection.
- Stale expected-old values refuse safely.
- Successful reconnect updates Grid and Variables pages in real time.

Rollback:

- Stop new broken-draft moves by disabling P7D, but retain null-safe DTOs, chips, reconnect, and
  the mandatory backend execution gate for already-saved drafts.
- Reconnect UI can return to read-only only after an audit proves there are no unresolved rows that
  need it.

### P8 - Durable ownerless memory variables

Goal: make explicit owner detachment preserve the variable and its commands. Owner instruction or
Block deletion that would currently cascade a variable remains blocked until delete-v3 in P10.

Tasks:

- [ ] Change React health semantics from unconditional `MISSING_OWNER=ERROR` to role-aware
  `MEMORY_ONLY`.
- [ ] Add `VariableOwnerDetachTransaction`.
- [ ] Make the detach operation use the connection/transaction owned by the parent graph mutation;
  it must never open or commit an independent transaction.
- [ ] Verify actual owner-column nullability/FKs in every supported production dialect; add a
  migration first if any installation cannot store a null owner.
- [ ] Preserve instruction `variable_id` links for surviving commands.
- [ ] Block owner instruction/Block/bulk deletion while it would use destructive v2 cascade;
  P10 supplies atomic detach-plus-delete.
- [ ] Require explicit confirmation for variable deletion.
- [ ] Make Memory dependency/apply/remapping tolerate legitimate ownerless variables without
  changing `+` selection breadth, copy-vs-move behavior, or order.
- [ ] Update backup/restore paths to preserve null owners.
- [ ] Verify SQLite, PostgreSQL, and Access behavior.
- [ ] Use transaction-level detach first. Consider `ON DELETE SET NULL` only in P14 because
  SQLite/Access may require table rebuilds and full migration verification.

Acceptance:

- Explicit detach leaves one ownerless variable and all variable-linked commands.
- `MEMORY_ONLY` is valid persisted data, but no current active command becomes executable
  ownerless unless P1 explicitly proves that role.
- GET/SET/E/CK/PDF/CSV follow P1 readiness rules; LOOP uses `LOOP_ANCHOR`, not variable ownership.
- No variable disappears implicitly.
- Destructive owner deletion stays disabled until P10.

Rollback:

- Requires database backup.
- Disable `durableMemoryVariablesV1` only after restoring affected data or keeping the new
  backward-compatible null-owner rows supported.
- Do not combine this phase with P9 or P10.

### P9 - Owner uniqueness and automatic variable creation

Goal: create one variable with every newly persisted Web Element.

Tasks:

- [ ] Run the focused duplicate-owner audit and back up the target database.
- [ ] Repair or explicitly quarantine every duplicate owner; ambiguous duplicates are refused, not
  arbitrarily reused.
- [ ] Add and verify dialect-specific owner-scoped uniqueness for non-null owners before enabling
  creation (PostgreSQL/SQLite partial indexes and Access null/index behavior require separate
  tests).
- [ ] Add idempotent owner-scoped create-or-load behavior.
- [ ] Persist Web Element and variable in one transaction.
- [ ] Use stable generated naming without overwriting user-renamed variables.
- [ ] Apply first when a Page Scanner DTO is materialized into `instruction`; do not create
  variables for every `scanned_element` repository observation/upsert.
- [ ] Apply to Command Editor/new Web Element paths.
- [ ] Apply to Component creation only in a separate subphase.
- [ ] Do not backfill historical rows yet.
- [ ] Lock a stable Web-Element discriminator before activation. If action-registry role is not
  sufficient, add/populate `source_kind` before using provenance in creation/delete rules.
- [ ] Verify fresh SQLite/Access/PostgreSQL initialization creates/checks the invariant even when
  migration ordering differs; capability stays off on mismatch.

Acceptance:

- Retried scanner requests produce one element and one variable, not duplicates.
- Two concurrent creators for the same owner produce exactly one variable.
- Failure after either insert rolls back both.
- Existing owner variable is reused, not replaced.

Rollback:

- Disable `automaticVariableCreationV1`; newly created valid variables remain data.

### P10 - Delete selected/direct/full explicit modes

Goal: give precise deletion choices without positional cascades.

Tasks:

- [ ] Extend the React planner with `SELECTED_ONLY`, `DIRECT_ATTACHMENTS`, and
  `FULL_EXPLICIT_GRAPH`.
- [ ] Define direct and transitive traversal by typed explicit edges only.
- [ ] Preview deleted IDs, surviving repairs, detached variable owners, and preserved variables.
- [ ] Keep modal detail capped at five rows with total counts.
- [ ] Add delete contract version 3 and atomic persistence.
- [ ] In the same connection/transaction, detach preserved variable owners, apply surviving
  instruction repairs, delete the exact rows, verify final state, advance graph version, and
  commit.
- [ ] Cover instruction delete, Block delete, bulk delete, and Component equivalents before each
  corresponding UI is enabled.
- [ ] Preserve version 2 only for graphs where durable-owner semantics are not active; never
  fallback to a path that would destroy an ownerless/preserved variable.

Required examples:

- delete ordinary authored command only;
- delete LOOP only and preserve its anchor/body;
- delete Web Element only and preserve variable/commands;
- delete GET only and show missing producer on consumers;
- delete IF root only when P7 can represent/repair survivors;
- delete GOTO without deleting its destination Block;
- direct mode does not include grandchildren;
- full mode follows explicit transitive edges only.

Rollback:

- Stop new delete-v3 actions if needed, but do not re-enable destructive v2 owner/Block deletion
  after P8 durable variables exist.
- Retain v3 reading/repair compatibility for rows already detached under P8.

### P11 - Cross-Block and conditional freedom

Goal: extend accepted single-row semantics after reconnect and execution gates are proven.

Subphases:

- [ ] P11A: ordinary and variable-command cross-Block movement.
- [ ] P11B: LOOP cross-Block movement with explicit anchor result.
- [ ] P11C: optional individual IF/ELSEIF/ELSE/ENDIF draft movement.
- [ ] P11D: empty-Block preservation/removal policy.
- [ ] P11E: keyboard move parity with pointer drag.

Acceptance:

- Every new unresolved state has a chip, reconnect path, and preflight rule before activation.
- Parent/variable/block IDs never resolve across owners.

Rollback:

- Separate flags per subphase; same-Block v3 remains available.

### P12 - Independent Components parity

Goal: reproduce accepted Bot Job behavior through Component-specific orchestration.

Prerequisites:

- BUG-002 and BUG-003 complete.
- Bot Job P6-P10 manually accepted.

Tasks:

- [ ] Keep `COMPONENT_ROW_MOVE` and Component-specific DTO/transaction.
- [ ] Use the same pure relationship policy with Component owner facts.
- [ ] Keep private Component drag state and rollback snapshot.
- [ ] Add Component-specific reconnect and delete contracts.
- [ ] Add Component raw-variable facts and realtime publication; do not reuse the Bot Job
  `VariablesWorkspaceService` owner/session implicitly.
- [ ] Preserve complete Component Memory copy behavior.
- [ ] Add automatic Component variable creation in its own subphase.

Acceptance:

- No Bot Job sender/owner/session is reused.
- Component edits never mutate Bot Job rows.
- `Check payment` remains a complete reusable copy fixture.

Rollback:

- Disable `componentRowMoveContractV3` without disabling Bot Job v3.

### P13 - Interactive Variables page

Goal: turn current diagnostics into safe repair entry points.

Tasks:

- [ ] Add owner/memory state badges.
- [ ] Add the single authoritative focus-to-grid transport for owner, producer, and consumer rows,
  including request correlation and graceful behavior when Bot Job Details is closed.
- [ ] Add reconnect owner/variable actions through the same contracts as GridItem.
- [ ] Add impacted-command preview before owner transfer.
- [ ] Add explicit variable delete with last-connection warning.
- [ ] Preserve last valid snapshot through refresh errors.
- [ ] Keep live runtime value editing out of this phase.

Acceptance:

- Grid and Variables page converge on the same committed revision.
- No Variables-page action invents a separate persistence rule.

Rollback:

- Disable Variables actions; retain the current read-only page.

### P14 - Historical repair and database constraints

Goal: enforce durable invariants only after production data is clean.

Tasks:

- [ ] Run read-only audit and export the exact repair plan.
- [ ] Back up every target database.
- [ ] Repair duplicate owners, stale variable links, missing parent projections, and missing
  `parent_block_id` only where unambiguous.
- [ ] Leave ambiguous rows unchanged and report them.
- [ ] Verify/repair the owner uniqueness installed in P9 and add any remaining safe
  owner-scoped/link constraints.
- [ ] Consider owner-scoped variable-link constraints only after circular-FK behavior is proven.
- [ ] Update fresh-install schemas after migrations pass.
- [ ] Implement the production migration as a dated class under
  `src/main/java/com/allinweb/ch/db/migrations/` and register it in
  `src/main/java/com/allinweb/ch/db/MigrationRunner.java`; resource V00x files alone do not
  migrate the running production database.
- [ ] Verify backup and restore across null-owner variables.

Acceptance:

- Re-run audit reports zero prohibited duplicates/cross-owner links.
- Migrations are repeatable/idempotent and have a tested down/restore path.

Rollback:

- Restore the pre-phase database backup.
- Remove only constraints introduced in this phase; do not delete repaired user data blindly.

### P15 - Retire duplicated legacy semantics

Goal: complete the React-authoring/Java-persistence boundary.

Tasks:

- [ ] Remove version-3 calls to Java connected-group inference.
- [ ] Retire Java semantic move checks only after TypeScript parity and execution gate acceptance.
- [ ] Keep structural owner/revision/permutation/expected-old-value validation.
- [ ] Remove version 2 only after production acceptance and rollback window.
- [ ] Remove expired feature flags in separate commits.
- [ ] Update all authoritative roadmaps and operations documentation.

Acceptance:

- Java cannot add unseen rows to a React move/delete request.
- React cannot persist outside its owner or bypass revision/transaction checks.
- No dead dual-path code remains.

Rollback:

- Before v2 removal, switch capability back to v2.
- After v2 removal, rollback requires the tagged pre-removal release.

### P16 - Runtime values and pause/edit/resume

Goal: stream initial/current variable values and edit them safely while paused.

This is intentionally deferred. It requires an Engine run-scoped API for:

- execution/run ID;
- value snapshots and sequence numbers;
- pause acknowledgement;
- compare-and-set variable edit;
- resume acknowledgement;
- cleanup on stop/failure.

Do not simulate this with authoring-table updates while a run is active.

## 11. Focused verification strategy

### Frontend suites

Protect existing behavior:

- `instructionDependency.test.ts`
- `useInstructionMemory.test.ts`
- `GridItemComp.memoryParity.test.tsx`
- `memoryList.groups.test.ts`
- `MemoryList.commandLifecycle.test.tsx`
- `useMemoryListDrag.test.ts`

Extend/add:

- `instructionMove.test.ts`
- `instructionDelete.test.ts`
- `variablesGraph.test.ts`
- `variablesWorkspace.contract.test.ts`
- `VariablesPage.test.tsx`
- `GridItem.dragMessages.test.tsx`
- `InstructionCommandPanel.test.tsx`
- `useInstructionDrag` and `useComponentInstructionDrag` DTO/verb/owner tests;
- `useBotJobDetailsController`, `MainDashboard`, and Page Scanner/Scanner execution-entry tests;
- `InstructionRelationshipDetails`, reconnect-dialog, and execution-preflight-dialog tests;
- Bot Job vs Component capability-isolation tests;
- mid-flight capability/epoch rollback tests;
- null-owner variable round-trip tests;
- pure relationship policy/graph/preflight tests;
- Bot Job and Component submitter contract tests.

### Backend suites

Extend/add:

- `InstructionMoveTransactionTest`
- `InstructionDeleteContractValidatorTest`
- `RowMoveServicesTest`
- `ComponentRowMoveRealDbTest`
- `VariableRelationshipServiceTest`
- `VariablesWorkspaceServiceTest`
- `VariableUpdateTransactionTest`
- `ComponentVariableCreationTransactionTest`
- Test Run/Launch/prelaunch gate tests;
- exact ONE/selected/full reachable-plan readiness tests;
- concurrent v2/v3 graph-version CAS tests;
- owner/block/bulk delete variable-detach tests;
- Component raw-variable realtime tests;
- database migration, backup, restore, idempotency, and injected-rollback tests.

### Golden invariants

Every phase that changes persistence proves:

1. exact owner scope;
2. exact ID set;
3. exact order;
4. exact expected-old/new relationship values;
5. no positional cascade;
6. no source-row mutation during Memory copy;
7. no partial transaction;
8. one correlated response;
9. latest graph remains visible on error;
10. Grid and Variables snapshots share the committed graph version and content revision;
11. a late response from a retired capability/epoch cannot mutate current UI state;
12. Component capability remains off during Bot Job-only phases.

## 12. Error and observability requirements

Every mutation log includes:

- operation/version;
- request ID;
- workspace/session/epoch;
- owner identifiers;
- base and committed graph version plus content revision;
- dragged/selected ID;
- exact updated/deleted/repaired IDs;
- variable owner patches;
- result and refusal code;
- elapsed transaction time.

Do not log locator secrets, variable values, credentials, or full payloads containing sensitive
data.

User-facing errors distinguish:

- stale workspace;
- owner mismatch;
- unsupported contract;
- expected-old relationship changed;
- transaction rollback;
- realtime refresh failure after commit;
- execution preflight refusal.

If commit succeeds but refresh fails, report that persistence succeeded, keep the last valid grid,
and offer an authoritative refresh. Do not retry the mutation automatically.

## 13. Claude/Codex reconciliation record

Accepted from both:

- single-row movement as the default;
- explicit reconnect UX;
- React semantic planning;
- Java atomic persistence;
- strict realtime correlation;
- execution blocking for unresolved active rows;
- frozen Memory List `+`;
- durable variables.

Accepted with modification:

- variable-centric ownership applies to variable-capable commands, not every command;
- `parentId` is a compatibility projection only where the Engine still needs it;
- same-Block freedom comes before cross-Block freedom;
- conditional boundaries remain constrained in the first release;
- delete is selected/direct/full explicit graph, never positional span deletion.

Deferred pending audit:

- SET as a variable-source consumer;
- E/CK/PDF/CSV running without a Web Element target;
- variable-anchored LOOP;
- universal variable assignment to every command;
- live runtime value editing.

Rejected:

- silently choosing the row above as a new relation;
- persisting dangling IDs;
- deleting variables automatically with their owner;
- moving/deleting innocent positional body rows;
- activating free broken-state authoring before an execution gate exists;
- sharing live drag controllers between Bot Job, Component, and Memory List pages.

## 14. Definition of done

The redesign is complete only when:

- [ ] one Bot Job instruction can move independently within and across Blocks;
- [ ] every broken required relation is visible and repairable;
- [ ] IF/LOOP positional bodies are never treated as delete ownership;
- [ ] variables survive owner deletion as legitimate memory;
- [ ] new Web Elements receive one variable atomically;
- [ ] delete selected/direct/full modes persist exactly what React confirmed;
- [ ] Test Run and Launch refuse unresolved active graphs on every entry path;
- [ ] Java v3 performs structural validation and exact persistence without semantic group
  expansion;
- [ ] Components have independent parity;
- [ ] Variables page edits use the same contracts and revisions;
- [ ] Memory List `+`, apply, fresh-ID copy, and reorder behavior are unchanged;
- [ ] database audits, constraints, backup, restore, and rollback are proven;
- [ ] focused and full regression suites pass;
- [ ] manual runtime acceptance passes on disposable Bot Job and Component copies;
- [ ] final source commits and deployed frontend bundle hashes are documented.

## 15. Immediate next task

Start P0 only:

1. claim files/owners;
2. capture baseline commits and deployed bundle hashes;
3. create sanitized golden graph fixtures;
4. run the read-only production relationship audit;
5. update `VARIABLE_SYSTEM_REDESIGN.md` to point to this roadmap and remove superseded future
   cascade/refusal claims;
6. stop for review before implementing P1 or changing application code.
