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
- `MEMORY_ONLY` is valid persisted data. Active commands remain runnable; unresolved variable
  relationships produce VOID diagnostics and never become an execution gate.
- GET/SET/E/CK/PDF/CSV follow the P1 diagnostic-only runtime rules; LOOP uses `LOOP_ANCHOR`, not
  variable ownership.
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

Goal: extend accepted single-row semantics after reconnect, diagnostics, and exact persistence are
proven.

Subphases:

- [ ] P11A: ordinary and variable-command cross-Block movement as exact single-row layout changes.
  For every relation invalidated by the final Block, require an explicit disconnect or reconnect
  patch; never move an attached row automatically.
- [ ] P11B: LOOP cross-Block movement with an explicit `parentId` anchor result.
- [ ] P11B-GOTO: preserve GOTO/EXCEL GOTO `parentBlockId` when it remains a valid different
  destination. Moving into that destination requires explicit disconnect or reconnect to another
  Block; never auto-target.
- [ ] P11C: optional individual IF/ELSEIF/ELSE/ENDIF draft movement.
- [ ] P11D: empty-Block preservation/removal policy.
- [ ] P11E: keyboard move parity with pointer drag.

#### P11A-C1 - Variables consumer cross-Block checkpoint (2026-07-29)

This is a separately advertised, rollback-safe subset of P11A. It does not mark general
cross-Block movement complete.

Implemented:

- [x] Add the dedicated `VARIABLES_INDIVIDUAL_CROSS_BLOCK_V1` capability without weakening or
  replacing `VARIABLES_INDIVIDUAL_ROW_V1`.
- [x] Keep the movement planner, exact destination/insertion point, full final layout, variable
  writer-order policy, compatible Web Element candidates, and explicit disconnect/reconnect
  choice in React/TypeScript.
- [x] Allow only `E`, `CK`, `PDF CHECK`, and `CSV CHECK` variable consumers to move between
  non-empty, structurally flat Blocks. `GET`, `SET`, owner transfer, structural commands, empty
  source Blocks, and rows with direct dependants remain refused.
- [x] Never infer a new `parentId` from physical proximity. Before persistence the client must
  choose either explicit `CLEAR` (`parentId`/`parentBlockId` become null) or exact `SET` to an
  earlier compatible Web Element in the destination Block.
- [x] Preserve `variableId`, variable owner, operation, every unrelated relationship, fixed Block
  order, and the relative order of every non-selected instruction. A disconnected result remains
  visible as `Reconnect parent`.
- [x] Keep Java as the authenticated trust/persistence boundary only: exact capability dispatch,
  owner/binding/revision/version checks, complete contiguous layout verification, one-row
  cross-Block reinsert verification, explicit relation-patch verification, compare-and-set,
  atomic persistence, final-state verification, and commit. Java does not choose authoring
  behavior or repair targets.
- [x] Lock the review dialog during reconnect/bootstrap/refresh changes, include the exact Bot Job
  owner in its authority key, and show the planned destination before the client confirms.
- [x] Make every visible Variables execution row a real before/after drop surface. The pointer
  midpoint selects the exact placement and an orange edge/glow shows the live target; the narrow
  gap and explicit cross-Block Block-zone targets remain available.
- [x] Keep the native drag source in the Variables-private controller as well as React render
  state, and carry both the private MIME value and a plain-text fallback so a fast browser drop
  cannot disappear while a state render is pending.

Focused verification:

- [x] 54 React tests across eight Variables/relationship suites cover exact layout authorship,
  explicit disconnect with zero candidates, exact reconnect, `GET`/`SET` refusal, source
  relationship health, direct dependants, flat-Block restrictions, policy-correct writer order,
  drop-zone anchors, disconnected-state rendering, profile transport, normalization, dialog
  preview, and retained same-Block behavior.
- [x] 63 Java tests across six mutation/profile/contract/Variables suites cover the separate
  profile, complete and contiguous authoritative/submitted layouts, healthy stored parent,
  source/destination restrictions, direct dependants, exact `CLEAR`/`SET`, preserved variable
  binding/owner, transaction commit, capability advertisement, and envelope profile forwarding.

Still deferred:

- [ ] Runtime/manual acceptance with production-shaped data, including refresh/reopen/restart
  persistence and Bot Job Details/Variables realtime convergence.
- [ ] Reopen the detached Variables window after the deployed bundle changes, then manually prove
  visible-row same-Block drops and eligible consumer-to-Block-zone drops. A stale already-open
  browser document retains its old JavaScript even when its WebSocket reconnects.
- [ ] `GET`/`SET` cross-Block semantics, variable-owner transfer, empty-Block policy, standalone
  reconnect repair action, structural movement, arbitrary destination gaps hidden from the
  variable lane, keyboard parity, and mixed legacy-writer activation proof.

Acceptance:

- Every new unresolved state has a chip, reconnect path, and preflight rule before activation.
- A cross-Block drag mutates exactly the selected instruction plus the explicit relation patches
  React previewed and the user confirmed.
- No relation target changes because of physical proximity.
- A GOTO/EXCEL GOTO row never commits with `parentBlockId == blockId`.
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

#### P13A - Private same-Block variable-command reorder lane

This is the first safe behavior-changing Variables-page slice. It is intentionally narrower than
general Variables-page drag/drop and does not activate reconnect, disconnect, owner transfer, or
cross-Block authoring.

Scope lock:

- [x] Add one Variables-owned execution-order lane for the selected variable. The lane is a view
  of that variable's owner/commands projected onto the authoritative Bot Job instruction order,
  not a complete Block renderer or a second ordering model.
- [x] Keep its drag controller, source ID, drop intent, pending request, timeout, rollback, and
  binding-epoch checks private to the detached Variables page. It may reuse pure stateless graph
  and contract types, but it must not share live drag state with Bot Job Details, Components, or
  Memory List.
- [x] Allow exactly one eligible variable command to move to one exact before/after gap inside its
  existing Block. GET, SET, E, CK, PDF CHECK, and CSV CHECK remain subject to the P1 command
  matrix. The selected variable's visible owner/command rows are drop anchors only, not implicit
  members of the move. Hidden ordinary rows keep their relative order; structural/navigation rows
  are not rendered as lane anchors in this slice, and crossing one is refused.
- [x] React calculates the complete final owner layout. Every non-selected instruction keeps its
  relative order except for the mechanical renumbering required by insertion of the selected row.
  The submitted `draggedInstructionId` identifies that one row only.
- [x] Preserve `parentId`, `parentBlockId`, `variableId`, variable ownership, action, configured
  `operation`, and all relationship targets byte-for-byte. The request carries no relationship,
  variable-binding, variable-owner, delete, or operation patch.
- [x] Refuse a source with an unresolved/invalid variable link, a cross-Block target, a drop across
  a LOOP/IF/navigation boundary, or a layout that introduces a new parent/producer/consumer order
  issue. This valid-only slice never persists a new broken draft.
- [x] Never expand a parent, child, variable family, positional body, LOOP anchor, conditional
  family, GOTO target, or nearby row into the move. Physical proximity is only the explicit
  before/after insertion point.
- [x] Do not expose rebind, reconnect, disconnect, variable-owner detach/transfer, command delete,
  cross-Block move, or Component mutation from this lane.

Capability and route:

- [x] The Variables snapshot supplies a versioned `mutationCapability` from the authenticated
  bound Bot Job when version-3 inspection succeeds. The envelope carries the exact
  `VARIABLES_INDIVIDUAL_ROW_V1` profile, contract version,
  authoritative owner assertion, workspace/binding epochs, graph version, content revision,
  complete layout rows, raw persisted actions, and relationship IDs. React validates those facts,
  derives LOOP/IF/GOTO/ordinary semantics through `instructionRelationshipPolicy`, and fails
  closed to a visible read-only page when the capability is missing, disabled, contradictory, or
  malformed.
- [ ] Gate production advertisement on a proven reliable shared graph version across every
  enabled legacy/v2 writer. The development route is implemented, but this activation condition
  remains tied to the mixed-writer debt below.
- [x] Use the private request route `variablesWorkspace.graphMutationV3` and correlated response
  `variablesWorkspace.graphMutationV3Response` on the `variablesManager` session. The route reuses
  `InstructionGraphMutationV3` with `mutationKind: "ROW_MOVE"`; it does not invent a second
  persistence contract.
- [x] Java derives the Bot Job owner from the authenticated detached binding and workspace
  registry, compares any client owner assertion, validates the complete layout and expected
  graph revision/version, performs database compare-and-set, persists exactly the submitted
  layout atomically, verifies the result, and commits. Java does not choose drag eligibility,
  expand a group, select a target, repair a relationship, or rewrite an operation. Its
  transaction-scoped `VARIABLES_INDIVIDUAL_ROW_V1` safety profile proves that the submitted
  request is one eligible same-Block reinsertion with no relationship/variable patches or
  structural crossing. It evaluates the same authoritative snapshot used by the write, avoiding
  a separate inspection/write race. The persistence validator may rederive a stored action's
  relation kind only to verify that the React submission did not forge or silently mutate
  persisted structural facts.
- [ ] After commit, send one correlated response and publish authoritative Bot Job Grid/raw
  variable/Variables snapshots with the same committed graph version and content revision.
  Failed, stale, late, or wrong-epoch responses preserve the last valid Variables snapshot.
- [x] Keep this narrow capability independent from `variablesWorkspaceActionsV1`; enabling command
  reorder must not implicitly enable the later repair/delete actions.

Required focused tests:

- [x] `variablesInstructionMove.test.ts`: exact one-row same-Block before/after layouts; stable
  hidden-row order; no patches; operation/relationship preservation; no-op, invalid source,
  structural-boundary, unsafe-order, and cross-Block refusal.
- [x] `useVariablesInstructionDrag.test.tsx` and `VariableExecutionLane.test.tsx`: page-private drag
  state, exact gap intent, owner rows not draggable, exact transfer validation, end-of-Block gaps,
  and authority-change cleanup.
- [x] `useVariablesGraphMutation.test.tsx`, `variablesWorkspace.contract.test.ts`, and
  `VariablesPage.test.tsx`: capability/profile fail-closed behavior, one correlated request,
  non-advancing-success refusal, authority-change cancellation, page request timeout behavior,
  and last-snapshot preservation after refresh failure.
- [x] `VariablesWorkspaceServiceTest`, `VariablesWorkspaceAuthorizationTest`,
  `InstructionGraphMutationContractValidatorTest`, `VariablesInstructionMutationProfileTest`,
  `ScannerBotJobTasksPublisherTest`, and `BotJobGraphMutationTransactionTest`:
  forged owner/binding/epoch refusal, complete-layout validation, stale revision/version refusal,
  concurrent same-base CAS with exactly one commit, injected rollback, no group inference, and
  exact committed layout.
- [ ] WebSocket routing/publication coverage proves the response operation names, acknowledgement
  before publication, and Grid/Variables convergence on one committed version.
- [ ] Mixed-writer coverage proves an intervening v2/legacy writer advances the same graph version
  and makes the stale Variables request fail before this capability is advertised.

Implementation checkpoint (2026-07-29):

- the Variables page owns a private `VariableExecutionLane`, native drag hook, pure movement
  planner, and correlated mutation transport; no live drag state is shared with GridItem,
  GridItemComp, or Memory List;
- Java exposes the complete persisted layout plus raw action/relationship facts. React derives
  action semantics through the shared TypeScript policy, calculates one complete exact layout,
  and submits no relationship/variable/operation patches for this valid-only reorder;
- Java remains the trust and persistence boundary: authenticated owner/binding checks, expected
  revision/version validation, compare-and-set, atomic database writes, final-state verification,
  and commit. A committed response carries `committed: true`; a post-commit transport rotation is
  a committed resync response rather than a false refusal. The WebSocket route attempts the
  correlated acknowledgement before invoking its publication hook and invokes that hook even if
  the requester closed, but end-to-end Grid/Variables convergence is not yet claimed;
- focused verification is green: 35 React tests across six Variables suites and 54 Java tests
  across the mutation transaction/profile, contract validator, publication, Variables
  authorization, and Variables service suites; the optimized React build also compiles and is
  mirrored byte-for-byte into the backend resource build;
- this checkpoint does not claim cross-Block movement, reconnect/detach, owner transfer, delete,
  end-to-end publication convergence, or mixed legacy-writer activation.
- the current application singleton advertises the capability whenever version-3 inspection
  succeeds so this slice can undergo the requested runtime testing. This is explicitly
  pre-production/pre-acceptance behavior, not proof that the production mixed-writer activation
  gate below has passed; production release requires gating or completing that gate.

Remaining activation debt:

- [ ] Shared-version debt: every enabled v2/legacy graph writer listed in Section 6 must advance
  the same `instruction_graph_state` owner version in its own transaction. Until the inventory,
  mixed-writer tests, and production-dialect checks pass, `mutationCapability` must be absent or
  disabled even if the version-3 transaction itself is available.
- [ ] Operation-patch debt: version 3 does not yet carry explicit expected-old/new instruction
  `operation` patches. This reorder is safe because it preserves `operation` exactly. Rebind,
  variable rename/type propagation, owner transfer, or any action that requires an operation
  rewrite remains disabled until React can submit an explicit operation patch (or an equally exact
  reviewed contract) and Java can validate/persist it in the same CAS transaction without hidden
  inference.
- [ ] P4 diagnostics/VOID semantics and P5 authenticated CAS transport must be accepted before
  P13A activation. P7/P8/P10/P11 and the broader P13 actions remain separate capabilities; P13A
  does not satisfy or bypass them.

Future P13A acceptance target (not yet claimed):

- One drag commits exactly one same-Block variable command and no relationship or operation change.
- Java can neither add a row nor infer a group/repair that React did not submit.
- Grid and Variables snapshots converge on the same committed version; a refusal leaves both last
  valid views intact.

P13A rollback:

- Withdraw/disable `mutationCapability` for the Variables session and retain the read-only page.
  Accepted layouts require no data conversion because they are ordinary version-3 same-Block
  layouts with unchanged relationships and operations.

#### P13B - Repair and lifecycle actions

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

- Disable `variablesWorkspaceActionsV1`; P13A command reorder may remain independently enabled
  after its own acceptance, or withdraw `mutationCapability` to return the whole page to read-only.

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
- [ ] Retire Java semantic move checks only after TypeScript parity, diagnostic coverage, and exact
  persistence acceptance.
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
- Test Run/Launch/prelaunch diagnostics and exactly-once dispatch tests;
- typed VOID versus `VALUE("")`, early VOID consumer/later writer, and non-blocking runtime tests;
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
- execution structural-start refusal;
- variable VOID/diagnostic bypass.

If commit succeeds but refresh fails, report that persistence succeeded, keep the last valid grid,
and offer an authoritative refresh. Do not retry the mutation automatically.

