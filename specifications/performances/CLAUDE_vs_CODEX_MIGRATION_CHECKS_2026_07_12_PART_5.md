### Regression found and removed

The earlier `StrictModeDroppable` wrapper gated the list behind a `requestAnimationFrame`. rAF is
**paused in hidden/background tabs**, so that wrapper would render the Memory List **empty** if its
detached window ever opened in the background — a production risk introduced by the "fix." Confirmed
live: with `document.hidden === true`, the rAF-gated list showed 0 rows; a plain `Droppable` showed
all 10 rows while still hidden.

- [x] Reverted `MemoryList.tsx` and `MemoryDragDemo.tsx` to a plain `<Droppable>` (no rAF gate).
- [x] Render the dev demo routes **outside** `React.StrictMode` in `index.tsx`, which matches
      production behavior (no double-invoke) and lets a plain `Droppable` work in `npm start`.

### Deliverables this pass

- [x] Frontend shortcut to test the **real** Memory List with synthetic data, no backend:
      `http://localhost:3000/?memoryListDemo=1`. Added a `demoMode` prop to `MemoryList` that seeds a
      10-row snapshot and keeps every command local. Verified live: all 10 rows + droppable render
      even in a hidden tab.
- [x] Runtime console monitor on the drag path (`[MemoryList][drag]` in `handleDragEnd`): logs
      drag-end, abort reasons, no-op reorders, and the `REORDER` send. Open DevTools in the jar,
      drag a row, and it prints exactly where the path stops.
- [x] Java drag & drop test without WebSocket/DB/JavaFX: extracted the pure reorder core into
      `com.allinweb.ch.socket.MemoryListReorder` and added `MemoryListReorderTest`
      (accepts complete permutations, rejects wrong count / null / unknown / duplicate / null-key;
      exhaustive adjacent-swap and full-reversal cases). Run:
      `mvn -Dtest=MemoryListReorderTest test` (user-owned; assistant does not run Maven).
- [x] Built React (`main.1a51dabd.js`, 45 files) and clean-copied into `resources/build`.

### Still user-owned

- [ ] Rebuild the jar (`mvn clean package`) so the running app finally contains the drag code +
      new bundle. This is the actual unblock for "inside Java."
- [ ] Run `mvn -Dtest=MemoryListReorderTest test` to see the backend drag contract go green.

## Claude — Memory List: replaced react-beautiful-dnd with native HTML5 drag (2026-07-24, latest)

rbd proved too fragile: it depends on `requestAnimationFrame`, which the browser **pauses for
hidden/occluded tabs**, so its reorder silently died there and was nearly impossible to drive or
verify. Replaced it with plain native drag in `MemoryList.tsx`.

- [x] `MemoryList.tsx` now uses native draggable rows (`draggable` + `onDragStart`/`onDragOver`/
      `onDrop`/`onDragEnd`) and a pure `reorderByIndex(from,to)` core. rbd import removed. Same
      optimistic snapshot update + `REORDER` command to the backend — only the drag mechanism changed.
- [x] Step-by-step `[MemoryList][drag]` logs: GRABBED / MOVE / DROP / REORDERED / send, with
      before/after key arrays and indices.
- [x] `window.__mlReorder(from,to)` hook triggers the whole pipeline without a physical drag (for
      occluded tabs and automated tests).
- [x] Verified live in a HIDDEN tab (which rbd could not survive): dispatched real
      `dragstart→dragover→drop` reorders correctly and logs every step; `__mlReorder` reorders
      correctly. Backend `MemoryListReorderTest` passed 10/10 in the user's `mvn` build.
- [x] Backend unchanged (`MemoryListReorder` + `REORDER` command still correct). React commit
      `033afec`; deployed bundle `main.98eaaef4.js`.
- [ ] User: rebuild the jar to pick up the native-drag bundle; drag then works with a real mouse
      in the foreground Memory List window.

Note: `MemoryDragDemo.tsx` (the `?memoryDragDemo=1` scratch bench) still uses rbd; the real
`MemoryList` and `?memoryListDemo=1` are native and robust.

## CODEX — Independent Command Editor CRUD investigation (2026-07-24)

Task thread: the detached Command Editor opens empty and must become an independent, real-time CRUD
workspace with Block/Instruction selection, every Web Field, and every command.

Detailed roadmap:
`ROADMAP_COMMAND_EDITOR_INDEPENDENT_CRUD_2026_07_24.md`

### Confirmed immediate defect (fixed in the checkpoint below)

- [x] Java already merges variables, Web Fields, Blocks, command definitions, the selected draft,
      graph revision, and capabilities into `commandEditor.workspaceBootstrapResponse`.
- [x] `CommandEditorPage.tsx` consumes only the target from that response and discards the merged
      editor data.
- [x] The child then attempts a redundant `commandEditor.bootstrap`.
- [x] The child passive effect can run before the parent effect assigns `targetRef`; the request is
      silently skipped or carries a stale binding.
- [x] `commands`, `webFields`, and `graphRevision` therefore remain empty and the command actions
      stay disabled.
- [x] P0 implementation: consume one atomic workspace snapshot, synchronize the target before child
      use, remove the duplicate bootstrap dependency, and add loading/error/retry states.

### Confirmed independent-CRUD gaps

- [x] The detached workspace is bound to one fixed instruction, not one Bot Job with mutable
      Block/Instruction selection.
- [x] Bootstrap has no complete `instructions` collection or selected-Block state.
- [x] Create-before, create-after, and edit already exist through `commandEditor.apply`.
- [x] Detached command deletion and first-command creation in an empty Block do not exist.
- [x] Command Editor mutations update Bot Job Details, but Bot Job Details/Page Scanner mutations do
      not currently push an authoritative snapshot back into Command Editor.
- [x] Existing safe delete/graph logic is embedded in the generic WebSocket mutation path and must
      be extracted rather than exposing raw `UPDATE_BLOCKS` to the detached page.
- [x] Current reads use shared mutable `PerformLists`; complete CRUD needs immutable owner-scoped
      read DTOs.
- [x] Existing mutation paths require a transaction/generated-key/owner-scope audit before they can
      be called complete CRUD.
- [x] At investigation time no detached Command Editor hydration/lifecycle/realtime integration
      test existed. The first-open and selection-correlation React coverage was added in the
      implementation checkpoint below; lifecycle and two-way realtime integration remain open.

### Read-only data evidence

The production SQLite database was queried read-only on 2026-07-24. Bot Job 5 contained 16 Blocks,
150 Instruction rows, 139 native Web Field rows, and 11 command rows at inspection time. Across all
jobs, 62 distinct raw action values existed. The data is present; the empty page is not caused by an
empty database.

### Agreed implementation order

- [x] Phase 0: fix first-open hydration and add a page-level regression test.
- [ ] Phase 1: freeze the supported-command and historical-row classification.
- [ ] Phase 2: return one immutable complete workspace snapshot.
- [ ] Phase 3: add backend-authoritative Block/Instruction selection. The non-empty
      Block/Instruction slice is implemented; empty-Block selection and deletion recovery remain.
- [ ] Phase 4: extract one owner-scoped transactional mutation foundation.
- [ ] Phase 5: complete create/update, including empty-Block append.
- [ ] Phase 6: add delete preview, confirmation, atomic delete, and selection recovery.
- [ ] Phase 7: add two-way realtime snapshots across Command Editor, Bot Job Details, and Page
      Scanner.
- [ ] Phase 8: finish UX, focused tests, React build, resource deployment, commit, and push.

### Implementation checkpoint - 2026-07-24

- [x] React hydrates target, Blocks, Instructions, Web Fields, command catalogue, variables, draft,
      capabilities, and revisions from one complete workspace response.
- [x] Detached page mode no longer sends the redundant child bootstrap.
- [x] Added explicit loading, empty, error, and Retry states.
- [x] Backend returns owner-filtered detached copies of all Blocks/Instructions, enriches
      Instructions with production Block metadata, and sorts deterministically.
- [x] Added `commandEditor.select` with registered-transport identity, active-workspace/owner
      validation, Block membership validation, request correlation, `bindingEpoch` rotation, and
      `selectionRevision`.
- [x] Added Block/Instruction selectors and stale request/binding/snapshot rejection in React.
- [x] Failed selections and failed retarget snapshots preserve the previous backend binding.
- [x] Focused React result: 2 suites and 27 tests passed.
- [x] React production build succeeded and was mirrored to backend resources as
      `main.362b81d6.js` and `main.c23c7909.css`.
- [x] Added focused Java source coverage for ordering, Block enrichment, and owner validation.
      Maven/Java execution was intentionally not run under the repository standing rule.
- [ ] Phase-2 immutable direct-read repository remains open. This slice reduces shared-list risk
      with immediate owner-filtered detached copies but does not claim a transactional immutable
      multi-table snapshot.
- [ ] Empty-Block append, typed delete preview/delete, selection recovery, and two-way external
      mutation synchronization remain open.

### Decisions

- D-017: A command remains an Instruction row; do not introduce a parallel command table.
- D-018: One atomic Command Editor workspace snapshot is the hydration and refresh source of truth.
- D-019: Bind the detached editor to the active Bot Job/workspace epoch; keep Block and Instruction
  selection mutable and backend-owned.
- D-020: Display every supported command and every historical command row. Unsupported historical
  rows remain visible and read-only; incompatible commands are disabled with a Java reason.
- D-021: Every successful mutation publishes acknowledgement first, then authoritative snapshots.
- D-022: Do not authorize generic `UPDATE_BLOCKS` from the detached editor. Extract and reuse typed
  mutation services and graph validators.
- D-023: Complete CRUD requires owner-scoped SQL, generated keys, one transaction, idempotent
  request IDs, and stale workspace/selection/content revision rejection.

## CLAUDE ⇆ CODEX — Bot Job Details decomposition Phase 1 review (2026-07-24)

Roadmap:
`BOT_JOB_DETAILS_COMPONENT_DECOMPOSITION_2026_07_24.md`

### Review of Claude's first leaves

- [x] Reviewed `FindBar` commits `d0b82a2` and `0780216`.
- [x] Reviewed `BlockCollapseToggle` commit `35184e4`.
- [x] Confirmed both leaves preserve state and business handlers in `GridItem`.
- [x] Corrected FindBar's effective compiled CSS parity (`250px` width and `10px` right padding).
- [x] Moved the existing Memory-count reopen action into `FindBar`, matching the roadmap boundary.
- [x] Restored the literal `is-collapsed` class for DOM/external-automation parity.
- [x] Recorded one process concern: the initial FindBar extraction normalized many unrelated line
      endings. It did not produce an identified runtime defect, but future leaf commits must avoid
      EOL-only churn to reduce merge-conflict and review risk.

### Next two tasks completed by CODEX

- [x] Added presentational `BlockStatusToggle.tsx` + `.module.scss` + focused tests.
- [x] Added presentational `ExecutionStateOverlay.tsx` + `.module.scss` + focused tests.
- [x] Preserved block-status persistence through the original `handleBlockStatus` callback.
- [x] Preserved execution green/red/yellow animation and row stacking while moving the overlay
      styles out of `Griditem.module.scss`.
- [x] Frontend commit: `eb7b4db`.

### Focused verification and deployment

- [x] Focused Jest: 4 suites, 15 tests passed. No complete test suite was run.
- [x] React production build succeeded with existing lint warnings.
- [x] Deployed `main.d40f66d4.js` and `main.f87986ad.css` to
      `src/main/resources/build`.
- [x] Verified source/deployment parity: 45 files on each side and no relative-path/SHA-256 delta.
- [x] Maven/Java compilation was not run.

Next unclaimed leaf: `EmptyBlocksPlaceholder`.

## CODEX - GridItemComp production parity and component-safe Memory flow (2026-07-27)

### Production diagnosis

- [x] The former `GridItemComp.tsx` was an independent legacy copy of `GridItem.tsx`. Its 2,400+
      lines had drifted from the canonical Bot Job grid, so later button, command, drag/drop,
      Memory List, validation, and realtime fixes were present in only one copy.
- [x] Component row and block requests did not have one explicit routing policy. A forged or stale
      target session could therefore select the wrong table family.
- [x] The component blue-arrow path bypassed the global Memory List and used the legacy direct
      injection pipeline, whose multiple connections/commits could leave a partially copied graph.
- [x] Component mutations could be followed by a stale process-cache snapshot, visually undoing a
      successful database write.
- [x] Instruction-array-only snapshots dropped empty component Blocks, so empty Blocks disappeared
      after refresh and could not be deleted, rolled back, or reordered reliably.
- [x] Component block reorder previously renumbered cached rows instead of persisting the submitted
      permutation. Block status also used two independent commits for the Block and its children.

### Frontend correction

- [x] `GridItemComp` is now a thin wrapper around the canonical `GridItem` with
      `workspaceMode="COMPONENT"`. All row/block buttons, command actions, status controls,
      move arrows, drag/drop handlers, find behavior, delete/rollback behavior, and Memory buttons
      are therefore the same implementation rather than a second copy.
- [x] Added one typed workspace policy:
      `componentTasks` + `componentsUpdate` + `COMPONENT_ROW_MOVE`.
- [x] Preserved the Components block-header design in `Griditem.module.scss`, including the dark
      blue `#0b5394` background, 20px height, white text, radius, padding, and shadow.
- [x] The row `+` stages one component instruction in the global Memory List.
- [x] The block `+` stages every eligible instruction in the block.
- [x] The blue arrow stages one typed whole-component Block in Memory and never sends
      `COMPONENT_INJECT`.
- [x] Component Memory items carry stable keys, authoritative source IDs, and a graph revision.
      Duplicate clicks are de-duplicated; stale source revisions are refused by the backend.
- [x] Full component Block catalogues are maintained independently from instruction rows, retaining
      empty Blocks across initial bootstrap and authoritative updates.
- [x] Empty Blocks are real row-drop targets. Moving a row into an empty component Block uses the
      same validated `COMPONENT_ROW_MOVE` transaction and does not delete unrelated pre-existing
      empty Blocks.
- [x] The first Components connection now requests an explicit bootstrap after its physical socket
      is registered, eliminating the former first-open race where the Java snapshot was published
      before the browser could receive it.
- [x] Bootstrap success is acknowledged before grid publication, but publication completion is now
      observed. A presentation-thread refresh failure emits a correlated
      `instructionEditor.resyncRequired` to the same physical page instead of leaving an empty
      Components grid with a false green status.
- [x] A failed authoritative refresh after a committed mutation now raises
      `instructionEditor.resyncRequired`; the grid disables further mutation until refreshed.
- [x] Refused component mutations also reload the authoritative snapshot, restoring any
      optimistically changed empty-Block catalogue, row order, status, or name.
- [x] The detached Components header now reports controller bootstrap state and exposes an
      explicit Refresh action. The Command Editor accepts and hydrates both typed
      `botJobTasks` and `componentTasks` workspaces.

### Backend correction

- [x] Direct `COMPONENT_INJECT` is refused with instructions to use Memory List.
- [x] `ComponentMemoryApplyService` performs mixed Memory apply on one connection and one
      transaction. It validates Bot Job ownership, component ownership/revision, target Block,
      command parents/GOTO Blocks, conditionals, variables, references, generated IDs, concurrent
      layout changes, and idempotent request IDs before commit.
- [x] Component instruction moves validate every destination Block against the active organization
      before updating any row.
- [x] Component Block order uses the complete submitted owner-scoped permutation and one
      transaction; unknown, duplicate, missing, or non-contiguous entries are rejected.
- [x] Block active status and all owned child instruction statuses are persisted in one transaction,
      including empty Blocks.
- [x] Component create/rollback/split, Command Editor, and Variable Editor paths now use
      owner-scoped validation and authoritative component snapshots.
- [x] The physical WebSocket session is authoritative. Component transport cannot relabel itself as
      Bot Job Details; the only cross-surface operations are exact test/hover destination mappings.
- [x] Bot Job grid writes also require the exact currently registered `botJobTasks` physical
      transport and the active registry binding. Submitted Bot Job/organization identity is
      validated and canonicalized before any table is read.
- [x] Authorization failures for graph preview, Memory capabilities, and Variable Editor operations
      are returned directly to the offending physical connection; a forged logical session cannot
      inject a correlated refusal into another page.
- [x] Component Test Click/Input resolves the source instruction freshly for each request so an ID
      collision with a Bot Job instruction cannot execute the wrong row.
- [x] Partial row moves resolve an omitted parent from its stored authoritative Block. Rollback
      normalizes the sole surviving destination Block to order 1.
- [x] Destructive Block rollback validates the submitted instruction graph revision and the
      complete pre-change Block catalogue inside the same database transaction. A concurrent row
      move or newly-created/reordered empty Block therefore refuses the stale rollback instead of
      being overwritten or deleted.
- [x] Block catalog loading now includes `export_file`, so empty component Blocks retain their
      Excel export metadata through bootstrap, refresh, and mutation recovery.
- [x] Early licence/routing failures and capability/editor authorization failures are returned to
      the offending physical WebSocket transport. Secondary cache reload failures are preserved
      without replacing the original mutation result.

### Focused verification

- [x] React focused regression result: 8 suites, 32 tests passed.
- [x] Covered component row/block Memory staging, blue-arrow Memory-only behavior, stable
      de-duplication, response correlation, component row-move routing, empty-Block persistence,
      empty-Block drop targets, complete rollback catalogues, block reorder payloads, component
      Command Editor hydration, and Components header refresh/status behavior.
- [x] Added focused Java source tests for Memory apply transaction/rollback/idempotency,
      component block create/rollback/status/order, foreign move destinations, WebSocket route
      authorization/correlation, component bootstrap with empty Blocks, snapshot policy, stale
      rollback revisions, concurrent empty-Block catalogue changes, and correlated bootstrap
      publication failure recovery.
- [x] Java/Maven tests were intentionally not run for this checkpoint.
- [x] Follow-up deployment on 2026-07-27: `npm run build` completed successfully with warnings,
      producing `main.2e8a4913.js` and `main.3000f6dd.css`.
- [x] Mirrored the React `build` directory into `src/main/resources/build`: 45 source files,
      45 deployed files, and zero relative-path/SHA-256 differences.

### Decisions

- D-024: Maintain one canonical instruction-grid implementation; select table/session behavior
  through a typed workspace policy.
- D-025: Components are reusable source data. Applying a component to a Bot Job must always pass
  through the global Memory List and its transactional backend service.
- D-026: A committed component mutation is followed only by an authoritative database reload.
  Never publish the legacy nested component cache as a fallback.
- D-027: Empty Blocks are first-class workspace entities and must be transported separately from
  instruction rows.
- D-028: Logical session IDs inside JSON are routing metadata only. Physical registered transport
  identity plus the active workspace registry are the authorization boundary for grid writes.
- D-029: A refused optimistic component mutation still requires an authoritative database snapshot;
  generic process-cache snapshots are never a recovery source.
- D-030: A destructive rollback must validate both the instruction revision and complete Block
  catalogue on the same transaction/connection used for its writes; a preflight check on another
  connection is diagnostic only and is never the concurrency boundary.

## CODEX - Nullable deletion and authoritative Component block staging (2026-07-27)

### Incident correction

- [x] Traced the production delete failure to null unboxing of `SplitDTO.getParentId()`.
- [x] Confirmed null parents are valid root metadata, including Component-derived Click rows.
- [x] Changed delete execution to use authoritative stored metadata.
- [x] Ordinary parent deletion now resolves a transitive dependent closure and uses the existing
      atomic graph-delete transaction for instructions, variables, and references.
- [x] Backend fix committed and pushed as `c3422325`.

### Component mapping

- [x] Identified the requested Components display-order block 18, `Check payment`, as database
      `component_block.id = 36`.
- [x] Mapped its 15 instructions, two variables, 25 references, IF/ELSE/ENDIF, LOOP, PAUSE,
      GET/CK/E, parent links, and variable links.
- [x] Found that the existing block `+` was filtering the aggregate through row-level `canAdd`,
      staging only instructions 42 and 43 and omitting the dependent graph.
- [x] Routed the existing Components block-header `+` to one typed whole-block Memory item.
- [x] Kept the obsolete blue arrow removed.
- [x] The existing transactional `ComponentMemoryApplyService` remains the sole whole-block copy
      path and remaps generated block, instruction, variable, reference, parent, and GOTO IDs.
- [x] Frontend source committed and pushed as `c8ca242`.
- [x] `npm run build` completed successfully with existing repository warnings; broad tests were
      intentionally deferred.
- [x] Deployed `main.5fdca76c.js` and `main.73f5e771.css` to backend resources.
- [x] Verified deployment parity: 45 source files, 45 target files, zero SHA-256 differences.

### Roadmap

- [x] Added
      `ROADMAP_COMPONENT_MEMORY_VARIABLE_AND_MULTI_EXECUTION_2026_07_27.md`.
- [x] Updated `specifications/VARIABLE_SYSTEM_REDESIGN.md` with Component aggregate rules.
- [ ] Next safe backend phase: one shared transitive dependency-closure service for preview,
      Memory selection, copy, move, and cascade deletion.
- [ ] Easiest independent dashboard phase: add the non-executing `Execution` checkbox column.
- [ ] Searchable Application Type, Name/Description editing, and authoritative realtime metadata
      publication follow.
- [ ] Headed/headless multi-launch and its tests remain explicitly last.

### Decisions

- D-031: A null parent is valid root metadata. Delete and copy paths must remain nullable end to
  end and may never infer corruption from null alone.
- D-032: A whole reusable Component block is one versioned aggregate. The block `+` stages one
  typed `BLOCK` item and never a filtered list of instruction items.
- D-033: Multi-launch cannot wrap the current single Launch call in a loop. It requires an
  explicit run coordinator, execution mode, isolation/concurrency policy, per-job state, and
  cancellation.

## CODEX - Variables Phase 0A started (2026-07-27)

### Read-only production audit

- [x] Audited `D:\Projects\ARWebBancaStato\ARWeb\database.db` without changing rows.
- [x] Found 22 Bot Job variables and 5 Component variables.
- [x] Found zero orphan or cross-owner variable links and zero missing variable owners.
- [x] Found one duplicate owner: Component instruction 44 owns variables 1 and 2; variable 2 is
      unused.
- [x] Found stale `variable_id=1` on Component Wait instruction 45.
- [x] Found Bot Job 18 cloned consumers 190-192 with missing parents; their Component source
      196-198 is structurally correct.
- [x] Confirmed current producer ordering has no GET-after-consumer violation.

### Backward-compatible implementation

- [x] Added canonical variable action/name policy.
- [x] New variables receive `VAR-<instructionId>-<normalized instruction name>`.
- [x] The Variable Editor/create-variable path refuses new duplicate declarations for one owner
      instruction transactionally.
- [x] Existing-variable updates require variable ID + selected Web Field + workspace owner, which
      prevents a stale/forged ID from modifying another Web Field's declaration.
- [x] Dependent operation loading and rewriting also require the selected `variable_id`, preventing
      cross-rewrites when legacy duplicate declarations share one Web Field.
- [x] Recorded that this is not yet a global database invariant; copy/import bypass paths remain
      in Phase 0B scope until repaired data can receive unique indexes.
- [x] GET ordering now protects E, CK, PDF CHECK, and CSV CHECK during row movement.
- [x] Component/Memory dependency normalization uses the same consumer policy.
- [x] Component Memory apply rejects E/CK/PDF CHECK/CSV CHECK selections without their matching GET
      and leaves the target Bot Job unchanged.
- [x] Preserved current SET runtime semantics: SET writes a literal and is not yet required to
      follow GET.
- [x] Focused Maven verification: 45 tests passed with zero failures/errors; no broad suite was
      run.
- [ ] Next: repair/audit service, explicit backup, deterministic legacy cleanup, then unique
      owner-instruction indexes.

### Decisions

- D-034: `variable.instruction_id` owns the Web Field declaration; GET is the runtime producer
  command referencing that declaration.
- D-035: Do not add a uniqueness constraint until duplicate/stale production rows have been
  reported and deterministically repaired.
- D-036: SET cannot be validated as a GET consumer until the Engine distinguishes literal SET from
  variable-source SET and executes those modes accordingly.

## CODEX - Detached Variables relationship workspace (2026-07-27)

### Bot Job-scoped implementation

- [x] Added a **Variables** action to Bot Job Details.
- [x] Added one fixed detached `variablesManager` page; reopening retargets/focuses the singleton
      instead of creating duplicate Variables windows.
- [x] Bound page authority to the active Bot Job Details registry and exact live
      `botJobTasks` transport. Browser-supplied Bot Job identity is not accepted as authority.
- [x] Added Pages Open presentation, native focus participation, local Close behavior, reload
      generation/grace, retirement tombstone, and forced-close fallback.
- [x] Added a Bot Job-scoped backend graph containing declarations, Blocks, commands, active state,
      `DECLARES`/`WRITES`/`READS`/`ASSIGNS_LITERAL`/`INVALID_LINK` edges, summary, revision, and
      diagnostics.
- [x] Effective health/order diagnostics consider active instructions in active Blocks while still
      transporting inactive links for authoring visibility.
- [x] Persisted variable and instruction graph mutations queue an exact-Bot-Job realtime update.
      Per-step execution/status traffic does not rebuild or republish this graph.
- [x] Kept registry access, SQL, and WebSocket sends outside the Variables state monitor; focused
      concurrency coverage verifies the previous lock-inversion path cannot deadlock.
- [x] Sanitized graph-load failures before they cross the WebSocket boundary.

### React relationship explorer

- [x] Added a TEMP-pattern detached page with title/status, Pages counter, local Close, summary,
      Find, health filters, Expand all/Collapse all, and responsive independent scrolling.
- [x] Added a collapsible variable tree and selected flow:
      declaration Web Field -> GET producer -> variable memory -> E/CK/PDF CHECK/CSV CHECK readers.
- [x] Rendered current SET compatibility separately as
      `literal SET -> declaration Web Field`; the UI does not falsely claim current SET reads a
      prior GET variable.
- [x] Added separate selected-variable and whole-Bot-Job diagnostics.
- [x] Added strict canonical snapshot parsing. A malformed variable/command/edge or incomplete
      revision/binding cannot replace the last valid graph.
- [x] Added request correlation, older-workspace rejection, same-workspace binding rotation,
      10-second request timeout, first-load Retry, and last-valid-snapshot preservation.
- [x] Frontend source committed and pushed as `e05503e`.

