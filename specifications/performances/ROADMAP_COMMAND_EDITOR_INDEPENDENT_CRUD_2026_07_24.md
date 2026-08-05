# Independent Command Editor CRUD Roadmap

Date: 2026-07-24
Status: Phase 0 complete; Phase 3 non-empty Block/Instruction selection slice implemented

## Objective

Turn the detached **Command Editor** into a complete, independent workspace for the active Bot Job.
The page must:

- load every Block and every Instruction for the active Bot Job;
- load every Web Field needed as a command target;
- expose the complete Java-owned command catalogue;
- let the user select or change the current Block and Instruction;
- create commands before, after, or at the end of the selected Block;
- update an editable command;
- preview and delete a command safely;
- remain synchronized in real time with Bot Job Details;
- continue using one singleton detached Command Editor window;
- keep all ownership, graph, compatibility, and persistence rules in Java.

This roadmap extends `ROADMAP_NEW_COMMAND_INLINE_REACT.md`. That document describes the original
inline/floating command migration. The present document covers the newer detached-window,
independent-selection, complete-CRUD requirement.

## Implementation Checkpoint - 2026-07-24

The first deployable slice is implemented:

- React consumes one complete `workspaceBootstrapResponse`/`workspaceTarget`/selection snapshot
  without the detached child's redundant mount-time bootstrap.
- The target ref is assigned synchronously with the rendered snapshot.
- Loading, ready, empty, error, and Retry states are explicit.
- Java returns every owner-scoped Block and Instruction, every current Web Field, the Java command
  catalogue, variables, draft, graph revision, row capabilities, and selected IDs.
- Production Instruction rows are enriched with Block name/order before deterministic sorting.
- `commandEditor.select` validates the registered manager transport, active Bot Job/workspace,
  organization, Block, Instruction, and Block membership.
- Accepted selection rotates `bindingEpoch`, advances `selectionRevision`, and returns one complete
  correlated snapshot. Failed or stale selection keeps the previous binding.
- React Block/Instruction selectors send exactly one correlated selection request and ignore stale
  request IDs, binding epochs, and snapshots.
- Retargeting an already-open Command Editor validates the full snapshot before committing the new
  binding.
- Command Editor transport identity is derived from the registered WebSocket transport for every
  `commandEditor.*` operation.
- Focused React verification passed: 2 suites, 27 tests.
- The production React build succeeded and was mirrored into `src/main/resources/build`
  (`main.362b81d6.js`, `main.c23c7909.css`).
- A focused Java unit test was added for owner validation, ordering, and Block enrichment. It was
  not executed because this repository's standing rule prohibits Maven/Java builds and tests.

This is not yet complete CRUD. The current selection contract requires a non-empty Block and a
valid Instruction. Immutable direct-read repository work, historical-row classification,
empty-Block append, typed delete preview/delete, deterministic recovery after external deletion,
and two-way Bot Job Details/Page Scanner synchronization remain in the phases below.

## Original Empty-Page Finding (Fixed In Phase 0)

The database and Java bootstrap are not empty. The initial data is lost at the React boundary.

Original failing sequence:

1. `GridItem.tsx` sends `commandEditor.workspaceOpen` for one instruction.
2. `CommandEditorWorkspaceService.open(...)` binds the singleton editor to that instruction.
3. The detached page connects as `commandEditorManager`.
4. `CommandEditorPage.tsx` sends `commandEditor.workspaceBootstrap`.
5. `CommandEditorWorkspaceService.bootstrap(...)` already merges the complete
   `CommandEditorService.bootstrap(...)` result into the response.
6. That response contains variables, Web Fields, Blocks, command definitions, the selected draft,
   graph revision, and row capabilities.
7. `CommandEditorPage.tsx` extracts only the selected target, clears `panelMessages`, and discards
   the rest of the merged bootstrap.
8. The newly mounted `InstructionCommandPanel` tries a second `commandEditor.bootstrap`.
9. The child effect can run before the parent effect copies the target into `targetRef`.
   `sendPanelCommand(...)` then sees a null or stale target and silently drops the request.
10. `commands`, `webFields`, and `graphRevision` remain empty. `commandsReady` stays false and the
    page appears to contain no usable data.

Primary frontend evidence:

- `abr-react-ts-grid/src/components/CommandEditorPage.tsx`
  - target ref synchronization: lines 114-116;
  - discarded merged bootstrap: lines 155-183;
  - target-dependent send: lines 319-329.
- `abr-react-ts-grid/src/components/InstructionCommandPanel.tsx`
  - redundant bootstrap: lines 106-112 and 130-132;
  - disabled readiness state: line 237.

Primary backend evidence:

- `CommandEditorWorkspaceService.bootstrap(...)`, lines 156-179, already returns one merged payload.
- `CommandEditorService.bootstrap(...)`, lines 104-142, already loads the available data.

### Implemented P0 correction

Use one atomic, typed `workspaceBootstrapResponse` to hydrate the page.

- Parse the target and all editor collections from the same response.
- Assign the current target ref synchronously before rendering target-dependent children.
- Pass the initial snapshot into the editor state/reducer.
- Remove the mount-time dependency on a second bootstrap request.
- Add explicit `loading`, `ready`, `empty`, `stale`, and `error` states with a Retry action.
- Add a `CommandEditorPage` integration test that fails if the first snapshot is discarded.

The small synchronous ref assignment can protect the transition, but the final architecture must
consume the already-merged snapshot and remove the duplicate request race.

## Read-Only Production Evidence

The configured production SQLite database was queried read-only on 2026-07-24:

`D:\Projects\ARWebBancaStato\ARWeb\database.db`

For Bot Job 5 at the time of inspection:

- 16 Blocks existed;
- 150 total Instruction rows existed;
- 139 rows were native Web Field rows using the current input/output/click classification;
- 11 rows were command rows.

Across all Bot Jobs, 62 distinct raw `actions` values existed. Most variation came from dynamic
native actions such as `I:<field>` and `O:<field>`. Persisted command-like values included
`CSV CHECK`, `E`, `GET`, `H`, `LOOP`, `PAUSE`, `Q`, and `SWIPE_DOWN`.

This confirms that the empty Command Editor is a hydration problem, not an empty-database problem.
It also confirms that raw `actions` values cannot be treated as one flat command catalogue.

## Current Architecture And Gaps

### What already works

- One detached `commandEditorManager` window is opened or focused.
- The backend validates the active Bot Job, organization, workspace epoch, and selected instruction.
- `CommandRegistry` owns canonical command metadata.
- `CommandOperationCodec` owns canonical operation serialization.
- `CommandEditorService.apply(...)` supports create-before, create-after, and edit.
- Apply requests use request IDs and graph revision validation.
- Successful detached Apply sends its acknowledgement to Command Editor and an authoritative
  instruction snapshot to `botJobTasks`.
- Variable create/update/delete already has a React workspace and Java service.
- Conditional, loop, move, split, and delete validators already exist and must be reused.

### What prevents independent CRUD

- The workspace binding owns one fixed instruction rather than one Bot Job plus mutable selection.
- `canonicalIdentity(...)` replaces client Block/Instruction values with the original binding.
- Bootstrap does not return an `instructions` collection or `selectedBlockId`.
- React has no Block selector, Instruction selector, or complete instruction collection.
- Command deletion is not exposed through the detached editor.
- Create requires an existing anchor and cannot create the first command in an empty Block.
- Bot Job Details mutations are not pushed back into Command Editor.
- The Command Editor ignores the authoritative `instructions` collection returned by Apply.
- Deleting the bound instruction makes `refreshBinding(...)` fail instead of choosing a valid next
  selection.
- The detached operation allowlist intentionally rejects generic grid mutations and currently has
  no safe editor-specific delete operation.
- Existing instruction deletion and several Block mutations remain inside the large
  `SimpleWebSocketServer` mutation switch.
- Several current persistence paths perform ordering, insert/update, relationship updates, and
  normalization in separate transactions.
- Some inserts discover new IDs by comparing before/after ID lists instead of generated keys.
- Some update statements identify rows by instruction ID without an owner predicate.
- `PerformLists` is shared mutable view state and is unsafe as the source of an immutable detached
  workspace snapshot.
- The current graph revision omits Blocks and editable fields such as name, active state, waits,
  locators, and hold values.
- There is no detached Command Editor lifecycle, hydration, selection, or realtime integration test.

## Important Data-Model Decision

A command is not stored in a separate command table. Commands and native Web Fields are both rows
in `instruction` (or `component_instruction` for the component context).

The independent editor therefore needs one authoritative instruction read model that classifies
each row as:

- native Web Field;
- editable command;
- structural boundary;
- legacy/unsupported command;
- other protected row.

Native Web Fields must remain available as command targets. They must not be silently converted or
deleted through a generic command form. Java capabilities decide which actions are legal for every
selected row.

No database migration is expected merely to display and edit commands. A migration is required only
if later implementation proves that the existing schema cannot provide an owner-scoped transactional
mutation safely.

## “All Commands” Definition

The current `CommandRegistry` contains 19 authoring definitions:

- SET
- GET
- CK
- PDF CHECK
- CSV CHECK
- Extract Field (`E`)
- IF
- GOTO
- EXCEL GOTO
- LOOP
- REFRESH LOOP
- REFRESH
- NEXT / ENTER
- SWIPE UP
- SWIPE DOWN
- Wait (`H`)
- PAUSE
- Close Browser (`Q`)
- Screenshot (`P`)

That list must not be assumed complete until it is compared with:

- distinct persisted instruction actions;
- runtime action constants;
- `executeJob()` handling;
- `CommandOperationCodec`;
- historical import/export fixtures.

Runtime constants also reference values such as `REFRESH_HOLD`, `BACK`, and `NEXT ROW`.

The finished page must:

- display every supported authoring command from the Java catalogue;
- keep unsupported historical command rows visible;
- mark unsupported historical rows read-only with a reason;
- show incompatible supported commands as disabled with a Java-provided reason instead of hiding
  them;
- never let React invent or serialize a command that Java does not support.

## Target Workspace Snapshot

One immutable response should hydrate and refresh the whole page:

```text
CommandEditorWorkspaceSnapshot
  requestId
  workspaceEpoch
  bindingEpoch
  selectionRevision
  graphRevision
  editorRevision
  botJob
    id
    name
    homeBankingId
    organizationName
  selectedBlockId
  selectedInstructionId
  blocks[]
  instructions[]
  webFields[]
  commandDefinitions[]
  variables[]
  selectedDraft
  selectedCapabilities
  warnings[]
```

Required collection rules:

- Blocks include empty Blocks and are ordered by Block order, then ID.
- Instructions include every row and are ordered by Block order, instruction order, then ID.
- Web Fields include every native Web Field in the active Bot Job, not only the current selection.
- Each Web Field carries stable IDs, Block identity, name, action, tag, active state, and the locator
  summary required by the UI.
- Command definitions include fields, target kind, placement support, create/edit support, and
  disabled reasons.
- The selected draft is decoded in Java.
- SQL/load failures are top-level typed errors, not fake rows inside an otherwise successful array.

## Target User Interface

The detached page keeps the established floating-page header and contains:

1. **Selection bar**
   - active Bot Job and organization;
   - searchable Block selector;
   - searchable Instruction selector filtered by the selected Block;
   - Refresh.

2. **Instruction/Web Field browser**
   - all Instructions for the selected Block;
   - filter for Web Fields, commands, structural rows, active/inactive, and text;
   - optional all-Blocks Web Field search;
   - stable ID, order, name, action, type/tag, and status.

3. **Command catalogue**
   - all Java definitions remain visible;
   - valid definitions are enabled;
   - invalid definitions are disabled with the backend reason;
   - legacy rows show their stored action/operation without pretending they are editable.

4. **Command form**
   - placement: Before, After, Edit, or Append when supported;
   - command-specific fields from Java metadata;
   - Web Field, Variable, and destination Block choices;
   - Java-generated preview and warnings;
   - Apply and Reset.

5. **CRUD actions**
   - Create;
   - Update;
   - Delete with impact preview and professional confirmation;
   - no browser `alert()` or `confirm()`.

Selecting a Web Field from another Block may change the selected Block, but Java must validate that
an element-dependent command targets a Web Field in the legal Block.

## Workspace Ownership And Selection

Refactor `CommandEditorWorkspaceService.Binding` from instruction ownership to:

- active Bot Job ID;
- organization ID;
- Bot Job workspace epoch;
- manager transport;
- mutable selected Block ID;
- optional mutable selected Instruction ID;
- binding epoch;
- selection revision.

The source Bot Job Details transport must remain authorized to open/retarget the singleton editor,
but the selected instruction must no longer be permanently owned by that source transport.

Selection rules:

- `commandEditor.workspaceOpen` selects the instruction clicked in Bot Job Details.
- `commandEditor.select` lets the detached page select a Block and optional Instruction.
- Java reloads and validates both IDs against the active Bot Job.
- A foreign/stale Block or Instruction is refused.
- Each accepted selection advances `selectionRevision` and returns one complete snapshot.
- If the selected row is deleted, choose deterministically:
  1. next row in the Block;
  2. previous row in the Block;
  3. first row in the next valid Block;
  4. first row in the previous valid Block;
  5. selected Block with no Instruction;
  6. no selection when no Blocks exist.

## WebSocket Contract

### Read and selection

| Request | Response/event | Purpose |
|---|---|---|
| `commandEditor.workspaceBootstrap` | `commandEditor.workspaceBootstrapResponse` | Atomic initial snapshot |
| `commandEditor.refresh` | `commandEditor.snapshot` | Reload authoritative state |
| `commandEditor.select` | `commandEditor.selectResponse`, then `commandEditor.snapshot` | Select Block/Instruction |
| existing `commandEditor.workspaceOpen` | response plus `commandEditor.workspaceTarget` | Retarget from Bot Job Details |

### Command mutations

| Request | Purpose |
|---|---|
| `commandEditor.create` | Insert before/after or append to a Block |
| `commandEditor.update` | Update one backend-approved command row |
| `commandEditor.deletePreview` | Return exact affected rows and confirmation token |
| `commandEditor.delete` | Delete the confirmed command/group atomically |

`commandEditor.apply` may remain as a compatibility alias for typed create/update while clients are
migrated, but the final React hook should expose explicit create and update methods.

Every mutation requires:

- `requestId`;
- `workspaceEpoch`;
- `bindingEpoch`;
- `selectionRevision`;
- `graphRevision`;
- `editorRevision`;
- stable Block/Instruction IDs;
- typed command fields.

Every successful mutation sends:

1. correlated mutation acknowledgement;
2. Command Editor authoritative snapshot;
3. Bot Job Details authoritative instruction snapshot.

The acknowledgement must precede snapshots, matching the existing batching rule in
`InstructionRealtimePublisher`.

## Persistence Architecture

### Read repository

Create a JavaFX-free `CommandEditorReadRepository` that:

- reads the active Bot Job, Blocks, Instructions, Variables, and relationships directly;
- uses owner-scoped prepared statements;
- returns immutable per-request DTOs;
- never clears or repopulates shared `PerformLists`;
- returns deterministic order;
- distinguishes no rows from a load failure.

### Mutation service

Create a JavaFX-free `CommandMutationService` with one transaction per create/update/delete:

1. reload the authoritative owner-scoped graph;
2. validate workspace, selection, graph, and editor revisions;
3. validate command, Web Field, Variable, Block, and graph relationships;
4. shift/normalize order inside the transaction;
5. mutate the row or complete structural family;
6. use generated keys directly;
7. validate the resulting conditional and loop graph;
8. commit;
9. build one fresh immutable snapshot;
10. publish acknowledgement and snapshots.

All update/delete SQL must include the owning Bot Job (or organization for component tables), not
only the row ID.

Reuse:

- `CommandRegistry`;
- `CommandCapabilityService`;
- `CommandOperationCodec`;
- `ConditionalGraphValidator`;
- `LoopGroupService`;
- `InstructionMoveGroupService` and `InstructionMoveValidator`;
- `InstructionDeleteImpactService`;
- `InstructionSplitValidator`;
- `BotJobDetailsWorkspaceRegistry`.

Extract existing delete behavior from the generic `SimpleWebSocketServer` switch. Do not authorize
raw `UPDATE_BLOCKS` or other generic mutation messages from the detached editor.

### Empty Block behavior

The create contract must support an optional anchor:

- Before/After/Edit require a selected Instruction.
- Append may use a selected Block with no Instruction.
- Java calculates order 1 for the first row.
- Element-dependent commands still require a compatible Web Field, so an empty Block can initially
  accept only commands whose Java definition has no Web Field dependency.

## Realtime Synchronization

Extend the current publication boundary so the active Bot Job has one canonical mutation stream.

- A mutation from Command Editor updates Bot Job Details.
- A mutation from Bot Job Details updates Command Editor.
- A Page Scanner Apply that changes the same Bot Job updates both.
- Selection changes from a Bot Job Details Command Editor arrow retarget the detached editor.
- Selection changes inside Command Editor may publish a lightweight highlight event to Bot Job
  Details.
- The editor ignores stale request IDs, binding epochs, selection revisions, and content revisions.
- External changes never overwrite an unsaved draft silently. The page shows a professional
  refresh/conflict message and lets the user reload the authoritative row.
- Switching or closing the active Bot Job retires the Command Editor using the existing workspace
  lifecycle.

One per-Bot-Job coordinator or lock must serialize mutations from Bot Job Details and Command Editor.
The existing `BotJobDetailsWorkspaceRegistry.commitWorkspaceMutation(...)` is the lifecycle boundary
to extend rather than creating a second independent lock.

## Phased Implementation

### Phase 0 - Fix Detached Hydration

- Consume the merged workspace bootstrap atomically.
- Remove the redundant child bootstrap race.
- Add loading/error/retry states.
- Add the missing `CommandEditorPage` integration test.

Exit: opening from any valid instruction shows commands, Web Fields, Blocks, variables, and enabled
actions without a second click or refresh.

### Phase 1 - Freeze Command And Row Classification

- Inventory `CommandRegistry`, runtime constants, persisted actions, codec support, and execution
  support.
- Classify every current row as Web Field, supported command, structural row, or legacy row.
- Add read-only legacy definitions/warnings.
- Add compatibility fixtures for every supported command.

Exit: no persisted instruction disappears, and “all commands” has a testable definition.

### Phase 2 - Build Immutable Workspace Snapshot

- Add snapshot/Block/Instruction/WebField/selection DTOs.
- Add `CommandEditorReadRepository`.
- Return all Blocks, all Instructions, all Web Fields, all commands, variables, draft, capabilities,
  and revisions.
- Remove fake error rows and dependence on shared lists for the snapshot.

Exit: one backend call can render the complete independent page, including an empty Block.

### Phase 3 - Add Independent Block And Instruction Selection

- Change the workspace binding to Bot Job plus mutable selection.
- Add `commandEditor.select`.
- Add searchable Block and Instruction selectors.
- Retarget from Bot Job Details through the same selection service.
- Add deterministic selection recovery.

Exit: the user can move between every Block and Instruction without reopening the page.

### Phase 4 - Create Transactional Mutation Foundation

- Extract reusable command/delete persistence from the generic WebSocket switch.
- Use one transaction and generated keys.
- Add owner-scoped SQL.
- Add `editorRevision` covering Blocks and every editable instruction field.
- Serialize per-Bot-Job mutations.

Exit: injected failure at any mutation step leaves database order and relationships unchanged.

### Phase 5 - Complete Create And Update

- Route typed create/update through the new mutation service.
- Support Before, After, Edit, and empty-Block Append.
- Return the complete snapshot after success.
- Render every command-specific field from Java metadata.

Exit: every registered command passes create/update tests where its capability allows it.

### Phase 6 - Complete Delete

- Add delete impact preview and confirmation token.
- Reuse graph/dependency deletion rules.
- Delete ordinary commands, IF families, ELSEIF branches, and loop groups atomically where allowed.
- Retarget selection after deletion.

Exit: deletion never leaves a dangling parent, variable, Block reference, or order gap.

### Phase 7 - Two-Way Realtime

- Fan out authoritative snapshots to Command Editor and Bot Job Details.
- Subscribe Command Editor to external Bot Job/Page Scanner mutations.
- Correlate acknowledgement before snapshot.
- Add stale/draft conflict behavior and selection highlight events.

Exit: both pages show the same rows and selection without manual Refresh.

### Phase 8 - UX, Focus, And Deployment

- Use the established non-modal detached-page header and confirmation components.
- Keep the singleton open/focus behavior.
- Preserve scroll and search filters across non-destructive refreshes.
- Add accessible labels, keyboard selection, and disabled reasons.
- Build React and mirror the build into `src/main/resources/build`.
- Commit and push frontend and backend only after focused verification.

Exit: the independent CRUD workspace is production-ready and no browser-native alert is used.

## Focused Test Matrix

### Frontend

- Initial workspace bootstrap hydrates all collections without a second request.
- Loading, empty, stale, reconnect, and error states are visible.
- Block selection filters Instructions deterministically.
- Every Web Field remains reachable through its Block or all-Blocks search.
- Full command catalogue renders with enabled/disabled reasons.
- Create, update, and delete each submit exactly once.
- Delete impact uses the React confirmation component.
- Snapshot reconciliation preserves valid selection.
- Deleted selection falls back deterministically.
- Stale responses cannot overwrite a newer target.
- Unsaved draft conflict does not silently discard data.

### Backend

- Snapshot completeness and deterministic ordering.
- Empty Blocks and null relationships.
- No cross-Bot-Job or cross-organization leakage.
- Valid and invalid Block/Instruction selection.
- Every registered command create/update path.
- Before/After/Append placement.
- IF family, ELSEIF, loop, GOTO, and EXCEL GOTO rules.
- Variable ownership/type and Web Field/tag compatibility.
- Delete preview and confirmed deletion impact.
- Owner-scoped update/delete.
- Transaction rollback at every injected failure point.
- Duplicate request ID executes once.
- Stale workspace, selection, graph, and editor revisions are rejected.
- Reconnect rotates/validates epochs correctly.

### Integration

- Command Editor mutation: acknowledgement, editor snapshot, Bot Job Details snapshot.
- Bot Job Details mutation updates Command Editor.
- Page Scanner Apply updates Command Editor.
- Selected row deletion retargets both views.
- Opening another instruction reuses and focuses the same detached editor.
- Closing Command Editor closes only that page.
- Closing/switching the owning Bot Job retires the editor.

Use a copied temporary SQLite database. Never run CRUD verification against the configured production
database.

## Expected File Impact

Backend:

- `CommandEditorWorkspaceService.java`
- `CommandEditorService.java`
- `SimpleWebSocketServer.java`
- `InstructionRealtimePublisher.java`
- `BotJobDetailsWorkspaceRegistry.java`
- `PerformDataBase.java` only through extracted, owner-scoped transaction boundaries
- new snapshot DTOs/read repository/mutation service
- focused workspace, repository, mutation, and realtime tests

Frontend:

- `CommandEditorPage.tsx`
- `CommandEditorPage.module.scss`
- `InstructionCommandPanel.tsx`
- `InstructionCommandPanel.module.scss`
- new typed workspace snapshot/reducer/hook
- `GridItem.tsx` only for shared retarget/highlight integration
- focused React tests plus one detached-page integration test

## Acceptance Criteria

- Command Editor is populated on first open.
- It lists every Block and every Instruction in the active Bot Job.
- It loads all Web Fields and all supported commands.
- Unsupported historical command rows remain visible and read-only.
- The user can select any valid Block and Instruction inside the detached page.
- Create, update, and delete are backend-authoritative, atomic, and idempotent.
- The first command can be appended to an empty Block when its command type is legal there.
- React never constructs canonical operation strings or decides graph legality alone.
- Bot Job Details, Page Scanner, and Command Editor converge in real time after a mutation.
- Stale requests and cross-job IDs are refused.
- Delete shows exact impact and uses the standard React confirmation component.
- One physical Command Editor window is reused and focused.
- Closing the Command Editor does not close the application.
- Focused tests pass before the frontend build is deployed.
