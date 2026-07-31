# Bot Job Variable Runtime Cutover

Date: 2026-07-30

## Contract

- `bot_job_variable_definition` is the only active Bot Job variable-definition source.
- `bot_job_runtime_memory` owns the Bot Job-wide runtime revision and reset generation.
- `bot_job_runtime_variable_value` owns the latest committed runtime value for each definition.
- `variable` is retained temporarily only as a migration/rollback source. Runtime code must not
  read or write it after cutover.
- Runtime values are exact raw text. No trim, locale conversion, currency conversion, or numeric
  coercion is allowed in canonical storage.
- `VALUE("")` is a valid empty value. `VOID` is a separate state with a reason and a null raw value.

## Legacy migration

1. Read legacy definitions in deterministic `(bot_job_id, instruction_id, id)` order.
2. For duplicate non-null `(bot_job_id, instruction_id)` owners, retain the lowest variable ID.
3. Remap every `instruction.variable_id` from each duplicate ID to the retained ID before
   retiring that duplicate from the new definition catalog.
4. Never deduplicate definitions whose legacy `instruction_id` is null.
5. Copy legacy `variable.value` only to `configured_value` definition metadata. Never use it as a
   live runtime value.
6. Initialize every migrated runtime value as `VOID / NO_PRODUCER_YET`.
7. Record every retained and merged ID in `bot_job_variable_migration_note`.
8. Keep the legacy table untouched until the new schema, references, counts, and migration notes
   have been verified.

## Runtime lifecycle

- Stop, pass, failure, page close, WebSocket reconnect, Bot Job switching, and backend restart
  preserve committed runtime values.
- New and cloned Bot Jobs start with `VOID` values.
- `CLEAR ALL VALUES` atomically resets values to `VOID` without deleting definitions or links.
- Test Run and Launch accept `runtimeMemoryPolicy: KEEP | RESET`; missing policy means `KEEP`.
- A reset is committed before the first instruction executes.
- Variable or Bot Job deletion removes its runtime rows as part of the same owner-scoped change.

## Synchronization

1. React owns form validation, confirmation dialogs, local drafts, and presentation state.
2. Java authorizes the exact Bot Job owner and persists the requested mutation.
3. The database transaction increments entry and Bot Job runtime revisions.
4. Java updates its execution cache only after commit.
5. WebSocket publication carries the committed revision and value snapshot.
6. React ignores stale revisions and reconciles its `useState` mirror.

Manual updates carry `expectedEntryRevision`. Clear-all carries `baseRuntimeRevision`. A stale
request returns the committed value instead of silently overwriting it.

## Delivery slices

- [x] Create and verify the four new tables for every supported database dialect.
- [x] Migrate and audit legacy definitions, including deterministic duplicate consolidation.
- [x] Introduce separated definition/runtime repositories and an owner-scoped runtime service.
- [x] Cut graph, mutation, execution, copy, delete, backup, and restore queries over to the new
      definition table.
- [x] Persist exact GET/SET values and remove canonical locale rewriting.
- [x] Add Variables `+ ADD`, `CLEAR ALL VALUES`, editable raw values, and realtime synchronization.
- [x] Add Test Run/Launch KEEP versus RESET selection.
- [x] Replace process-memory clearing on database reload with cache invalidation and hydration.
- [x] Verify focused migration, lifecycle, concurrency, backend, and React tests.
- [x] Build/deploy the React bundle, commit, and push both repositories.

## Delivery verification

- Durable migration/runtime lifecycle suite: 53 tests passed.
- Graph, copy, delete, rollback, preflight, relationship, command-editor, and component-memory
  regression suite: 78 tests passed.
- React variable controls and execution-policy suite: 18 tests passed.
- React production build completed and was mirrored into `src/main/resources/build`.
