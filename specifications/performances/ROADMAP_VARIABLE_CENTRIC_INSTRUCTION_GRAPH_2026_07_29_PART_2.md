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
14. Once any broken draft is persisted, authoritative diagnostics and reconnect remain available;
    variable health never becomes a backend execution gate.
15. Activation order is fixed: harden P4 diagnostics/VOID semantics first; land P5 version-3
    transport/CAS second; activate P6 valid same-Block single-row movement third; activate P7
    modal reconnect and explicit broken-draft choices fourth; only then activate P11 cross-Block
    and later conditional freedom. Later code may be prepared behind disabled capabilities, but it
    cannot be activated out of this order.

Recommended independent capability flags:

- `relationshipChipsV1`
- `executionRelationshipDiagnosticsV1`
- `runtimeVariableVoidV1`
- `executionStructuralGateV1` (non-variable structural start envelope only)
- `rowMoveContractV3`
- `freeSameBlockMoveV1`
- `relationshipReconnectV1`
- `durableMemoryVariablesV1`
- `deleteContractV3`
- `freeCrossBlockMoveV1`
- `componentRowMoveContractV3`
- `automaticVariableCreationV1`
- Variables snapshot `mutationCapability` for the narrow
  `variablesWorkspace.graphMutationV3` command-reorder route
- `variablesWorkspaceActionsV1`
- `conditionalDraftMoveV1`

## 9. Phased implementation roadmap

| Phase | Deliverable | Data risk | Activation |
|---|---|---:|---|
| P0 | Baseline, fixtures, backups, ownership, and contract freeze | None | Documentation/tests only |
| P1 | Engine and entry-point semantic audit | None | No behavior change |
| P2 | Pure React relationship classifier | None | No UI change |
| P3 | Relationship details and read-only chips | None | Frontend flag |
| P4 | Execution diagnostics, typed VOID, and structural start safety | Low | Warning-only for variable health |
| P5 | Additive version-3 mutation contract | Low | Capability off |
| P6 | Bot Job same-Block single-row drag that preserves relationships | Medium | Bot Job valid-only flag |
| P7 | Chip-to-modal reconnect plus broken-draft activation | Medium | Reconnect + diagnostics |
| P8 | Durable ownerless memory variables | High | Data flag + backup |
| P9 | Owner uniqueness, then automatic variable creation for new Web Elements | High | Migration + creation flag |
| P10 | Delete selected/direct/full explicit modes | High | Delete v3 flag |
| P11 | Cross-Block and later conditional freedom | High | Separate flags |
| P12 | Independent Components parity | High | Component-only flag |
| P13 | Variables same-Block command reorder lane, then interactive repair actions | Medium | Private mutation capability, then Variables actions flag |
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
- [x] Record that `ScannerPreLaunchExecutionGate` controls execution concurrency, not variable
  health; do not reuse it as a variable relationship gate.
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
- Warning-only variable diagnostics cover every start path, not only one React button.

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

- [x] Extract `InstructionRelationshipDetails`.
- [x] Replace invalid `renderOperations -> null` paths.
- [x] Render all states from Section 5.
- [x] Keep reconnect chips read-only in this phase.
- [x] Preserve the last valid grid during refresh/error.
- [x] Gate shared `GridItem` rendering by server-advertised workspace capability and add a
  regression proving Components remain unchanged before P12.

Acceptance:

- No relationship error blanks a row or grid.
- Existing valid colored relationship details remain visually unchanged.
- No persistence request is introduced.

Rollback:

- Turn off `relationshipChipsV1` or revert only the extracted renderer.

### P4 - Execution diagnostics, typed VOID, and structural start safety

Goal: keep flexible authoring observable and make missing runtime variable values safe without
turning variable health into an execution permission gate.

Tasks:

- [x] Add pure TypeScript execution diagnostic output with exact row IDs and messages.
- [x] Display a bounded modal/list with row focus actions. Bot Job Details can clear Find, expand,
  scroll, focus, and transiently highlight the affected row; detached Page Scanner displays the
  same authoritative bounded report without inventing unavailable local row data.
- [x] Start in shadow/warn mode and record which existing jobs have issues. Accepted Test
  Run/Launch requests still dispatch exactly once; warnings are displayed after the correlated
  response and never offer a second Continue/Run action.
- [x] Permanently prohibit variable diagnostics from blocking any mapped Test Run/Launch entry
  point.
- [x] Split variable diagnostics from true structural start failures in the Java and TypeScript
  result models; remove `BLOCKED`/`WOULD_BLOCK` wording from variable-health presentation without
  breaking correlated transport compatibility.
- [x] Add typed runtime `VOID | VALUE` semantics. Never use the literal `"VOID"` or a shared
  `"Not Variable defined"` map key as runtime state.
- [x] Make missing/failed producers leave `VOID`, make `VALUE("")` a valid empty Web result, and
  make VOID consumers bypass only the dependent operation with a bounded warning.
- [x] Verify at the run-scoped store boundary that a later successful producer replaces VOID and
  later consumers read the recovered value. Full browser-path integration coverage remains open.
- [x] Preserve user Active flags; authoritative preflight reads them without rewriting them.
- [ ] Bind preflight to exact authoritative owner, database graph version, content revision, and
  the actual requested run scope. Workspace epoch is additionally required for detached-workspace
  callers, not invented for Main Dashboard/legacy callers.
- [x] Add the pure Java authoritative-snapshot readiness evaluator and wire warning-only
  observations into Bot Job Details Test Run/Launch, detached Page Scanner Test Run through the
  same host, and classic scanner/prelaunch. React remains the user-facing diagnostic surface.
  Main Dashboard/mobile/external Engine entry-point coverage and the immediate version recheck
  remain open.
- [ ] Atomically recheck graph version immediately before Engine start so preflight cannot race a
  mutation.
- [x] Validate only the reachable run plan: ONE/single-Block Test Run validates that selected
  active scope; full/from-selected runs validate their actual active reachable scope.
- [x] Cover current runtime facts: GET and SET need compatible Web Elements; E currently needs its
  persisted target plus variable/producer; CK/PDF/CSV need their current target/variable contract;
  LOOP needs its anchor; IF needs valid structure; GOTO needs its target Block.
- [ ] Make active/inactive policy explicit and identical for Bot Job Details Test Run, full Launch,
  Main Dashboard Launch, and scanner/prelaunch.
- [ ] Disable silent runtime `fixExcelGoto`; move legacy repair to an explicit migration/action.
- [x] Make full Launch return immediately when definition load fails instead of reporting an error
  and continuing.

Implementation checkpoint (2026-07-30):

- Java and TypeScript classify variable-command element targets, variable bindings, variable
  order, and duplicate-variable facts as `VARIABLE_DIAGNOSTIC`; warning-only results are
  execution-ready and can never become a start gate through `ready()`.
- Snapshot/run-scope identity, LOOP/conditional structure, and Block-navigation failures remain
  separately classified as `STRUCTURAL_START_FAILURE`.
- The WebSocket report preserves legacy `READY | WOULD_BLOCK | UNAVAILABLE` status values for old
  detached clients and adds canonical `outcome`, disposition counts, and per-issue
  severity/disposition fields. React consumes the canonical outcome and normalizes legacy
  observations without displaying `WOULD_BLOCK` for variable health.
- Focused verification is green: 20 Java tests across the evaluator/monitor/WebSocket transport
  and 52 React tests across transport normalization, warning presentation, controller
  exactly-once behavior, and the pure TypeScript relationship evaluator.

Acceptance:

- Every variable diagnostic permits exactly one requested Test Run/Launch dispatch.
- No variable diagnostic refuses, pauses, cancels, or terminates an execution request.
- An ownerless/missing-producer variable is visible as `VOID(NO_PRODUCER_YET)`.
- `VALUE("")` remains distinct from VOID and can be validated as an expected empty value.
- A VOID consumer is diagnostic/skipped, does not report PASS, and does not prevent later steps or
  later writers from executing.
- Inactive rows remain visible and do not block execution.

Rollback:

- Diagnostic UI can be disabled without stored-data conversion; warning-only execution behavior
  remains.
- Typed VOID can roll back independently before its capability is enabled because it is run-scoped
  and is never persisted as user data.
- Do not enable broken-draft movement until reconnect and authoritative diagnostics are accepted.

### P5 - Additive version-3 mutation contract

Goal: land transport and transaction support without changing user behavior.

Tasks:

- [x] Define typed React DTOs for owner, layout, relationship patches, variable-owner patches, and
  expected old values.
- [x] Add a dedicated `InstructionGraphMutationV3`; do not overload `SplitDTO` with ambiguous
  nullable fields.
- [x] Add version-3 structural contract validator in Java.
- [x] Add a database-owned graph-state row with a compound workspace/owner primary key,
  atomic first-row creation, owner-deletion cleanup/tombstone policy, and compare-and-set version.
  Register the production migration in
  `com.allinweb.ch.db.MigrationRunner`.
- [ ] Make fresh SQLite/Access initialization and migration ordering self-healing; capability
  activation fails closed if graph-state support is absent.
- [ ] Make every remaining v2 and v3 graph writer bump the same version in its own transaction.
- [x] Add one Bot-Job-only transaction foundation that applies a complete layout and explicit
  relationship/variable patches atomically.
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

Implementation checkpoint (2026-07-29):

- the React contract, private inert transport hook, Java structural validator, graph-state
  migration/repository, and additive Bot Job transaction exist behind no advertised capability;
- explicit and implicit GOTO self-targets are rejected, including moving a GOTO into its existing
  destination while attempting to keep `parentBlockId`;
- focused tests cover atomic rollback, expected-old state, graph-version staleness, variable
  binding/ownership, empty-Block preservation, and the GOTO invariant;
- live routing remains prohibited until every v2/legacy Bot Job graph writer increments the same
  database graph version, otherwise a non-participating writer can still be overwritten;
- the transaction must retain the rollback-failure connection invalidation safeguard, and live
  routing must source owner/epoch from server session state rather than request data;
- capability advertisement, correlated WebSocket routing, post-commit authoritative broadcast,
  idempotency, mixed-writer tests, and production-dialect verification remain open.

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

- [x] Add the pure, currently unwired `instructionFreeMove.ts` planner.
- [ ] Make a gap drop move exactly the selected row.
- [ ] Preserve valid relationships unchanged.
- [ ] If a proposed move would create a new broken/detached relationship, preview the issue and
  refuse activation until P7 provides repair. Do not persist unresolved drafts in P6.
- [ ] Keep IF-family boundaries constrained in this first release.
- [ ] Keep Block movement and cross-Block row movement unchanged; exact cross-Block single-row
  behavior belongs to P11.
- [ ] Use private Bot Job drag state and one correlated request.
- [ ] Roll back the optimistic layout on refusal/timeout.

Acceptance:

- Wait/Pause/ordinary rows move freely.
- GET/SET/E/CK/CHECK rows move individually when all audited required relations remain valid.
- Moving inside a LOOP body does not drag the positional body.
- LOOP itself moves only when its anchor outcome is explicit.
- GOTO/EXCEL GOTO destination remains the existing `parentBlockId`, which is valid because P6 is
  same-Block only.
- The grid never disappears on refusal.

Rollback:

- Disable `freeSameBlockMoveV1`; valid P6 rows remain compatible with the current group planner.

### P7 - Explicit reconnect actions

Goal: repair one typed relationship without implicit choices.

Deliver in separate commits:

#### P7A - Element and LOOP targets

- [x] Add the isolated, currently unwired `ReconnectRelationshipDialog`.
- [ ] Open the dialog by clicking the relationship/reconnect chip; keep animated relationship
  arrows deferred until after modal reconnect and P11 acceptance.
- [x] Reuse `SearchBox`; the caller supplies already-filtered exact-owner compatible candidates.
- [ ] Support `RECONNECT_PARENT` and `RECONNECT_LOOP`.
- [ ] Treat LOOP/REFRESH_LOOP `parentId` as the anchor identity.
- [x] Preview the current and selected target. Impacted-row integration remains part of live
  reconnect wiring.

#### P7B - Block and conditional targets

- [ ] Support `RECONNECT_BLOCK`.
- [ ] Read/write the GOTO/EXCEL GOTO destination through `parentBlockId`.
- [ ] Exclude the row's containing `blockId` from compatible GOTO targets and refuse an attempted
  same-Block target.
- [ ] Never select the source, destination, drop, nearest, or preceding Block automatically.
- [ ] Support `REPAIR_CONDITIONAL` without moving positional body rows.

#### P7C - Variable binding and owner transfer

- [ ] Support selecting an existing compatible variable.
- [ ] Build the preview/contract for leaving a variable ownerless as memory, but keep `DETACH`
  disabled until P8 has cross-dialect persistence and backup/restore support.
- [ ] Show every impacted command before transferring variable ownership.
- [ ] React submits the exact parent compatibility projection with the owner patch.
- [ ] Never transfer ownership based only on physical proximity.

#### P7D - Broken-draft activation

- [ ] Keep authoritative variable diagnostics warning-only on every execution entry point.
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
  authoritative diagnostics for already-saved drafts.
- Reconnect UI can return to read-only only after an audit proves there are no unresolved rows that
  need it.

