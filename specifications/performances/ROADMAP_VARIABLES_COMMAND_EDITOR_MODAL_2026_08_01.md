# Variables Command Editor Modal Roadmap

Date: 2026-08-01  
Status: **IN PROGRESS — CE-1 through CE-9 complete; CE-10 remains**
Primary entry point: green edit button in Variables > All Commands

## Current implementation handoff

The active stop point is CE-9. CE-10 acceptance and deferred tests are next and require the user's
runtime review.

Session handoff (2026-08-01): **PAUSED FOR USER ACCEPTANCE TOMORROW**. Do not start CE-10 and do not
activate typed IF/ELSEIF execution until the user reports the CE-9 runtime results.

Completed and pushed:

- CE-1: explicit editor eligibility;
- CE-2: independent target Block and placement;
- CE-3: CANCEL, UPDATE, and COPY NEW shell;
- CE-4: typed LOOP, REFRESH_LOOP, and Wait editors;
- CE-5: atomic same-ID UPDATE persistence and authoritative publication;
- CE-6: atomic fresh-ID, relationship-free COPY NEW persistence and authoritative publication;
- CE-7: typed CheckValue, CSV/PDF Check, and ExcelWrite editors with shadow persistence.
- CE-8: typed GOTO and SWIPE repetition editors using the existing intrinsic count storage.
- CE-9: typed IF/ELSEIF editors with shadow persistence; legacy conditional execution is unchanged.

Persistence commits awaiting Claude review:

- backend UPDATE: `f273119f`;
- backend COPY NEW: `1259f18b`;
- frontend COPY NEW: `05e6c22`;
- deployed bundle: `c9a9395b`.
- frontend CE-7: `ed300a0`;
- backend CE-7: `88628393`;
- deployed CE-7 bundle: `f482756a`.
- frontend CE-8: `1157f10`;
- backend CE-8: `c5ff63c5`;
- deployed CE-8 bundle: `641bb887`.
- frontend CE-9: `d545b71`;
- backend CE-9: `f0e33ef0`;
- deployed CE-9 bundle: `56d1942f`.

No Maven or backend tests were run for CE-6 or CE-7 by user instruction. The frontend production build
passed with existing warnings. CE-10 is the remaining roadmap step.

## Adjacent Variables improvements — outside CE-7 through CE-10

These improvements belong to the Variables workspace but are not Command Editor modal steps. They
must be implemented and reviewed independently so they cannot delay or destabilize CE-7–CE-10.

### AV-1 — Repeatable Add Variable modal

Modal: `Add Variable — Define a producer-free Bot Job variable`.

Status: **implemented; runtime acceptance pending** (`f92c3d5` frontend,
`771a79d8` backend).

- On every open and after every successful creation, inspect the current Bot Job's variable names
  case-insensitively and calculate the first free sequential name: `Variable_1`, `Variable_2`, and
  so on.
- Put that suggested name into the Variable name input as its actual editable value, not as a
  placeholder.
- A name such as `variable_1`, `VARIABLE_1`, or `Variable_1` occupies the same sequence number.
- Put a small `ADD` button immediately to the right of the Variable name input.
- `ADD` stages the current valid name in the modal and immediately prepares the next free suggested
  name, allowing several variables to be assembled in one visit.
- `CREATE VARIABLE` persists the staged names plus the current valid input using the authoritative
  Variables creation path. Each created definition begins producer-free and `VOID` according to the
  existing runtime-memory contract.
- A successful `CREATE VARIABLE` must not close the modal. Keep it open, show the standard result
  message, refresh the authoritative Variables snapshot, clear successfully created staged entries,
  and prefill the next free `Variable_X` suggestion.
- Only the user's explicit Close/Cancel action closes the modal.
- Duplicate validation and backend persistence remain case-insensitive and authoritative; a stale
  suggestion must never create a duplicate if another page creates the same name concurrently.

Implementation note: the modal stages all requested names and the existing authoritative
single-variable WebSocket creation path commits them sequentially. A partial failure stops the
remaining queue and reports how many definitions were already committed; it never falsely reports
the complete batch as successful.

### AV-2 — Block-scoped Release Connections modal

Status: **implemented; runtime acceptance pending** (`f92c3d5` frontend).

- Add the same Block search/filter pattern already used by Resolve Connections and Review
  Connections to the Release Connections modal.
- Synchronize it bidirectionally with the Variables page's left-panel Block filter.
- Empty Block selection means **all Blocks** and preserves the current release-all behavior.
- Selecting one Block means the modal displays, counts, and releases only connections whose source
  instructions belong to that Block.
- Changing the Block inside Release Connections immediately updates both the modal scope and the
  Variables page Block filter.
- The submitted release mutation must be rebuilt from the currently selected Block scope; hidden
  connections from other Blocks must not be released accidentally.
- Always allow the Release Connections modal to open, including when the initial Block has zero
  releasable connections. The user must be able to change the Block filter inside the modal.
- A zero-connection scope is a valid review state. It performs no mutation and must not close or
  block the modal with an unrelated error.
- Preserve the existing rule that release removes relationships only; it does not delete commands,
  Blocks, or variable definitions.

## 1. Purpose

Replace the green square's former quick-reconnect behavior with an independent editor for commands
that own intrinsic configuration. Relationship repair remains the responsibility of the existing
Reconnect Parent, Reconnect Variable, Resolve Connections, and Review Connections flows.

The editor must make future command changes easy to isolate, debug, extend, and roll back. Every
command family therefore receives its own React component, TypeScript draft contract, and
validation rules.

## 2. Target Block is not a Variables filter

The Block control inside `ComponentEditorModal` must not synchronize with the Variables page Block
filter.

It represents the **containing target Block** for UPDATE or COPY NEW:

- it initially selects the Block containing the opened command;
- selecting another Block reloads the placement choices from that Block's current instructions;
- it does not filter the modal, Variables page, Review Connections, or Resolve Connections;
- changing the Variables page filter while the modal is open must not change this target.

The target Block is distinct from a command relationship such as:

- a GOTO destination Block;
- a LOOP anchor instruction;
- a connected Web Element parent;
- a connected runtime variable.

## 3. Placement control

Placement is derived from the selected target Block and uses this order:

```text
Keep current position        UPDATE only, current Block only
At the top
At the end
After #1 Instruction name
After #2 Instruction name
After #3 Instruction name
...
```

Rules:

1. Opening an existing command defaults UPDATE to **Keep current position**.
2. Changing only command fields and pressing UPDATE never moves the instruction.
3. Selecting a different placement in the current Block updates its order.
4. Selecting another target Block removes **Keep current position** and requires a valid placement.
5. The `After` list is rebuilt immediately whenever the target Block changes.
6. The selected command itself is excluded from same-Block `After` options for UPDATE.
7. Placement is submitted as typed intent, not as a guessed final order number:
   `KEEP`, `TOP`, `END`, or `AFTER_INSTRUCTION` plus the reference instruction ID.
8. Java persists the submitted intent atomically and publishes the authoritative updated grid.

## 4. Modal actions

### 4.1 UPDATE

UPDATE modifies the existing instruction ID.

- Same Block + `KEEP`: update intrinsic command configuration only.
- Same Block + another placement: update and reorder the same instruction.
- Different target Block: update and move the same instruction to the chosen placement.
- The source row is never cloned during UPDATE.
- The committed result must update Variables and GridItem through the normal realtime snapshot.

Relationship behavior when UPDATE moves across Blocks requires command-aware validation:

- Block-local Web Element or LOOP-anchor relationships that are invalid in the target Block become
  disconnected and visible through the existing reconnect badges.
- Bot Job-scoped variable bindings may remain only when the command contract allows them.
- GOTO cannot target its own containing Block.
- No missing relationship may prevent the row itself from being moved; it becomes a diagnostic.

### 4.2 COPY NEW

COPY NEW creates a new instruction ID and never changes or removes the original.

This is an intentionally **pure command copy**:

- copy the selected command action, editable intrinsic fields, and client-edited name;
- create the new row in the selected target Block and placement;
- do not copy `parent_id`;
- do not copy `parent_block_id`;
- do not copy `variable_id`;
- do not copy declaration ownership or producer/consumer relationships;
- do not move or reuse the original instruction ID.

The new row starts disconnected wherever its command policy requires relationships. Existing red
reconnect badges and Resolve Connections can attach those relationships explicitly afterward.

For GOTO and LOOP this means COPY NEW preserves intrinsic values such as count/interval, but not
the GOTO destination or LOOP anchor.

### 4.3 CANCEL

CANCEL closes the modal without changing database state, order, relationships, or Variables page
selection.

## 5. Green edit-button eligibility

The current rule, `policy.role !== WEB_ELEMENT`, is too broad. Replace it with a dedicated
TypeScript eligibility policy.

| Command | Green editor | Editable intrinsic configuration |
|---|---:|---|
| LOOP | yes | interval and iterations |
| REFRESH_LOOP | yes | interval and iterations |
| H / Wait | yes | waiting seconds |
| CK / CheckValue | yes, critical | operator and typed comparison operand |
| PDF CHECK | yes | operator, operand, and PDF source configuration |
| CSV CHECK | yes | operator, operand, and CSV source configuration |
| GOTO | yes | GOTO count; destination remains a relationship |
| SWIPE_UP | yes | repetition count |
| SWIPE_DOWN | yes | repetition count |
| IF | yes after typed-condition contract | variable/operand/operator expression |
| ELSEIF | yes after typed-condition contract | variable/operand/operator expression |
| ExcelWrite / `E` | yes | output key, column, file configuration |
| GET | no | relationships only; use reconnect flows |
| SET | no | relationships only; use reconnect flows |
| EXCEL GOTO | no | no editable intrinsic value; use Block reconnect |
| ELSE | no | structural marker |
| ENDIF | no | structural marker |
| REFRESH | no | no configurable value |
| PAUSE | no | no configurable value |
| NEXT_ENTER | no | no configurable value |
| Screenshot / Close Browser | no | no configurable value |

GET and SET are deliberately excluded. Their Web Element and variable relationships are already
editable through the relationship modals, so another editor would be redundant.

## 6. Modal structure

```text
Command Editor
  Bot Job context
  Selected command summary
  Target Block
  Placement
  Command-specific editor
  Validation/diagnostics
  CANCEL | COPY NEW | UPDATE
```

The previously added Blocks/Commands/Connections/Diagnostics presentation may remain as context,
but the Block SearchBox must be replaced with the independent Target Block selector.

## 7. React and TypeScript separation

```text
command-editor/
  ComponentEditorModal.tsx
  ComponentEditorModal.module.scss
  componentEditor.types.ts
  commandEditorEligibility.ts
  commandEditorDraft.ts
  commandEditorPlacement.ts
  commandEditorValidation.ts
  commandEditorMutation.ts

  editors/
    LoopCommandEditor.tsx
    LoopCommandEditor.module.scss
    RefreshLoopCommandEditor.tsx
    RefreshLoopCommandEditor.module.scss
    WaitCommandEditor.tsx
    WaitCommandEditor.module.scss
    CheckValueCommandEditor.tsx
    CheckValueCommandEditor.module.scss
    ExternalCheckCommandEditor.tsx
    ExternalCheckCommandEditor.module.scss
    GotoCommandEditor.tsx
    GotoCommandEditor.module.scss
    SwipeCommandEditor.tsx
    SwipeCommandEditor.module.scss
    ConditionalCommandEditor.tsx
    ConditionalCommandEditor.module.scss
    ExcelWriteCommandEditor.tsx
    ExcelWriteCommandEditor.module.scss
```

Each editor receives a typed draft and emits intrinsic configuration only. It does not open a
WebSocket, query the database, modify Block order, or decide relationship repair.

## 8. Typed contracts

```ts
type CommandEditorPlacement =
  | { kind: 'KEEP' }
  | { kind: 'TOP' }
  | { kind: 'END' }
  | { kind: 'AFTER_INSTRUCTION'; instructionId: number };

type CommandEditorAction = 'UPDATE' | 'COPY_NEW';

interface CommandEditorMutationIntent<TDraft> {
  action: CommandEditorAction;
  sourceInstructionId: number;
  targetBlockId: number;
  placement: CommandEditorPlacement;
  draft: TDraft;
  graphRevision: string;
}
```

React owns form state, placement construction, eligibility, validation messages, and typed mutation
intent. Java owns authorization, revision checks, atomic persistence, ID generation for COPY NEW,
and committed realtime publication.

## 9. Small implementation sequence

### CE-1 - Eligibility policy

- Create `commandEditorEligibility.ts`.
- Replace the broad non-Web-Element condition.
- Keep red relationship controls unchanged.

### CE-2 - Independent target and placement

- Remove synchronization between the modal Block control and Variables.
- Add Target Block and dynamic Placement controls.
- Add typed placement contracts and client validation.

### CE-3 - Modal action shell

- Add CANCEL, COPY NEW, and UPDATE.
- Keep mutation buttons disabled until a command-specific draft is valid.
- Add confirmation when UPDATE changes Block or order.

### CE-4 - First command editors

- Implement LOOP.
- Implement REFRESH_LOOP.
- Implement H / Wait.
- Hydrate their current values from the selected instruction.

### CE-5 - Atomic UPDATE persistence

- Add a dedicated backend update transaction.
- Preserve the instruction ID.
- Support same-Block reorder and cross-Block move.
- Publish the authoritative grid revision.

### CE-6 - Pure COPY NEW persistence

- Generate a new instruction ID.
- Clear all parent, Block-target, variable, and ownership relationships.
- Insert at the requested target placement.
- Preserve the original row unchanged.

### CE-7 - Check and output editors

- [x] Implement CheckValue with the new typed variable-operation contract.
- [x] Implement CSV/PDF Check editors.
- [x] Implement ExcelWrite output configuration.

CE-7 implementation evidence (2026-08-01):

- React commit `ed300a0` adds isolated CheckValue, external CSV/PDF Check, and ExcelWrite editors.
- Java commit `88628393` adds the typed shadow configuration migration/repository and extends only
  the Variables Command Editor UPDATE/COPY transactions.
- Deployed bundle commit `f482756a` contains the successful frontend production build.
- CE-7 deliberately does not activate the V2 executors and does not overwrite legacy
  `instruction.operation` for Check/ExcelWrite commands. This keeps current execution behavior
  available while typed configuration is reviewed and migrated.
- Maven/backend tests were not run at the user's request; Java compilation and runtime migration
  acceptance remain pending.

### CE-8 - Navigation and gesture editors

- [x] Implement GOTO count.
- [x] Implement SWIPE counts.

CE-8 implementation evidence (2026-08-01):

- React commit `1157f10` adds isolated GOTO and SWIPE editor components.
- Java commit `c5ff63c5` validates and atomically persists their positive count values through the
  existing Variables Command Editor UPDATE/COPY transactions.
- GOTO destination Block remains a separate reconnectable relationship and COPY NEW continues to
  clear it. SWIPE direction remains fixed by the selected command.
- Deployed bundle commit `641bb887` contains the successful frontend production build.
- Maven/backend tests were not run at the user's request.

### CE-9 - Typed conditional editors

- [x] Implement IF and ELSEIF only after conditional expression persistence is approved.
- [x] Keep ELSE and ENDIF structural and without green edit buttons.

CE-9 implementation evidence (2026-08-01):

- React commit `d545b71` adds the isolated typed conditional editor and limits eligibility to IF and
  ELSEIF; ELSE and ENDIF remain structural.
- Java commit `f0e33ef0` appends typed condition-source and left-variable persistence and extends only
  the Variables Command Editor UPDATE/COPY boundary.
- `PREVIOUS_RESULT` preserves current production execution. `VARIABLE_COMPARISON` is stored as a
  typed shadow contract for later executor activation; CE-9 does not rewrite legacy operation data.
- COPY NEW clears typed variable references and stores `PREVIOUS_RESULT` on the disconnected copy.
- Deployed bundle commit `56d1942f` contains the successful frontend production build.
- Maven/backend tests were not run at the user's request.

### CE-10 - Acceptance and deployment

- Update and copy commands in the current Block.
- Update and copy into another Block at top/end/after every valid row.
- Verify COPY NEW has a fresh ID and no inherited relationships.
- Verify the original remains unchanged.
- Verify Variables and GridItem update without manual refresh.

## 10. First implementation slice

Implement CE-1 through CE-4 first. This produces the correct UI contract and useful editors for
LOOP, REFRESH_LOOP, and Wait without changing variable/check semantics prematurely.
