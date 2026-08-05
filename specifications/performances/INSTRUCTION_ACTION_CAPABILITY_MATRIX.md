# Instruction Action Capability Matrix

Date: 2026-07-10

## Conditional Grammar

The required grammar is:

```text
IF
  instructions
  ELSEIF        optional, zero or more
    instructions
  ELSE          optional, zero or one
    instructions
ENDIF
```

Rules:

- IF appears once as the family root.
- ELSEIF may appear zero or more times.
- ELSEIF is valid only after IF/another ELSEIF and before ELSE/ENDIF.
- ELSE may appear zero or one time.
- ELSE must follow all ELSEIF branches.
- ENDIF appears once and closes the same IF family.
- No ELSEIF may be inserted after ELSE or after ENDIF for that family.
- Nested IF families require stack-based parsing; order-only boolean scans are insufficient.

## Row Categories

| Category | Actions |
|---|---|
| Native element | `I...`, `O...`, `C...`, link/tag actions |
| Value command | SET, GET, CK, CSV CHECK, PDF CHECK, E |
| Flow command | GOTO, EXCEL GOTO, LOOP, REFRESH_LOOP |
| Conditional boundary | IF, ELSEIF, ELSE, ENDIF |
| Independent command | Q/QUIT, P/SCREEN, H/HOLD, PAUSE, REFRESH, NEXT_ENTER, SWIPE_UP, SWIPE_DOWN, NEXT ROW |

## Control Matrix

Legend: YES = applies; NO = blocked; CONDITIONAL = backend capability required.

| UI/operation | Native element | Value command | Flow command | IF family | Independent |
|---|---:|---:|---:|---:|---:|
| Render operation details | YES | YES | YES | YES, boundary label only | YES |
| Rename display label | YES | NO | NO | NO | NO |
| Device flags F/E/T/N/S | YES | NO | NO | NO | NO |
| Test click | YES | NO | NO | NO | NO |
| Ordinary up/down buttons | YES, graph-safe only | YES, graph-safe only | CONDITIONAL | NO | YES |
| Drag within block | YES, graph-safe only | YES, graph-safe only | group/graph rules | family/group only | YES |
| Drag across blocks | CONDITIONAL | CONDITIONAL | NO if referenced | NO partial move | CONDITIONAL |
| Memory-list `+` | YES | CONDITIONAL | NO for loop boundaries | NO | CONDITIONAL |
| Down-arrow floating panel | YES | YES | YES | YES, restricted actions | YES |
| Edit command | N/A for native row | YES | YES with restrictions | NO independent edit | YES |
| Add element property command | YES if compatible | NO | NO | NO | NO |
| Split Component | CONDITIONAL | CONDITIONAL | NO across graph edge | NO inside family | CONDITIONAL |
| Delete single row | CONDITIONAL on dependents | CONDITIONAL on dependents | graph-aware | ELSEIF only; others delete family | CONDITIONAL |

## Predicate Review

### `allSpecialOperations`

Current positive list:

```text
SET GET CK Q E P H GOTO IF ELSEIF ELSE ENDIF PAUSE REFRESH LOOP
REFRESH_LOOP NEXT_ENTER SWIPE_UP SWIPE_DOWN EXCEL GOTO NEXT ROW
CSV CHECK PDF CHECK
```

Current effects when true:

- `renderEditButton` suppresses ordinary name editing.
- `renderDeviceOptionsRow` suppresses F/E/T/N/S controls.
- `renderTestClick` suppresses test click.

These effects are broadly correct for special operations, but one list should not own unrelated capabilities. Replace it with separate predicates:

- `isNativeElement`
- `canRenameDisplayLabel`
- `supportsDeviceFlags`
- `supportsElementTest`
- `isConditionalBoundary`
- `isLoopBoundary`
- `canAddToMemory`

### `editableSpecialOperations`

Current positive list excludes IF-family boundaries and includes command rows. This correctly prevents the legacy Edit Operation entry for IF/ELSEIF/ELSE/ENDIF.

It is incomplete because editability also depends on:

- whether the referenced element/variable/block still exists;
- whether changing command family would break parent edges;
- EXCEL GOTO singleton ownership;
- session capabilities;
- graph revision.

### `renderOperations`

Positive behavior:

- Renders action-specific details for SET/GET/checks/GOTO/loops/swipes and other commands.
- Resolves loop parents and reports invalid parent ids.

Negative behavior:

- It is presentation logic and must not decide command legality.
- Invalid parent warnings show that graph integrity can already be broken before rendering.
- Parsing operation strings in React duplicates backend format knowledge.

### `renderEditButton`

Applies only to native elements because all special operations return an empty spacer.

Required rule: rename edits `clientNamed`, never the canonical instruction name used by matching/recovery.

### `renderMoveButtons`

Currently blocks only IF, ELSEIF, ELSE, ENDIF.

Required additions:

- Disable independent movement of LOOP/REFRESH_LOOP and their marked parent.
- Use graph capabilities for rows with dependents.
- Do not show buttons when the resulting destination is invalid.

### `renderTestClick`

Current behavior shows test click for every row not in `allSpecialOperations`.

Required refinement: show only when the row has a testable web element/locator. Unknown non-special rows must not automatically receive it.

### Memory `+`

Immediate rule implemented:

- IF, ELSEIF, ELSE, ENDIF never show `+` and are rejected by `handleAddToMemory`.

Additional rules to confirm:

- LOOP/REFRESH_LOOP boundaries should not be copied independently.
- A native parent with loop flags may require copying the complete connected group.
- Commands referencing variables/elements may require dependency inclusion or rejection.

## Floating Panel Rules For IF Family

| Selected row | Allowed panel actions | Blocked panel actions |
|---|---|---|
| IF | Insert command inside first branch; add ELSEIF before ELSE/ENDIF; delete family with confirmation | Edit/convert IF; memory `+`; split inside family; independent cross-block move |
| ELSEIF | Insert command inside branch; add another ELSEIF after this branch but before ELSE/ENDIF; delete this ELSEIF branch with impact confirmation | Convert boundary; memory `+`; place after ELSE/ENDIF |
| ELSE | Insert command inside ELSE branch; delete family only through explicit family action | Add ELSEIF after ELSE; add second ELSE; edit boundary; memory `+` |
| ENDIF | Insert command after family; delete family only through explicit family action | Add ELSEIF after ENDIF; edit boundary; memory `+` |

The phrase "before/after selected row" is insufficient for conditionals. The backend must resolve a semantic placement such as `INSIDE_IF_BRANCH_END`, `NEW_ELSEIF_BEFORE_ELSE`, or `AFTER_CONDITIONAL_FAMILY`.

## Backend Enforcement

Every capability must be returned by `InstructionGraphService` and revalidated when applying:

- memory copy/apply;
- command add/edit;
- ELSEIF insertion;
- drag/move;
- split;
- delete;
- cross-block movement.

React predicates remain presentation helpers only. A crafted WebSocket request must not bypass a blocker.
