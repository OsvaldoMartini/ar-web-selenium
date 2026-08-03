# EXCEL GOTO TypeScript Engine Roadmap

Date: 2026-08-03  
Status: planned; requirements normalized; implementation not started  
Scope: EXCEL GOTO only

## 1. Purpose

EXCEL GOTO is a dataset-loop controller. It does not read a Web Element and it is not a normal
instruction-to-instruction parent relationship. It controls which Excel data row is currently in
memory and which Bot Job Block executes next.

The new implementation will place the deterministic EXCEL GOTO control rules in an independent
React/TypeScript engine. Java will remain responsible for durable persistence, authorized file and
database access, and broadcasting committed runtime state. Java must not independently reproduce a
second, conflicting copy of the EXCEL GOTO decision rules.

This roadmap does not implement or change LOOP, REFRESH LOOP, IF, GET, SET, or CHECKVALUE rules.

## 2. Corrected functional rules

### 2.1 Initial constraint

- A Bot Job may contain only one active EXCEL GOTO instruction during the first implementation.
- Adding or transforming a second instruction into EXCEL GOTO must be refused with a clear client
  message before persistence.
- Multiple Excel files may exist, but the first implementation selects only one dataset for the
  Bot Job execution.

### 2.2 First execution and rerun

When a Bot Job execution or Smoke Test starts or reruns:

1. Start execution from the first configured Bot Job Block.
2. Load the first row of the selected Excel dataset.
3. Copy that row's exact raw values into execution memory without changing locale, currency, date,
   decimal, empty-string, or text formatting.
4. Execute the ordered instructions using those current row values.

A rerun starts again at the first dataset row. It does not silently resume from the final row of a
previous run.

### 2.3 EXCEL GOTO decision

EXCEL GOTO is placed at the end of the Block/use-case range controlled by the dataset. When
execution reaches it:

- If another Excel row exists:
  1. advance to the next row;
  2. load that row into execution memory;
  3. jump to the configured **Return Block**;
  4. execute the controlled use case again with the new row.
- If no row remains:
  1. mark the dataset loop complete;
  2. jump to the configured **End Block**, or continue after the controlled range when the End
     Block policy explicitly represents normal continuation;
  3. never jump back to the Return Block again for that run.

### 2.4 Future multiple-dataset rule

Future support may allow several Excel datasets in one Bot Job. Each dataset must own an isolated
Block/use-case scope with its own Return Block and End Block. Dataset scopes must never overlap or
cross-jump into one another.

## 3. Terminology

- **Dataset**: the selected Excel file and sheet/range used by one EXCEL GOTO controller.
- **Current Row**: the dataset row whose exact values are currently loaded into execution memory.
- **Return Block**: the first Block executed again after advancing to another dataset row.
- **End Block**: the Block entered after the final dataset row completes.
- **Controlled Scope**: the ordered Blocks/use case repeated for every dataset row.
- **Row Cursor**: the zero-based internal dataset position; the UI displays it as Row 1, Row 2,
  and so on.

## 4. State machine

```text
NOT_STARTED
  -> LOAD_FIRST_ROW
  -> EXECUTING_SCOPE
  -> EXCEL_GOTO_REACHED
       -> more rows: ADVANCE_ROW -> LOAD_ROW_MEMORY -> JUMP_RETURN -> EXECUTING_SCOPE
       -> no rows:   DATASET_COMPLETE -> JUMP_END -> CONTINUE_EXECUTION
```

The engine must return an explicit decision. It must not modify the instruction pointer, runtime
memory, or database through hidden side effects.

```ts
type ExcelGotoDecision =
  | { type: 'LOAD_FIRST_ROW'; rowIndex: number }
  | { type: 'ADVANCE_AND_JUMP_RETURN'; rowIndex: number; returnBlockId: number }
  | { type: 'COMPLETE_AND_JUMP_END'; endBlockId: number }
  | { type: 'COMPLETE_AND_CONTINUE' }
  | { type: 'CONFIGURATION_ERROR'; code: string; message: string };
```

## 5. Configuration contract

```ts
interface ExcelGotoConfiguration {
  instructionId: number;
  botJobId: number;
  datasetId: string;
  returnBlockId: number;
  endBlockId: number | null;
  controlledBlockIds: number[];
  active: boolean;
}

interface ExcelGotoRuntimeState {
  executionId: string;
  botJobId: number;
  datasetId: string;
  rowIndex: number;
  rowCount: number;
  status: 'NOT_STARTED' | 'RUNNING' | 'COMPLETE' | 'STOPPED' | 'FAILED';
  currentRowValues: Record<string, string>;
  revision: number;
}
```

All row values remain exact raw text. A legitimate empty Excel cell is `VALUE("")`; `VOID` means
that no usable value is available under the runtime-variable contract.

## 6. Validation rules

The React/TypeScript planner validates the complete configuration before submitting it:

- no more than one active EXCEL GOTO exists in the Bot Job;
- the selected dataset exists and can be read;
- Return Block exists in the same Bot Job;
- End Block, when configured, exists in the same Bot Job;
- Return Block and End Block are different;
- EXCEL GOTO belongs to its declared controlled scope;
- Return Block belongs to the controlled scope;
- End Block does not restart or re-enter the completed scope;
- controlled Blocks are ordered, unique, and do not cross another dataset scope;
- the graph revision and workspace epoch still match when persistence occurs.

The backend revalidates ownership, IDs, uniqueness, and revision/CAS safety before persistence. It
does not choose a different Return Block, End Block, dataset, or row-transition decision.

## 7. Independent TypeScript design

Create an isolated engine package rather than adding EXCEL GOTO branches throughout the Variables
page or Smoke Test component:

```text
src/components/variables/engine/excel-goto/
  ExcelGotoEngine.ts
  excelGoto.types.ts
  excelGotoPolicy.ts
  excelGotoStateMachine.ts
  excelGotoPlanner.ts
  excelGotoDiagnostics.ts
  excelGotoSmokeAdapter.ts
  excelGotoRuntimeAdapter.ts
```

Responsibilities:

- `ExcelGotoEngine.ts`: public orchestration API only.
- `excelGotoPolicy.ts`: one-per-Bot-Job and Block-scope compatibility rules.
- `excelGotoStateMachine.ts`: pure row-transition and jump decisions.
- `excelGotoPlanner.ts`: immutable execution plan creation.
- `excelGotoDiagnostics.ts`: client-facing configuration diagnostics.
- `excelGotoSmokeAdapter.ts`: deterministic synthetic dataset integration.
- `excelGotoRuntimeAdapter.ts`: production bridge contract; no UI rendering.

## 8. Smoke Test behavior

The Smoke Test uses synthetic Excel rows but executes the same TypeScript state machine used by the
production plan.

Example:

```text
Row 1: USER=SMOKE_USER_1, AMOUNT=100.00
Row 2: USER=SMOKE_USER_2, AMOUNT=200.00
Row 3: USER=SMOKE_USER_3, AMOUNT=
```

For every row, the Smoke Test must visibly:

1. load the current row into run memory;
2. execute the controlled Blocks;
3. reach EXCEL GOTO;
4. advance and jump to Return Block, or complete and jump to End Block;
5. report the exact row transition in the step log.

An empty synthetic dataset completes without executing the controlled scope. A one-row dataset
executes the scope once and then completes.

## 9. Dataset execution panel

Add a left-side floating or docked panel for EXCEL GOTO execution visibility:

- dataset/file and sheet name;
- current row and total rows, such as `Row 3 of 10`;
- current raw row values;
- Return Block and End Block;
- current state: loading, executing, advancing, complete, stopped, or failed;
- latest transition, such as `Row 3 -> Row 4 -> Block #2 Login`.

The panel follows real-time engine events. It must not calculate a separate row cursor or a second
execution state.

## 10. Backend and persistence boundary

Recommended durable separation:

- `bot_job_excel_goto_definition`: configuration, with a unique Bot Job constraint during the
  first implementation;
- `bot_job_excel_goto_runtime`: current execution/dataset cursor when durable resume or audit is
  required;
- the existing durable runtime-variable storage: exact current values loaded from the row.

Java responsibilities:

- authorized Excel/file access;
- durable configuration and runtime-state transactions;
- uniqueness, ownership, and CAS/revision enforcement;
- broadcasting committed snapshots;
- applying the exact TypeScript-authored execution decision to the production executor bridge.

TypeScript responsibilities:

- configuration compatibility;
- execution planning;
- row-transition decisions;
- Return/End Block decisions;
- Smoke Test simulation and visualization state.

The legacy Java EXCEL GOTO behavior remains readable as a migration reference until acceptance is
complete. New code must not call both legacy and new decision engines for the same execution.

## 11. Small, reversible implementation phases

### EG-0 — Legacy baseline

- Map the current Java EXCEL GOTO data source, row cursor, jump target, and executor call sites.
- Record read-only fixtures for empty, one-row, and multi-row datasets.
- Make no runtime behavior change.

Rollback: documentation-only.

### EG-1 — Pure TypeScript engine

- Add the independent types, policy, planner, state machine, and diagnostics.
- Run it only against in-memory fixtures with no page or backend integration.

Rollback: remove the isolated engine folder.

### EG-2 — Typed configuration persistence

- Add dedicated configuration persistence and snapshot DTOs.
- Enforce one active EXCEL GOTO per Bot Job.
- Keep the production executor on legacy behavior.

Rollback: stop publishing the typed configuration; legacy execution remains unchanged.

### EG-3 — Command Editor integration

- Add Dataset, Return Block, End Block, and controlled-scope fields.
- Validate and persist them atomically.
- Show explicit diagnostics for missing or invalid targets.

Rollback: hide the editor fields and retain persisted data.

### EG-4 — Smoke Test integration

- Connect the new engine to deterministic synthetic Excel rows.
- Add visible row transitions, Return Block jumps, and End Block completion.
- Do not touch production Playwright execution.

Rollback: detach only `excelGotoSmokeAdapter.ts`.

### EG-5 — Dataset panel

- Add the left dataset panel driven by existing Smoke Test events.
- Display current row values and jump transitions in real time.

Rollback: remove the presentation component; engine behavior remains unchanged.

### EG-6 — Production dataset bridge

- Add the Java file/data provider and durable runtime-state service.
- Load exact row text into the new runtime-variable storage.
- Keep legacy production jumps active until comparison succeeds.

Rollback: disable the new bridge and continue with legacy execution.

### EG-7 — Production cutover

- Route EXCEL GOTO execution through the TypeScript-authored plan and state decisions.
- Disable the legacy semantic decision path for EXCEL GOTO.
- Preserve Java persistence, authorization, file access, and executor integration.

Rollback: feature-flag back to the legacy executor without deleting new configuration.

### EG-8 — Multiple datasets (deferred)

- Permit several EXCEL GOTO controllers.
- Require independent, non-overlapping controlled scopes.
- Add a dataset-specific Return Block, End Block, cursor, memory namespace, and panel section.

Rollback: retain the one-controller feature flag and existing unique constraint policy.

## 12. Edge cases and required outcomes

- **No Excel rows**: report `DATASET_EMPTY`, jump to End Block/continue, and do not execute the
  controlled scope.
- **One row**: load once, execute once, and complete without an extra Return Block jump.
- **Missing Return Block**: configuration error; never guess a Block.
- **Missing End Block**: use explicit continue policy only; never silently select a Block.
- **Inactive EXCEL GOTO**: do not loop; follow normal ordered execution.
- **Inactive Return or End Block**: configuration diagnostic before execution.
- **Stopped execution**: preserve committed runtime values and record the stopped cursor.
- **Rerun**: reset the EXCEL GOTO cursor to the first row.
- **Failed instruction inside the scope**: follow the existing execution failure policy; do not
  silently advance the Excel row.
- **Refresh/reconnect**: reload the authoritative current cursor and row values; do not restart the
  loop implicitly.
- **Locale-formatted data**: preserve exact text such as `1.234,56`, `1,234.56`, dates, currencies,
  and empty strings.

## 13. Acceptance criteria

- A second active EXCEL GOTO cannot be created in the same Bot Job.
- Zero, one, and multiple rows produce the documented number of controlled-scope executions.
- Every row after the first loads before the Return Block executes.
- The final row goes to End Block/continuation exactly once.
- The dataset panel and step log show the same authoritative row cursor.
- Smoke Test and production use the same TypeScript state machine.
- Exact raw Excel values reach runtime memory unchanged.
- Refresh, WebSocket reconnection, and page closure do not corrupt the execution cursor.
- No Java and TypeScript rule engines independently decide different EXCEL GOTO transitions.

## 14. Deferred decisions

- Whether a stopped production execution may explicitly **Resume Current Row** in addition to
  **Rerun From First Row**.
- Whether End Block is mandatory or an explicit `CONTINUE_AFTER_SCOPE` option is sufficient.
- How Excel file/sheet/range selection is represented for multiple datasets.
- Whether dataset runtime cursors require long-term audit history or only the latest durable state.

