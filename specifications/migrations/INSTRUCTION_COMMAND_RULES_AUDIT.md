# Instruction And Command Rules Audit

Date: 2026-07-10

## Scope

This document records the behavior currently distributed across:

- React `GridItem.tsx` and `GridItemComp.tsx`
- JavaFX `ARNewCommandPane` and `ARNewCommandScene`
- `SimpleWebSocketServer`
- `PerformDataBase.preFillNewInstruction`
- `InstructionLoadMatcher`
- variable persistence and parent-reference handling

It is a findings document, not a statement that every current behavior is correct.

## Confirmed Legacy JavaFX Call

The React handlers `handleInsertStepBefore` and `handleInsertStepAfter` still send `INSERT_BEFORE` / `INSERT_AFTER`.

`SimpleWebSocketServer.injectStepAfterOrBefore` forwards those messages to session `bot-job-scene`. `ARNewCommandScene` receives them and calls `showModal()` on the JavaFX Add/Update Operations page.

Therefore the migration is incomplete. Direct `commandEditor.apply` avoids JavaFX, but the panel's **Insert empty step before/after** actions still take the legacy route.

## Instruction Families

### Native Web Elements

Actions represented by scanned page elements include:

- Input: base action `I` and flags such as `I:S`, `I:E`, hidden variants
- Output: base action `O`
- Click: base action `C`
- Link: tag `a` or action `A`

These rows own element locator/reference data and may expose device flags, test click, display-name edit, and compatible element commands.

Current tag matching restrictions:

- Input accepts `input`, `select`, `textarea`.
- Output accepts `label`, `a`, `span`, `p`, `div`.
- Click accepts `button`, `a`.
- Unknown actions currently default to unrestricted matching. This is unsafe as a command-compatibility policy.

### Element-Dependent Commands

The following require a web-element parent or selected web field:

- `SET`
- `GET`
- `CK`
- `PDF CHECK`
- `CSV CHECK`
- `E` / Extract Field
- `LOOP`
- `REFRESH_LOOP`

Persistence accepts `parentId` for these actions. The JavaFX pane also verifies that the selected web field belongs to the selected block for most element commands.

`SET`, `GET`, `CK`, PDF/CSV checks, and Extract Field also depend on a variable. Their operation string formats differ and must be encoded by Java, not by a generic text input.

### Block-Dependent Commands

- `GOTO`
- `EXCEL GOTO`

These require `parentBlockId`, require a destination block, and cannot be useful with fewer than two blocks. The destination cannot be an invalid placeholder.

`EXCEL GOTO` has a uniqueness rule: only one is allowed per bot job. Existing JavaFX behavior changes a second create into an edit of the existing command.

### Structural Commands

- `IF`
- `ELSEIF`
- `ELSE`
- `ENDIF`

Creating `IF` is an atomic three-row operation: IF, ELSE, ENDIF. All three receive the IF id as `parentId`.

`ELSEIF` is inserted separately and receives the root IF parent id. ELSE and ENDIF are generated boundaries, not independent generic commands.

The confirmed grammar is IF, zero or more ELSEIF branches, optional single ELSE, then ENDIF. ELSEIF may be inserted before ELSE/ENDIF, but never after ELSE or after ENDIF in the same family. IF, ELSEIF, ELSE, and ENDIF must never expose the memory-list `+` action.

Editing rules:

- `IF`, `ELSEIF`, `ELSE`, and `ENDIF` are included in `allSpecialOperations`.
- They are excluded from `editableSpecialOperations`.
- They do not show ordinary move buttons.
- A generic command selector must not allow converting one boundary independently into another action.

Deletion rules:

- Deleting IF, ELSE, or ENDIF deletes the root IF family and children.
- Deleting ELSEIF deletes only that ELSEIF.
- Deleting a normal instruction with attached parent operations asks whether dependent rows should also be deleted.
- The current confirmation is JavaFX and must become a structured React confirmation response.

### Loop Commands

- `LOOP`
- `REFRESH_LOOP`

A loop command references an element row through `parentId`. The parent row carries `loopOnly` or `refreshLoop` flags. The loop boundary must remain after its parent.

Current persistence forces LOOP and REFRESH_LOOP to the end of a block, alongside GOTO and EXCEL GOTO. This conflicts with the React UI's apparent arbitrary before/after placement and must be resolved explicitly.

Moving a loop boundary before its parent is forbidden. Moving a loop parent after its boundary is forbidden. Neither side may move to another block independently.

### Independent Commands

These do not require an element or variable:

- Refresh
- NEXT/ENTER
- Swipe Up / Swipe Down (repeat count)
- Wait/Hold (seconds)
- Pause
- Close Browser
- Screenshot

The codebase uses inconsistent aliases:

- Wait: `H` and `HOLD`
- Screenshot: `P` and `SCREEN`
- Close: `Q` and `QUIT`

One canonical action-code registry is required before migration completion.

## Command Compatibility Findings

The current React `InstructionCommandPanel` incorrectly offers every command for every selected instruction.

Required rule dimensions:

| Dimension | Examples |
|---|---|
| Row kind | native element, command, IF boundary, loop boundary |
| Element kind | input, output/text, click/button/link, unknown |
| Command target | element, variable, block, none |
| Placement | before, after, edit, forced end |
| Session | bot job or component |
| Graph membership | IF family, loop pair, has dependents |

Minimum compatibility policy to validate with regression tests:

- IF/ELSEIF/ELSE/ENDIF cannot receive element commands as properties.
- LOOP/REFRESH_LOOP cannot receive additional child commands and cannot be independently converted.
- SET should target writable/input-like elements.
- GET and Extract Field should target readable elements; confirm whether inputs are also readable.
- Check commands require a variable and a compatible readable source.
- Device flags and test-click controls remain native-element features, not special-command features.
- Block and independent commands are added relative to a row but do not become properties of that row.
- Editing changes a command's parameters only within a compatible command family unless Java validates a safe conversion.

## Drag-And-Drop Findings

`GridItem.tsx` contains approximately 350 lines of inline drag validation.

Current same-block restrictions:

- IF cannot move after ELSEIF, ELSE, or ENDIF in its family.
- ELSEIF cannot move before IF or after ELSE/ENDIF.
- ELSE cannot move before IF/ELSEIF or after ENDIF.
- ENDIF cannot move before IF/ELSE.
- A loop parent cannot move after LOOP/REFRESH_LOOP.
- LOOP/REFRESH_LOOP cannot move before its parent.

Current cross-block restrictions:

- IF-family rows cannot move across blocks.
- LOOP/REFRESH_LOOP cannot move across blocks.
- Rows marked `refreshLoop` or `loopOnly` cannot move across blocks.

Problems:

- Rules exist in React but are not represented as one backend validator.
- IF detection uses booleans and is not safe for nested IF groups.
- Several checks use order indexes and `parentId` inconsistently.
- Duplicate branches and repeated alert code make behavior hard to prove.
- The UI mutates local state before the backend is the authoritative validator.
- Cross-block moves may invalidate variable/element/block references not covered by current checks.
- Moving one member of a graph should be replaced by moving a validated group or rejecting the move.

## Split Component Findings

Split is forbidden when:

- the block has only one instruction;
- the selected row is an IF-family boundary;
- the selected row is inside an IF range;
- the split crosses a loop parent/boundary connection;
- no subsequent instructions remain.

When the last row is selected, current React code silently changes the split point to the previous row. This is surprising and should be replaced by an explicit disabled state or preview.

Split legality is currently computed in React and should be validated again in Java using stable ids and graph edges.

## Variable Findings

Variables have type (`$String` or `#Numeric`), name, value, optional numeric locale, CSV delimiter, parent instruction, and usage count.

- Names must be non-empty and unique in context.
- Type is required.
- Empty values become `$EMPTY`.
- Variables with usages cannot be deleted.
- Renaming/retyping a variable rewrites parent operation strings for GET, CK, and Extract Field.
- Updating a variable must refresh the instruction grid because operation rendering can change.

The first React variable service does not yet perform all parent-operation rewrites from `ARElementValuePane`. It must not replace JavaFX as the default until parity is implemented and tested.

## Current Implementation Gaps

1. Insert-empty actions still open JavaFX.
2. The command panel uses one free-form operation field instead of action-specific schemas.
3. The command catalogue is static in React instead of capability-filtered by Java.
4. Command application does not yet enforce all element, variable, block, IF, loop, and uniqueness rules.
5. Variable update parity is incomplete.
6. No backend graph validator protects drag, split, insert, edit, and delete uniformly.
7. No idempotency store currently prevents duplicate command Apply requests.
8. `GridItemComp` does not expose Split Component through the new panel.

## Source-Of-Truth Decision

Java must own one immutable instruction graph and return capabilities for a selected row. React may use those capabilities for immediate UX, but Java must validate every mutation again.

No rule should remain only in `GridItem.tsx`, `GridItemComp.tsx`, or JavaFX control visibility.
